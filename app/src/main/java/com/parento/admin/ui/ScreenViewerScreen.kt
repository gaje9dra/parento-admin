package com.parento.admin.ui

import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.parento.admin.screensharing.ScreenSharingSession

/**
 * Dedicated viewer surface. It intentionally renders no pixels until a real,
 * authenticated frame transport exists. This prevents fabricated or stale
 * screen content from being presented as live.
 */
class ScreenViewerScreen(
    private val root: FrameLayout,
) {
    fun render(session: ScreenSharingSession?) {
        root.removeAllViews()
        val surface = FrameLayout(root.context).apply {
            setBackgroundColor(Color.BLACK)
        }
        surface.addView(TextView(root.context).apply {
            text = if (session?.status?.name == "ACTIVE") {
                "LIVE VIEWER

No approved media-frame transport is available in the current backend contract."
            } else {
                "SCREEN VIEWER

No live screen content."
            }
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            contentDescription = "Screen viewer status"
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        root.addView(surface)
    }
}
