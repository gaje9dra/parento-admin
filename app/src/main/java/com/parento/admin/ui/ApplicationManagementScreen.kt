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
        column.addView(text("Device: ${state.deviceName}"))
        column.addView(text("Managed device ID: ${state.deviceId}"))
        column.addView(statusIndicator(
            "Data source",
            when (state.connection) {
                ApplicationDataConnectionState.LIVE -> "LIVE BACKEND STATE"
                ApplicationDataConnectionState.OFFLINE -> "OFFLINE — LAST IN-MEMORY STATE"
                ApplicationDataConnectionState.UNKNOWN -> "UNKNOWN"
            },
        ))
        state.message?.let { column.addView(text(it)) }

        val inventory = state.inventory
        column.addView(section("Application inventory"))
        column.addView(text("Freshness: ${inventory?.freshness?.name ?: "UNAVAILABLE"}"))
        column.addView(text("Observed: ${inventory?.observedAt ?: "Unavailable"}"))
        column.addView(text("Received: ${inventory?.receivedAt ?: "Unavailable"}"))

        val search = EditText(root.context).apply {
            hint = "Search app name or package"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(state.query)
            setSingleLine(true)
            contentDescription = "Filter applications by name or package"
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        column.addView(search)

        val filtered = inventory?.applications.orEmpty().filter {
            val q = state.query.trim().lowercase()
            q.isBlank() ||
                it.packageName.lowercase().contains(q) ||
                it.displayName.orEmpty().lowercase().contains(q)
        }

        if (filtered.isEmpty()) {
            column.addView(text(if (inventory == null) "Inventory unavailable." else "No applications match the current filter."))
        } else {
            filtered.forEach { column.addView(applicationCard(it)) }
        }

        state.inventoryNextCursor?.let {
            column.addView(
                button(if (state.inventoryLoadingMore) "Loading more applications…" else "Load more applications") {
                    viewModel.loadMoreInventory()
                }.apply {
                    isEnabled = !state.inventoryLoadingMore && !state.actionBusy && state.connection == ApplicationDataConnectionState.LIVE
                },
            )
        }

        column.addView(
            button("Request fresh inventory") { viewModel.requestInventory() }.apply {
                isEnabled = !state.actionBusy &&
                    state.connection == ApplicationDataConnectionState.LIVE &&
                    inventory?.freshness !in setOf(InventoryFreshness.REVOKED, InventoryFreshness.DISCONNECTED)
            },
        )
        column.addView(
            button(if (state.loading) "Refreshing…" else "Refresh") { viewModel.refresh() }.apply {
                isEnabled = !state.loading
            },
        )

        state.command?.let { renderCommand(column, it) }

        column.addView(section("Application policies"))
        column.addView(button("Create policy") { showCreatePolicyDialog() }.apply { isEnabled = !state.actionBusy && state.connection == ApplicationDataConnectionState.LIVE })
        if (state.policies.isEmpty()) {
            column.addView(text("No application policies are available."))
        } else {
            state.policies.forEach { column.addView(policyCard(it, state)) }
        }
        state.policyNextCursor?.let {
            column.addView(
                button(if (state.policyLoadingMore) "Loading more policies…" else "Load more policies") {
                    viewModel.loadMorePolicies()
                }.apply {
                    isEnabled = !state.policyLoadingMore && !state.actionBusy && state.connection == ApplicationDataConnectionState.LIVE
                },
            )
        }

        renderPolicyState(column, state)
    }

    private fun renderCommand(column: LinearLayout, command: ApplicationManagementCommand) {
        column.addView(section("Latest application-management command"))
        column.addView(text("Command ID: ${command.id}"))
        column.addView(text("Type: ${command.type}"))
        column.addView(statusIndicator("Command status", command.status.name))
        command.createdAt?.let { column.addView(text("Created: $it")) }
        command.deliveryAt?.let { column.addView(text("Delivered: $it")) }
        command.acknowledgedAt?.let { column.addView(text("Acknowledged: $it")) }
        command.completedAt?.let { column.addView(text("Completed: $it")) }
        command.failureCode?.let { column.addView(text("Failure code: $it")) }
        command.errorCategory?.let { column.addView(text("Error category: $it")) }
        column.addView(text("Command delivery is not enforcement success."))
    }

    private fun renderPolicyState(column: LinearLayout, state: ApplicationManagementUiState.Content) {
        column.addView(section("Device policy state"))
        val ps = state.policyState
        val assignment = ps?.assignment
        val sync = ps?.synchronization

        column.addView(text("Desired assignment: ${ps?.policy?.name ?: "None"}"))
        column.addView(text("Desired policy version: ${sync?.desiredPolicyVersion ?: assignment?.policyVersion ?: "—"}"))
        column.addView(text("Reported policy: ${sync?.reportedPolicyId ?: "Not reported"}"))
        column.addView(text("Reported policy version: ${sync?.reportedPolicyVersion ?: "Not reported"}"))

        if (sync == null) {
            column.addView(statusIndicator("Enforcement", "UNKNOWN"))
            column.addView(text("No backend enforcement result is available."))
        } else {
            column.addView(statusIndicator("Enforcement", sync.status.name))
            sync.lastRequestedAt?.let { column.addView(text("Last synchronization request: $it")) }
            sync.lastReportedAt?.let { column.addView(text("Last reported state: $it")) }
            sync.errorCode?.let { column.addView(text("Safe backend error code: $it")) }
            sync.command?.let { renderCommand(column, it) }
            if (sync.status != EnforcementStatus.APPLIED && !state.actionBusy && state.connection == ApplicationDataConnectionState.LIVE) {
                column.addView(button("Request policy synchronization") { viewModel.syncPolicy() })
            }
        }

        assignment?.policyId?.let { policyId ->
            column.addView(
                button("Remove policy assignment") {
                    confirm(
                        "Remove policy assignment?",
                        "This changes the desired backend policy state. It does not claim that device enforcement has already changed.",
                    ) { viewModel.removePolicy(policyId) }
                }.apply {
                    isEnabled = !state.actionBusy && state.connection == ApplicationDataConnectionState.LIVE
                },
            )
        }
    }

    private fun applicationCard(app: ApplicationInventoryItem) = LinearLayout(root.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 10, 0, 10)
        addView(text(app.displayName ?: app.packageName).apply { setTypeface(typeface, Typeface.BOLD) })
        addView(text(app.packageName))
        addView(text("Version: ${app.versionName ?: "Unavailable"} (${app.versionCode?.toString() ?: "—"})"))
        addView(text("Enabled: ${app.enabled?.toString() ?: "Unavailable"} • State: ${app.installState}"))
        addView(text("Inventory freshness: ${app.freshness.name}"))
        addView(text("Desired action: ${app.policyAction?.name ?: "Not reported"}"))
        addView(text("Reported action: ${app.reportedPolicyAction?.name ?: "Not reported"}"))
        addView(statusIndicator("Actual enforcement", app.enforcementStatus.name))
        if (app.enforcementStatus != EnforcementStatus.APPLIED) {
            addView(text("This application is not represented as successfully enforced."))
        }
    }

    private fun policyCard(policy: ApplicationPolicy, state: ApplicationManagementUiState.Content) =
        LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
            addView(text("${policy.name} • v${policy.version}").apply { setTypeface(typeface, Typeface.BOLD) })
            addView(text("Status: ${policy.status.name}"))
            addView(text("Rules: ${policy.rules.size}"))
            policy.description?.let { addView(text(it)) }
            addView(button("Assign to this device") {
                confirm(
                    "Assign ${policy.name} v${policy.version}?",
                    "This changes the desired policy for ${state.deviceName}. It does not mean enforcement has completed.",
                ) { viewModel.assignPolicy(policy.id) }
            }.apply {
                isEnabled = policy.status == PolicyStatus.ACTIVE &&
                    !state.actionBusy &&
                    state.connection == ApplicationDataConnectionState.LIVE
            })
            addView(button("Edit policy") { showEditPolicyDialog(policy) }.apply {
                isEnabled = !state.actionBusy && state.connection == ApplicationDataConnectionState.LIVE
            })
        }

    private fun showCreatePolicyDialog() {
        val name = EditText(root.context).apply { hint = "Policy name"; setSingleLine(true) }
        val description = EditText(root.context).apply { hint = "Description (optional)" }
        val rules = EditText(root.context).apply { hint = "Rules: com.example.app=BLOCK;com.example.safe=ALLOW" }
        val box = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
            addView(name)
            addView(description)
            addView(rules)
        }
        AlertDialog.Builder(root.context).setTitle("Create application policy").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val parsed = parseRules(rules.text.toString())
                if (parsed == null || name.text.toString().trim().isBlank()) {
                    Toast.makeText(root.context, "Invalid policy name or rules.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.createPolicy(name.text.toString().trim(), description.text.toString().trim().ifBlank { null }, parsed)
                }
            }.show()
    }

    private fun showEditPolicyDialog(policy: ApplicationPolicy) {
        val name = EditText(root.context).apply { setText(policy.name); setSingleLine(true) }
        val description = EditText(root.context).apply { setText(policy.description.orEmpty()) }
        val rules = EditText(root.context).apply { setText(policy.rules.joinToString(";") { it.packageName + "=" + it.action.name }) }
        val status = Spinner(root.context).apply {
            adapter = ArrayAdapter(root.context, android.R.layout.simple_spinner_dropdown_item, PolicyStatus.values().map { it.name })
            setSelection(policy.status.ordinal)
            contentDescription = "Policy status"
        }
        val box = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
            addView(name)
            addView(description)
            addView(rules)
            addView(status)
        }
        AlertDialog.Builder(root.context).setTitle("Edit policy v${policy.version}").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val parsed = parseRules(rules.text.toString())
                if (parsed == null || name.text.toString().trim().isBlank()) {
                    Toast.makeText(root.context, "Invalid policy name or rules.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.updatePolicy(
                        policy.copy(
                            name = name.text.toString().trim(),
                            description = description.text.toString().trim().ifBlank { null },
                            status = PolicyStatus.values()[status.selectedItemPosition],
                            rules = parsed,
                        ),
                        policy.version,
                    )
                }
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
        AlertDialog.Builder(root.context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ -> action() }
            .show()
    }

    private fun statusIndicator(label: String, value: String) =
        text("$label: $value").apply {
            contentDescription = "$label status: $value"
        }

    private fun title(value: String) = TextView(root.context).apply {
        text = value
        textSize = 26f
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun section(value: String) = TextView(root.context).apply {
        text = value
        textSize = 19f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 18, 0, 8)
    }

    private fun text(value: String) = TextView(root.context).apply {
        text = value
        textSize = 15f
        setPadding(0, 4, 0, 4)
    }

    private fun button(label: String, action: () -> Unit) = Button(root.context).apply {
        text = label
        minHeight = root.resources.getDimensionPixelSize(com.parento.admin.R.dimen.minimum_touch_target)
        contentDescription = label
        setOnClickListener { action() }
    }
}
