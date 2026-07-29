package ru.krestnikov.pushfilter.filter

import ru.krestnikov.pushfilter.data.ALL_APPS
import ru.krestnikov.pushfilter.data.FilterRule
import ru.krestnikov.pushfilter.data.RuleMode
import ru.krestnikov.pushfilter.data.VerdictReason

data class Verdict(
    val blocked: Boolean,
    val rule: FilterRule?,
    val reason: VerdictReason,
)

object FilterEngine {

    /**
     * Blocking rules act as a veto: if any of them matches, the notification is dropped.
     * Allowing rules turn the app into a whitelist — once one exists, anything that does not
     * match it is dropped. An app with no rules at all is left alone.
     */
    fun evaluate(
        packageName: String,
        content: NotificationContent,
        rules: List<FilterRule>,
    ): Verdict {
        var matchedBlock: FilterRule? = null
        var matchedAllow: FilterRule? = null
        var hasAllowRule = false
        var hasAnyRule = false

        for (rule in rules) {
            if (!rule.enabled) continue
            if (rule.packageName != ALL_APPS && rule.packageName != packageName) continue
            hasAnyRule = true
            when (rule.mode) {
                RuleMode.BLOCK ->
                    if (matchedBlock == null && matches(rule, content)) matchedBlock = rule

                RuleMode.ALLOW -> {
                    hasAllowRule = true
                    if (matchedAllow == null && matches(rule, content)) matchedAllow = rule
                }
            }
        }

        return when {
            matchedBlock != null -> Verdict(true, matchedBlock, VerdictReason.BLOCKED_BY_RULE)
            matchedAllow != null -> Verdict(false, matchedAllow, VerdictReason.ALLOWED_BY_RULE)
            hasAllowRule -> Verdict(true, null, VerdictReason.NO_ALLOW_RULE_MATCHED)
            hasAnyRule -> Verdict(false, null, VerdictReason.NO_BLOCK_RULE_MATCHED)
            else -> Verdict(false, null, VerdictReason.NO_RULES)
        }
    }

    fun matches(rule: FilterRule, content: NotificationContent): Boolean {
        if (rule.pattern.isEmpty()) return false
        val regex = RegexCache.compile(rule.pattern, rule.caseSensitive) ?: return false
        val haystack = content.forTarget(rule.target)
        if (haystack.isEmpty()) return false
        return regex.containsMatchIn(haystack)
    }
}
