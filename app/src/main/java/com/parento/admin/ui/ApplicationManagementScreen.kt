package com.parento.admin.ui

import android.app.AlertDialog
import android.graphics.Typeface
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import com.parento.admin.application.*

class ApplicationManagementScreen(
    private val root: ViewGroup,
    private val viewModel: ApplicationManagementViewModel,
) {
    fun render(state: ApplicationManagementUiState, onBack: () -> Unit) {
        root.removeAllViews()
        val column = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            val p = resources.getDimensionPixelSize(com.parento.admin.R.dimen.screen_padding)
            setPadding(p, p, p, p)
        }
        column.addView(button("Back to device", onBack))
        when (state) {
            ApplicationManagementUiState.Loading -> column.addView(text("Loading application management…"))
            is ApplicationManagementUiState.Error -> {
                column.addView(text(state.message))
                if (state.retryable) column.addView(button("Retry") { viewModel.refresh() })
            }
            is ApplicationManagementUiState.Content -> renderContent(column, state)
        }
        root.addView(ScrollView(root.context).apply { addView(column) })
    }

    private fun renderContent(column: LinearLayout, state: ApplicationManagementUiState.Content) {
        column.addView(title("Application management"))
        column.addView(text("Device: " + state.deviceName))
        column.addView(text("Managed device ID: " + state.deviceId))
        state.message?.let { column.addView(text(it)) }

        val inventory = state.inventory
        column.addView(section("Application inventory"))
        column.addView(text("Freshness: " + (inventory?.freshness?.name ?: "UNAVAILABLE")))
        column.addView(text("Observed: " + (inventory?.observedAt ?: "Unavailable")))
        column.addView(text("Received: " + (inventory?.receivedAt ?: "Unavailable")))

        val search = EditText(root.context).apply {
            hint = "Search app name or package"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(state.query)
            setSingleLine(true)
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { viewModel.setQuery(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        column.addView(search)

        val filtered = inventory?.applications.orEmpty().filter {
            val q = state.query.trim().lowercase()
            q.isBlank() || it.packageName.lowercase().contains(q) || it.displayName.orEmpty().lowercase().contains(q)
        }
        if (filtered.isEmpty()) {
            column.addView(text(if (inventory == null) "Inventory unavailable." else "No applications match the current filter."))
        } else filtered.forEach { column.addView(applicationCard(it)) }

        column.addView(button("Request fresh inventory") { viewModel.requestInventory() }.apply {
            isEnabled = inventory?.freshness != InventoryFreshness.REVOKED && inventory?.freshness != InventoryFreshness.DISCONNECTED
        })
        column.addView(button(if (state.loading) "Refreshing…" else "Refresh") { viewModel.refresh() }.apply { isEnabled = !state.loading })

        column.addView(section("Application policies"))
        column.addView(button("Create policy") { showCreatePolicyDialog() })
        if (state.policies.isEmpty()) column.addView(text("No application policies are available."))
        else state.policies.forEach { column.addView(policyCard(it)) }

        column.addView(section("Device policy state"))
        val ps = state.policyState
        column.addView(text("Assigned policy: " + (ps?.policy?.name ?: "None")))
        column.addView(text("Policy version: " + (ps?.policy?.version?.toString() ?: "—")))
        ps?.synchronization?.let {
            column.addView(text("Enforcement: " + it.status.name))
            column.addView(text("Desired version: " + (it.desiredPolicyVersion?.toString() ?: "—")))
            column.addView(text("Device reports: " + (it.reportedPolicyVersion?.toString() ?: "—")))
            it.errorCode?.let { e -> column.addView(text("Safe error code: " + e)) }
            if (it.status != EnforcementStatus.APPLIED) column.addView(button("Request policy synchronization") { viewModel.syncPolicy() })
        }
    }

    private fun applicationCard(app: ApplicationInventoryItem) = LinearLayout(root.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 10, 0, 10)
        addView(text(app.displayName ?: app.packageName).apply { setTypeface(typeface, Typeface.BOLD) })
        addView(text(app.packageName))
        addView(text("Version: " + (app.versionName ?: "Unavailable") + " (" + (app.versionCode?.toString() ?: "—") + ")"))
        addView(text("Enabled: " + (app.enabled?.toString() ?: "Unavailable") + " • State: " + app.installState))
        addView(text("Inventory freshness: " + app.freshness.name))
    }

    private fun policyCard(policy: ApplicationPolicy) = LinearLayout(root.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 10, 0, 10)
        addView(text(policy.name + " • v" + policy.version).apply { setTypeface(typeface, Typeface.BOLD) })
        addView(text("Status: " + policy.status.name))
        addView(text("Rules: " + policy.rules.size))
        policy.description?.let { addView(text(it)) }
        addView(button("Assign to this device") {
            confirm("Assign " + policy.name + " v" + policy.version + "?", "This changes desired policy state for this device.") { viewModel.assignPolicy(policy.id) }
        })
        addView(button("Edit policy") { showEditPolicyDialog(policy) })
    }

    private fun showCreatePolicyDialog() {
        val name = EditText(root.context).apply { hint = "Policy name"; setSingleLine(true) }
        val description = EditText(root.context).apply { hint = "Description (optional)" }
        val rules = EditText(root.context).apply { hint = "Rules: com.example.app=BLOCK;com.example.safe=ALLOW" }
        val box = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 8, 32, 0); addView(name); addView(description); addView(rules) }
        AlertDialog.Builder(root.context).setTitle("Create application policy").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val parsed = parseRules(rules.text.toString())
                if (parsed == null || name.text.toString().trim().isBlank()) Toast.makeText(root.context, "Invalid policy name or rules.", Toast.LENGTH_LONG).show()
                else viewModel.createPolicy(name.text.toString().trim(), description.text.toString().trim().ifBlank { null }, parsed)
            }.show()
    }

    private fun showEditPolicyDialog(policy: ApplicationPolicy) {
        val name = EditText(root.context).apply { setText(policy.name); setSingleLine(true) }
        val description = EditText(root.context).apply { setText(policy.description.orEmpty()) }
        val rules = EditText(root.context).apply { setText(policy.rules.joinToString(";") { it.packageName + "=" + it.action.name }) }
        val status = Spinner(root.context).apply {
            adapter = ArrayAdapter(root.context, android.R.layout.simple_spinner_dropdown_item, PolicyStatus.values().map { it.name })
            setSelection(policy.status.ordinal)
        }
        val box = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 8, 32, 0); addView(name); addView(description); addView(rules); addView(status) }
        AlertDialog.Builder(root.context).setTitle("Edit policy v" + policy.version).setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val parsed = parseRules(rules.text.toString())
                if (parsed == null || name.text.toString().trim().isBlank()) Toast.makeText(root.context, "Invalid policy name or rules.", Toast.LENGTH_LONG).show()
                else viewModel.updatePolicy(policy.copy(name = name.text.toString().trim(), description = description.text.toString().trim().ifBlank { null }, status = PolicyStatus.values()[status.selectedItemPosition], rules = parsed), policy.version)
            }.show()
    }

    private fun parseRules(value: String): List<ApplicationPolicyRule>? {
        if (value.isBlank()) return emptyList()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<ApplicationPolicyRule>()
        value.split(";").forEach { raw ->
            val parts = raw.split("=", limit = 2)
            if (parts.size != 2) return null
            val pkg = parts[0].trim()
            val action = runCatching { PolicyAction.valueOf(parts[1].trim().uppercase()) }.getOrNull() ?: return null
            if (!validAndroidPackageName(pkg) || !seen.add(pkg)) return null
            result += ApplicationPolicyRule(pkg, action)
        }
        return result
    }

    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(root.context).setTitle(title).setMessage(message).setNegativeButton("Cancel", null).setPositiveButton("Confirm") { _, _ -> action() }.show()
    }
    private fun title(value: String) = TextView(root.context).apply { text = value; textSize = 26f; setTypeface(typeface, Typeface.BOLD) }
    private fun section(value: String) = TextView(root.context).apply { text = value; textSize = 19f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 18, 0, 8) }
    private fun text(value: String) = TextView(root.context).apply { text = value; textSize = 15f; setPadding(0, 4, 0, 4) }
    private fun button(label: String, action: () -> Unit) = Button(root.context).apply { text = label; minHeight = root.resources.getDimensionPixelSize(com.parento.admin.R.dimen.minimum_touch_target); contentDescription = label; setOnClickListener { action() } }
}
