package com.parento.admin.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.parento.admin.R

object AdminStateViews {
    fun loading(context: Context): View {
        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.state_min_height),
            )
            contentDescription = context.getString(R.string.loading_content_description)
        }
        container.addView(
            ProgressBar(context).apply {
                isIndeterminate = true
                contentDescription = context.getString(R.string.loading_content_description)
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        return container
    }

    fun empty(context: Context): View =
        stateCard(context).apply {
            addText(context.getString(R.string.empty_devices_title), 20f)
            addText(context.getString(R.string.empty_devices_message), 16f)
        }

    fun error(context: Context, message: String, canRetry: Boolean, onRetry: (() -> Unit)?): View =
        stateCard(context).apply {
            addText(context.getString(R.string.error_title), 20f)
            addText(message, 16f)
            if (canRetry && onRetry != null) {
                addView(
                    MaterialButton(context).apply {
                        text = context.getString(R.string.retry)
                        minHeight = context.resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
                        setOnClickListener { onRetry() }
                    },
                )
            }
        }

    private fun stateCard(context: Context): LinearLayout {
        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                context.resources.getDimensionPixelSize(R.dimen.card_padding),
                context.resources.getDimensionPixelSize(R.dimen.card_padding),
                context.resources.getDimensionPixelSize(R.dimen.card_padding),
                context.resources.getDimensionPixelSize(R.dimen.card_padding),
            )
        }
        card.addView(content)
        return content
    }

    private fun LinearLayout.addText(text: String, sizeSp: Float) {
        addView(
            MaterialTextView(context).apply {
                this.text = text
                textSize = sizeSp
                setPadding(0, 0, 0, context.resources.getDimensionPixelSize(R.dimen.item_spacing))
            },
        )
    }
}
