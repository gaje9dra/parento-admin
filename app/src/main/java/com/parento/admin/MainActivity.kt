package com.parento.admin

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textview.MaterialTextView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = MaterialTextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            setPadding(32, 64, 32, 16)
        }

        val status = MaterialTextView(this).apply {
            text = getString(R.string.phase_status)
            textSize = 16f
            setPadding(32, 16, 32, 32)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(status)
        }

        setContentView(container)
    }
}
