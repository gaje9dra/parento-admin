package com.parento.admin.ui

import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.parento.admin.R

class AdminLoginScreen(
    private val root: LinearLayout,
    private val viewModel: AuthenticationViewModel,
) {
    fun render(state: com.parento.admin.auth.AuthenticationState) {
        root.removeAllViews()
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        val padding = root.resources.getDimensionPixelSize(R.dimen.screen_padding)
        root.setPadding(padding, padding, padding, padding)

        val title = TextView(root.context).apply {
            text = root.context.getString(R.string.login_title)
            textSize = 28f
            gravity = Gravity.CENTER
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val subtitle = TextView(root.context).apply {
            text = root.context.getString(R.string.login_subtitle)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        root.addView(subtitle)

        val emailLayout = TextInputLayout(root.context).apply {
            hint = root.context.getString(R.string.login_email)
        }
        val email = TextInputEditText(root.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            singleLine = true
        }
        emailLayout.addView(email)
        root.addView(
            emailLayout,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = padding },
        )

        val passwordLayout = TextInputLayout(root.context).apply {
            hint = root.context.getString(R.string.login_password)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val password = TextInputEditText(root.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            singleLine = true
        }
        passwordLayout.addView(password)
        root.addView(
            passwordLayout,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val button = MaterialButton(root.context).apply {
            text = root.context.getString(R.string.login_button)
            isAllCaps = false
            minHeight = root.resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
            setOnClickListener {
                emailLayout.error = null
                passwordLayout.error = null
                val emailValue = email.text?.toString()?.trim().orEmpty()
                val passwordValue = password.text?.toString().orEmpty()
                if (emailValue.isEmpty() || !emailValue.contains('@')) {
                    emailLayout.error = root.context.getString(R.string.login_invalid_email)
                    return@setOnClickListener
                }
                if (passwordValue.isEmpty()) {
                    passwordLayout.error = root.context.getString(R.string.login_invalid_password)
                    return@setOnClickListener
                }
                viewModel.login(emailValue, passwordValue)
                password.text?.clear()
            }
        }
        root.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val status = TextView(root.context).apply {
            textSize = 14f
        }
        when (state) {
            com.parento.admin.auth.AuthenticationState.Unauthenticated -> Unit
            com.parento.admin.auth.AuthenticationState.Authenticating -> {
                button.isEnabled = false
                status.text = root.context.getString(R.string.login_loading)
            }
            is com.parento.admin.auth.AuthenticationState.AuthenticationError -> {
                status.text = state.message
            }
            is com.parento.admin.auth.AuthenticationState.Authenticated -> Unit
        }
        root.addView(status)
    }
}
