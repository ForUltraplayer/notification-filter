package ru.krestnikov.pushfilter.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val rules: List<FilterRule>,
    val ignoredApps: Set<String>,
    /** Null when the file predates format 2 and carries no settings at all. */
    val settings: Settings? = null,
)

/** Human-readable JSON so a backup can be edited or shared outside the app. */
object RulesBackup {

    /** 2 added the settings object; a reader of either version ignores what it does not know. */
    private const val FORMAT_VERSION = 2

    fun encode(data: BackupData): String {
        val array = JSONArray()
        for (rule in data.rules) {
            array.put(
                JSONObject().apply {
                    put("package", rule.packageName)
                    put("name", rule.name)
                    put("mode", rule.mode.name)
                    put("pattern", rule.pattern)
                    put("target", rule.target.name)
                    put("caseSensitive", rule.caseSensitive)
                    put("enabled", rule.enabled)
                },
            )
        }
        return JSONObject().apply {
            put("format", FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("rules", array)
            put("ignoredApps", JSONArray(data.ignoredApps.toList()))
            data.settings?.let { put("settings", encodeSettings(it)) }
        }.toString(2)
    }

    private fun encodeSettings(settings: Settings): JSONObject = JSONObject().apply {
        put("filteringEnabled", settings.filteringEnabled)
        put("logAllowed", settings.logAllowed)
        put("ignoreOngoing", settings.ignoreOngoing)
        put("ignoreGroupSummary", settings.ignoreGroupSummary)
        put("retentionDays", settings.retentionDays)
    }

    /** Throws [org.json.JSONException] when [json] is not a rules backup. */
    fun decode(json: String): BackupData {
        val root = JSONObject(json)
        return BackupData(
            rules = decodeRules(root.getJSONArray("rules")),
            ignoredApps = decodeIgnored(root.optJSONArray("ignoredApps")),
            settings = decodeSettings(root.optJSONObject("settings")),
        )
    }

    /** Each field falls back to its default, so a hand-written file may list only a few. */
    private fun decodeSettings(obj: JSONObject?): Settings? {
        if (obj == null) return null
        val defaults = Settings()
        return Settings(
            filteringEnabled = obj.optBoolean("filteringEnabled", defaults.filteringEnabled),
            logAllowed = obj.optBoolean("logAllowed", defaults.logAllowed),
            ignoreOngoing = obj.optBoolean("ignoreOngoing", defaults.ignoreOngoing),
            ignoreGroupSummary = obj.optBoolean(
                "ignoreGroupSummary",
                defaults.ignoreGroupSummary,
            ),
            retentionDays = obj.optInt("retentionDays", defaults.retentionDays),
        )
    }

    private fun decodeIgnored(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }
    }

    private fun decodeRules(array: JSONArray): List<FilterRule> {
        val now = System.currentTimeMillis()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val pattern = item.optString("pattern")
                if (pattern.isEmpty()) continue
                add(
                    FilterRule(
                        packageName = item.optString("package", ALL_APPS).ifEmpty { ALL_APPS },
                        name = item.optString("name"),
                        mode = enumOrDefault(item.optString("mode"), RuleMode.BLOCK),
                        pattern = pattern,
                        target = enumOrDefault(item.optString("target"), MatchTarget.BOTH),
                        caseSensitive = item.optBoolean("caseSensitive", false),
                        enabled = item.optBoolean("enabled", true),
                        createdAt = now + index,
                    ),
                )
            }
        }
    }
}
