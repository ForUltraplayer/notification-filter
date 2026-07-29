package ru.krestnikov.pushfilter.data

/** Pseudo package name for a rule that applies to every application. */
const val ALL_APPS = "*"

enum class RuleMode {
    /** The notification is dropped when the pattern matches. */
    BLOCK,

    /** Only notifications matching the pattern get through. */
    ALLOW,
}

enum class MatchTarget { TITLE, TEXT, BOTH }

data class FilterRule(
    val id: Long = 0L,
    val packageName: String = ALL_APPS,
    val name: String = "",
    val mode: RuleMode = RuleMode.BLOCK,
    val pattern: String = "",
    val target: MatchTarget = MatchTarget.BOTH,
    val caseSensitive: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
)

enum class VerdictReason {
    /** No enabled rule covers this application. */
    NO_RULES,

    /** Rules exist, none of the blocking ones matched. */
    NO_BLOCK_RULE_MATCHED,

    BLOCKED_BY_RULE,
    ALLOWED_BY_RULE,

    /** The app has allow-rules, so anything that does not match them is dropped. */
    NO_ALLOW_RULE_MATCHED,
}

data class LogEntry(
    val id: Long = 0L,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val blocked: Boolean,
    val ruleId: Long? = null,
    val ruleName: String? = null,
    val rulePattern: String? = null,
    val reason: VerdictReason,
)

data class LogStats(val blocked: Int, val allowed: Int) {
    val total: Int get() = blocked + allowed
}

enum class LogFilter { ALL, BLOCKED, ALLOWED }

data class LogQuery(
    val filter: LogFilter = LogFilter.ALL,
    val packageName: String? = null,
    val search: String = "",
)

/**
 * Escapes every regex metacharacter so the result matches [literal] verbatim.
 * Produces a readable pattern, unlike [Regex.escape] which wraps text in \Q..\E.
 */
fun escapeRegexLiteral(literal: String): String = buildString(literal.length + 8) {
    for (ch in literal) {
        if (ch in "\\.[]{}()*+-?^\$|") append('\\')
        append(ch)
    }
}
