package ru.krestnikov.pushfilter.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krestnikov.pushfilter.data.ALL_APPS
import ru.krestnikov.pushfilter.data.FilterRule
import ru.krestnikov.pushfilter.data.MatchTarget
import ru.krestnikov.pushfilter.data.RuleMode
import ru.krestnikov.pushfilter.data.VerdictReason
import ru.krestnikov.pushfilter.data.escapeRegexLiteral

private const val SHOP = "com.example.shop"
private const val BANK = "com.example.bank"

private fun rule(
    mode: RuleMode,
    pattern: String,
    packageName: String = SHOP,
    target: MatchTarget = MatchTarget.BOTH,
    caseSensitive: Boolean = false,
    enabled: Boolean = true,
) = FilterRule(
    id = 1,
    packageName = packageName,
    mode = mode,
    pattern = pattern,
    target = target,
    caseSensitive = caseSensitive,
    enabled = enabled,
)

private fun evaluate(
    rules: List<FilterRule>,
    title: String = "",
    text: String = "",
    packageName: String = SHOP,
) = FilterEngine.evaluate(packageName, NotificationContent(title, text), rules)

class FilterEngineTest {

    @Test
    fun `app without rules is left alone`() {
        val verdict = evaluate(emptyList(), title = "Order shipped")
        assertFalse(verdict.blocked)
        assertEquals(VerdictReason.NO_RULES, verdict.reason)
    }

    @Test
    fun `blocking rule drops a matching notification`() {
        val rules = listOf(rule(RuleMode.BLOCK, "sale|discount"))
        val verdict = evaluate(rules, title = "Huge SALE today")
        assertTrue(verdict.blocked)
        assertEquals(VerdictReason.BLOCKED_BY_RULE, verdict.reason)
        assertEquals(rules.single(), verdict.rule)
    }

    @Test
    fun `blocking rule lets everything else through`() {
        val verdict = evaluate(listOf(rule(RuleMode.BLOCK, "sale")), title = "Order shipped")
        assertFalse(verdict.blocked)
        assertEquals(VerdictReason.NO_BLOCK_RULE_MATCHED, verdict.reason)
    }

    @Test
    fun `allowing rule turns the app into a whitelist`() {
        val rules = listOf(rule(RuleMode.ALLOW, "payment"))

        val kept = evaluate(rules, title = "Payment received")
        assertFalse(kept.blocked)
        assertEquals(VerdictReason.ALLOWED_BY_RULE, kept.reason)

        val dropped = evaluate(rules, title = "Weekend deals just for you")
        assertTrue(dropped.blocked)
        assertEquals(VerdictReason.NO_ALLOW_RULE_MATCHED, dropped.reason)
    }

    @Test
    fun `blocking rule wins over a matching allowing rule`() {
        val rules = listOf(
            rule(RuleMode.ALLOW, "payment"),
            rule(RuleMode.BLOCK, "advert"),
        )
        val verdict = evaluate(rules, title = "payment advert")
        assertTrue(verdict.blocked)
        assertEquals(VerdictReason.BLOCKED_BY_RULE, verdict.reason)
    }

    @Test
    fun `rules of other applications are ignored`() {
        val rules = listOf(rule(RuleMode.BLOCK, "sale", packageName = BANK))
        val verdict = evaluate(rules, title = "sale", packageName = SHOP)
        assertFalse(verdict.blocked)
        assertEquals(VerdictReason.NO_RULES, verdict.reason)
    }

    @Test
    fun `wildcard rule applies to every application`() {
        val rules = listOf(rule(RuleMode.BLOCK, "spam", packageName = ALL_APPS))
        assertTrue(evaluate(rules, title = "spam", packageName = SHOP).blocked)
        assertTrue(evaluate(rules, title = "spam", packageName = BANK).blocked)
    }

    @Test
    fun `disabled rule has no effect`() {
        val rules = listOf(rule(RuleMode.BLOCK, "sale", enabled = false))
        val verdict = evaluate(rules, title = "sale")
        assertFalse(verdict.blocked)
        assertEquals(VerdictReason.NO_RULES, verdict.reason)
    }

    @Test
    fun `matching ignores case unless the rule asks otherwise`() {
        assertTrue(evaluate(listOf(rule(RuleMode.BLOCK, "sale")), title = "SALE").blocked)
        assertFalse(
            evaluate(
                listOf(rule(RuleMode.BLOCK, "sale", caseSensitive = true)),
                title = "SALE",
            ).blocked,
        )
    }

    @Test
    fun `match target narrows the searched text`() {
        val titleOnly = listOf(rule(RuleMode.BLOCK, "promo", target = MatchTarget.TITLE))
        assertTrue(evaluate(titleOnly, title = "promo", text = "").blocked)
        assertFalse(evaluate(titleOnly, title = "", text = "promo").blocked)

        val textOnly = listOf(rule(RuleMode.BLOCK, "promo", target = MatchTarget.TEXT))
        assertFalse(evaluate(textOnly, title = "promo", text = "").blocked)
        assertTrue(evaluate(textOnly, title = "", text = "promo").blocked)

        val both = listOf(rule(RuleMode.BLOCK, "promo", target = MatchTarget.BOTH))
        assertTrue(evaluate(both, title = "promo", text = "").blocked)
        assertTrue(evaluate(both, title = "", text = "promo").blocked)
    }

    @Test
    fun `anchors apply per line so a rule can target one field`() {
        val rules = listOf(rule(RuleMode.BLOCK, "^Ad: "))
        assertTrue(evaluate(rules, title = "News", text = "Ad: buy now").blocked)
        assertFalse(evaluate(rules, title = "News", text = "This is not an Ad: really").blocked)
    }

    @Test
    fun `a broken pattern never matches and never throws`() {
        val verdict = evaluate(listOf(rule(RuleMode.BLOCK, "unclosed (group")), title = "anything")
        assertFalse(verdict.blocked)
    }

    @Test
    fun `an empty notification is not caught by a catch-all pattern`() {
        assertFalse(evaluate(listOf(rule(RuleMode.BLOCK, ".*"))).blocked)
    }

    @Test
    fun `escaped literals match themselves and nothing else`() {
        val seed = "Save 50% (limited) [today] +1"
        val rules = listOf(rule(RuleMode.BLOCK, escapeRegexLiteral(seed)))
        assertTrue(evaluate(rules, title = seed).blocked)
        assertFalse(evaluate(rules, title = "Save 50 limited today 1").blocked)
    }
}
