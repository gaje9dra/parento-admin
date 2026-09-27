package com.parento.admin.policy

object NetworkPolicyValidator {
    fun normalizeDomain(value: String): String? {
        val trimmed = value.trim().lowercase()
        val wildcard = trimmed.startsWith("*.")
        val candidate = if (wildcard) trimmed.removePrefix("*.") else trimmed
        if (candidate.isEmpty() || candidate.contains('*') || candidate.length > 253) return null
        if (candidate.contains('/') || candidate.contains(':') || candidate.any { it.code in 0..31 || it.code == 127 }) return null
        val labels = candidate.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() || it.length > 63 }) return null
        if (labels.any { !it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) }) return null
        return if (wildcard) "*.$candidate" else candidate
    }

    fun validateRule(domain: String, existing: List<NetworkPolicyRule> = emptyList()): String? {
        val normalized = normalizeDomain(domain) ?: return "Enter a valid domain or supported wildcard."
        if (existing.any { normalizeDomain(it.domain) == normalized }) return "This domain already exists in the policy."
        return null
    }

    fun normalizeRules(rules: List<NetworkPolicyRule>): List<NetworkPolicyRule>? {
        val normalized = rules.mapNotNull { rule ->
            normalizeDomain(rule.domain)?.let { rule.copy(domain = it, validationError = null) }
        }
        return if (normalized.size == rules.size && normalized.map { it.domain }.distinct().size == normalized.size) normalized else null
    }
}
