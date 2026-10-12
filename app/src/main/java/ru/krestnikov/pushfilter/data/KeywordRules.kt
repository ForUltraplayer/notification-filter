// @file KeywordRules.kt
// @author: ForUltraplayer
// @brief Convert simple user keywords to literal global notification rules.
package ru.krestnikov.pushfilter.data

enum class KeywordError { EMPTY, TOO_LONG, DUPLICATE }

object KeywordRules {
    const val MAX_LENGTH = 120

    fun validate(input: String, rules: List<FilterRule>, editingId: Long = 0L): KeywordError? {
        val keyword = input.trim()
        return when {
            keyword.isBlank() -> KeywordError.EMPTY
            keyword.length > MAX_LENGTH -> KeywordError.TOO_LONG
            rules.any { (editingId == 0L || it.id != editingId) && it.name.equals(keyword, ignoreCase = true) } -> KeywordError.DUPLICATE
            else -> null
        }
    }

    fun create(input: String, original: FilterRule? = null): FilterRule {
        val keyword = input.trim()
        require(keyword.isNotBlank() && keyword.length <= MAX_LENGTH)
        return FilterRule(
            id = original?.id ?: 0L,
            name = keyword,
            packageName = ALL_APPS,
            mode = RuleMode.BLOCK,
            pattern = escapeRegexLiteral(keyword),
            target = MatchTarget.BOTH,
            caseSensitive = false,
            enabled = true,
            createdAt = original?.createdAt ?: 0L,
        )
    }
}
