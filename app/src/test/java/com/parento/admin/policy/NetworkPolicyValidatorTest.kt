package com.parento.admin.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyValidatorTest {
    @Test
    fun rejectsMoreThanBackendMaximumRules() {
        val rules = (1..(NetworkPolicyValidator.MAX_RULES + 1)).map { index ->
            NetworkPolicyRule(
                id = null,
                domain = "$index.example.com",
                action = NetworkRuleAction.BLOCK,
            )
        }

        assertNull(NetworkPolicyValidator.normalizeRules(rules))
    }

    @Test fun normalizesExactAndWildcardDomains() {
        assertEquals("example.com", NetworkPolicyValidator.normalizeDomain(" Example.COM "))
        assertEquals("*.example.com", NetworkPolicyValidator.normalizeDomain("*.Example.COM"))
        assertNull(NetworkPolicyValidator.normalizeDomain("https://example.com"))
        assertNull(NetworkPolicyValidator.normalizeDomain("foo.*.example.com"))
    }

    @Test fun wildcardDoesNotMatchApex() {
        val wildcard = NetworkPolicyValidator.normalizeDomain("*.example.com")
        assertEquals("*.example.com", wildcard)
        assertFalse(NetworkPolicyValidator.validateRule("example.com") == null && NetworkPolicyValidator.normalizeDomain("*.example.com") == "example.com")
    }

    @Test fun rejectsDuplicateNormalizedRules() {
        val rules = listOf(
            NetworkPolicyRule("1", "example.com", NetworkRuleAction.BLOCK),
            NetworkPolicyRule("2", "EXAMPLE.COM", NetworkRuleAction.ALLOW),
        )
        assertFalse(NetworkPolicyValidator.normalizeRules(rules) != null)
    }

    @Test fun allowsBothActions() {
        assertTrue(NetworkPolicyValidator.validateRule("example.com") == null)
        assertTrue(NetworkPolicyValidator.validateRule("*.example.com") == null)
        assertEquals(NetworkRuleAction.ALLOW, NetworkRuleAction.valueOf("ALLOW"))
        assertEquals(NetworkRuleAction.BLOCK, NetworkRuleAction.valueOf("BLOCK"))
    }
}
