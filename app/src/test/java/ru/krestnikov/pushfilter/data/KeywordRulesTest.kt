// @file KeywordRulesTest.kt
// @author: ForUltraplayer
// @brief Verify literal matching and input rules for the simplified keyword UI.
package ru.krestnikov.pushfilter.data

import org.junit.Assert.*
import org.junit.Test
import ru.krestnikov.pushfilter.filter.FilterEngine
import ru.krestnikov.pushfilter.filter.NotificationContent

class KeywordRulesTest {
    private fun blocked(keyword: String, title: String = "", body: String = "") =
        FilterEngine.evaluate("example.shop", NotificationContent(title, body), listOf(KeywordRules.create(keyword))).blocked

    @Test fun koreanKeywordMatchesTitleAndBody() {
        assertTrue(blocked("(광고)", title = "(광고) 오늘의 혜택"))
        assertTrue(blocked("(광고)", body = "오늘만 (광고) 할인"))
        assertFalse(blocked("(광고)", title = "광고 표시 설정"))
        assertFalse(blocked("(광고)", title = "주문 배송 완료"))
    }
    @Test fun regexCharactersAreLiteral() {
        listOf("[ad]", "a.b", "a+b", "a*b", "a?b", "a|b", "^ad$", "{ad}", "a\\b").forEach {
            assertTrue(blocked(it, title = "prefix $it suffix"))
        }
        assertFalse(blocked("a.b", title = "axb"))
        assertFalse(blocked("a|b", title = "a"))
        assertFalse(blocked("[ad]", title = "a"))
    }
    @Test fun trimsEdgesAndIgnoresEnglishCase() {
        assertEquals("SALE", KeywordRules.create("  SALE  ").name)
        assertTrue(blocked("  SALE  ", body = "flash Sale today"))
    }
    @Test fun rejectsEmptyAndLongKeywords() {
        assertEquals(KeywordError.EMPTY, KeywordRules.validate("  ", emptyList()))
        assertEquals(KeywordError.TOO_LONG, KeywordRules.validate("a".repeat(121), emptyList()))
        assertNull(KeywordRules.validate("a".repeat(120), emptyList()))
    }
    @Test fun duplicateIsCaseInsensitiveEvenBeforePersistence() {
        assertEquals(KeywordError.DUPLICATE, KeywordRules.validate(" sale ", listOf(KeywordRules.create("SALE"))))
    }
    @Test fun editingSelfIsAllowedButOtherDuplicateIsNot() {
        val first = KeywordRules.create("SALE").copy(id = 1)
        val second = KeywordRules.create("광고").copy(id = 2)
        assertNull(KeywordRules.validate("sale", listOf(first, second), 1))
        assertEquals(KeywordError.DUPLICATE, KeywordRules.validate("광고", listOf(first, second), 1))
    }
    @Test fun editKeepsIdentityAndTimestamp() {
        val old = KeywordRules.create("old").copy(id = 8, createdAt = 1234)
        val updated = KeywordRules.create("new", old)
        assertEquals(8L, updated.id)
        assertEquals(1234L, updated.createdAt)
        assertEquals("new", updated.name)
        assertEquals(ALL_APPS, updated.packageName)
        assertEquals(MatchTarget.BOTH, updated.target)
        assertEquals(RuleMode.BLOCK, updated.mode)
    }
    @Test fun anyKeywordCanBlockButNormalMessageSurvives() {
        val rules = listOf("(광고)", "무료체험").map { KeywordRules.create(it) }
        assertTrue(FilterEngine.evaluate("example.bank", NotificationContent("", "무료체험 안내"), rules).blocked)
        assertFalse(FilterEngine.evaluate("example.bank", NotificationContent("입금", "송금이 완료되었습니다"), rules).blocked)
    }
}
