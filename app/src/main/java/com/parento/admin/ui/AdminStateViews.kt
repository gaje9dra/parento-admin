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
        val container = centeredContainer(context)
        val progress = ProgressBar(context).apply {
            isIndeterminate = true
            contentDescription = context.getString(R.string.loading_content_description)
        }
        container.addView(progress, centeredLayoutParams())
        return container
    }

    fun empty(context: Context): View {
        val container = stateCard(context)
        addText(
            container,
            context.getString(R.string.empty_devices_title),
            20f,
        )
        addText(
            container,
            context.getString(R.string.empty_devices_message),
            16f,
        )
        return container
    }

    fun error(
        context: Context,
        message: String,
        canRetry: Boolean,
        onRetry: (() -> Unit)?,
    ): View {
        val container = stateCard(context)
        addText(container, context.getString(R.string.error_title), 20f)
        addText(container, message, 16f)
        if (canRetry && onRetry != null) {
            val retry = MaterialButton(context).apply {
                text = context.getString(R.string.retry)
                minHeight = context.resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
                setOnClickListener { onRetry() }
            }
            container.addView(retry)
        }
        return container
    }

    private fun centeredContainer(context: Context): FrameLayout =
        FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            contentDescription = context.getString(R.string.loading_content_description)
        }

    private fun centeredLayoutParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        )

    private fun stateCard(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                context.resources.getDimensionPixelSize(R.dimen.card_padding),
                context.resources.getDimensionPixelSize(R.dimen.card_padding),
                context.resources.getDimensionPixelSize(R.dimen.card_padding),
                context.resources.getDimensionPixelSize(R.dimen.card_padding),
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            background = MaterialCardView(context).background
        }

    private fun addText(container: LinearLayout, text: String, sizeSp: Float) {
        val view = MaterialTextView(container.context).apply {
            this.text = text
            textSize = sizeSp
            setPadding(0, 0, 0, container.context.resources.getDimensionPixelSize(R.dimen.item_spacing))
        }
        container.addView(view)
    }
}
