package ru.krestnikov.pushfilter.filter

/**
 * Patterns are recompiled for every notification otherwise, and notifications arrive on the
 * main thread. Failed compilations are cached too so a broken rule stays cheap.
 */
object RegexCache {

    private const val MAX_ENTRIES = 128

    private val cache = HashMap<String, Regex?>()

    fun compile(pattern: String, caseSensitive: Boolean): Regex? {
        val key = if (caseSensitive) "1$pattern" else "0$pattern"
        synchronized(cache) {
            if (cache.containsKey(key)) return cache[key]
            if (cache.size >= MAX_ENTRIES) cache.clear()
            val regex = runCatching { Regex(pattern, options(caseSensitive)) }.getOrNull()
            cache[key] = regex
            return regex
        }
    }

    /** Returns the compiler error message, or null when [pattern] is valid. */
    fun errorOf(pattern: String): String? = runCatching { Regex(pattern) }
        .exceptionOrNull()
        ?.let { it.message?.substringBefore('\n') ?: it.toString() }

    private fun options(caseSensitive: Boolean): Set<RegexOption> =
        if (caseSensitive) setOf(RegexOption.MULTILINE)
        else setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
}
