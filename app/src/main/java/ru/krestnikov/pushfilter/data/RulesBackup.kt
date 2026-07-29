package ru.krestnikov.pushfilter.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val rules: List<FilterRule>,
    val ignoredApps: Set<String>,
)

/** Human-readable JSON so rules can be edited or shared outside the app. */
object RulesBackup {

    private const val FORMAT_VERSION = 1

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
        }.toString(2)
    }

    /** Throws [org.json.JSONException] when [json] is not a rules backup. */
    fun decode(json: String): BackupData {
        val root = JSONObject(json)
        return BackupData(
            rules = decodeRules(root.getJSONArray("rules")),
            ignoredApps = decodeIgnored(root.optJSONArray("ignoredApps")),
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
