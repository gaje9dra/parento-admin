package com.parento.admin.ui

import android.graphics.Typeface
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.parento.admin.policy.*

class NetworkPolicyScreen(private val root: ViewGroup, private val viewModel: NetworkPolicyViewModel) {
    fun renderList(state: NetworkPolicyListUiState, onOpen: (String) -> Unit) {
        root.removeAllViews()
        val c = column()
        c.addView(title("Network & Website Policies"))
        c.addView(text("Manage domain rules and assignments. Saving a policy does not mean it is enforced."))
        c.addView(button("Create policy") { onOpen("new") })
        when (state) {
            NetworkPolicyListUiState.Loading -> c.addView(text("Loading policies…"))
            NetworkPolicyListUiState.Empty -> c.addView(text("No network policies exist yet."))
            is NetworkPolicyListUiState.Content -> {
                state.policies.forEach { p ->
                    c.addView(button(p.name) { onOpen(p.id) })
                    c.addView(text("Version ${p.version} • ${p.status.name} • ${p.rules.size} rules • Updated ${p.updatedAt}"))
                }
                c.addView(button("Refresh") { viewModel.loadPolicies(true) })
            }
            is NetworkPolicyListUiState.Error -> {
                c.addView(text(state.message))
                if (state.canRetry) c.addView(button("Retry") { viewModel.loadPolicies(true) })
            }
        }
        root.addView(ScrollView(root.context).apply { addView(c) })
    }

    fun renderDetail(state: NetworkPolicyDetailUiState, onBack: () -> Unit) {
        root.removeAllViews()
        val c = column()
        c.addView(button("Back to policies", onBack))
        when (state) {
            NetworkPolicyDetailUiState.Idle -> c.addView(text("Select a policy."))
            NetworkPolicyDetailUiState.Loading -> c.addView(text("Loading policy…"))
            NetworkPolicyDetailUiState.Saving -> c.addView(text("Saving policy…"))
            is NetworkPolicyDetailUiState.Error -> { c.addView(text(state.message)); c.addView(button("Back", onBack)) }
            is NetworkPolicyDetailUiState.Content -> renderContent(c, state)
        }
        root.addView(ScrollView(root.context).apply { addView(c) })
    }

    private fun renderContent(c: LinearLayout, state: NetworkPolicyDetailUiState.Content) {
        val p = state.policy
        c.addView(title(if (p.id.isBlank()) "Create policy" else "Policy details"))
        val name = EditText(root.context).apply { hint = "Policy name"; setText(p.name) }
        val description = EditText(root.context).apply { hint = "Description (optional)"; setText(p.description.orEmpty()); minLines = 2 }
        c.addView(name); c.addView(description)
        c.addView(text("Version: ${p.version} • Status: ${p.status.name}"))
        c.addView(text("Configuration, command delivery, reported state, and enforcement state are separate."))
        val rules = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL }
        c.addView(section("Rules"))
        p.rules.forEach { addRule(rules, it) }
        c.addView(rules)
        c.addView(button("Add rule") { addRule(rules, NetworkPolicyRule(null, "", NetworkRuleAction.BLOCK, true)) })
        c.addView(button("Save policy") {
            val edited = mutableListOf<NetworkPolicyRule>()
            for (i in 0 until rules.childCount) {
                val row = rules.getChildAt(i) as LinearLayout
                val domain = (row.getChildAt(0) as EditText).text.toString()
                val action = if ((row.getChildAt(1) as Button).text.toString() == "ALLOW") NetworkRuleAction.ALLOW else NetworkRuleAction.BLOCK
                edited += NetworkPolicyRule(p.rules.getOrNull(i)?.id, domain, action, true)
            }
            val normalized = NetworkPolicyValidator.normalizeRules(edited)
            if (normalized == null) {
                c.addView(text("Invalid or duplicate domain rule."))
            } else if (p.id.isBlank()) {
                viewModel.create(name.text.toString(), description.text.toString().ifBlank { null }, normalized)
            } else {
                viewModel.update(p.copy(name = name.text.toString(), description = description.text.toString().ifBlank { null }, rules = normalized))
            }
        })
        if (p.id.isNotBlank()) {
            c.addView(button(if (p.status == NetworkPolicyStatus.ACTIVE) "Disable policy" else "Enable policy") {
                viewModel.update(p.copy(status = if (p.status == NetworkPolicyStatus.ACTIVE) NetworkPolicyStatus.DISABLED else NetworkPolicyStatus.ACTIVE))
            })
            renderDeviceState(c, state)
        }
        state.message?.let { c.addView(text(it)) }
    }

    private fun addRule(container: LinearLayout, rule: NetworkPolicyRule) {
        val row = LinearLayout(root.context).apply { orientation = LinearLayout.HORIZONTAL }
        val domain = EditText(root.context).apply {
            hint = "example.com or *.example.com"; setText(rule.domain)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            contentDescription = "Domain"
        }
        val action = Button(root.context).apply {
            text = rule.action.name
            setOnClickListener { text = if (text == "ALLOW") "BLOCK" else "ALLOW" }
            contentDescription = "Rule action"
        }
        row.addView(domain); row.addView(action); container.addView(row)
    }

    private fun renderDeviceState(c: LinearLayout, state: NetworkPolicyDetailUiState.Content) {
        c.addView(section("Device assignment & enforcement"))
        state.devices.forEach { d ->
            c.addView(text("${d.displayName} • ${d.connectionState.name} • ${d.enrollmentState.name}"))
            c.addView(button("Load status") { viewModel.loadDeviceState(d.deviceId) })
        }
        state.deviceState?.let { ds ->
            ds.synchronization?.let { s ->
                c.addView(text("Desired: ${s.desiredPolicyVersion ?: "none"} • Reported: ${s.reportedPolicyVersion ?: "none"}"))
                c.addView(text("Enforcement: ${s.status.name} • Freshness: ${s.freshness()}"))
                c.addView(text("Last reported: ${s.lastReportedAt ?: "never"}"))
                s.lastErrorCode?.let { c.addView(text("Error: $it")) }
            }
            ds.capability?.let { cap ->
                c.addView(text("Capability: ${cap.mode.name} • supported=${cap.supported} • version=${cap.capabilityVersion ?: "unknown"}"))
                c.addView(text("Reported: ${cap.reportedAt}"))
            }
            ds.command?.let { c.addView(text("Command: ${it.status.name} • ${it.type.wireValue}")) }
            state.selectedDeviceId?.let { id ->
                c.addView(button("Request policy sync") { viewModel.sync(id) })
                c.addView(button("Request status refresh") { viewModel.requestStatus(id) })
                c.addView(button("Assign this policy") { viewModel.assign(id) })
                c.addView(button("Remove this assignment") { viewModel.remove(id) })
            }
        }
    }

    private fun title(v: String) = TextView(root.context).apply { text = v; textSize = 24f; setTypeface(typeface, Typeface.BOLD) }
    private fun section(v: String) = TextView(root.context).apply { text = v; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setPadding(0,18,0,8) }
    private fun text(v: String) = TextView(root.context).apply { text = v; textSize = 15f; setPadding(0,5,0,5) }
    private fun button(v: String, onClick: () -> Unit) = Button(root.context).apply { text = v; minHeight = 52; contentDescription = v; setOnClickListener { onClick() } }
    private fun column() = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
}
