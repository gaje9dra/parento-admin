package com.parento.admin.ui

import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.parento.admin.R
import com.parento.admin.domain.DeviceLocation
import com.parento.admin.domain.LocationFreshness

class LocationScreen(
    private val root: FrameLayout,
    private val viewModel: LocationViewModel,
    private val mapView: MapView?,
    private val onBack: () -> Unit,
) {
    private var latestLocation: DeviceLocation? = null

    fun render(state: LocationUiState) {
        root.removeAllViews()
        val scroll = ScrollView(root.context)
        val content = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                root.resources.getDimensionPixelSize(R.dimen.screen_padding),
                root.resources.getDimensionPixelSize(R.dimen.screen_padding),
                root.resources.getDimensionPixelSize(R.dimen.screen_padding),
                root.resources.getDimensionPixelSize(R.dimen.screen_padding),
            )
        }

        content.addView(MaterialTextView(root.context).apply {
            text = root.context.getString(R.string.location_title)
            textSize = 24f
        })
        content.addView(MaterialButton(root.context).apply {
            text = root.context.getString(R.string.location_back)
            contentDescription = text
            minHeight = root.resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
            setOnClickListener { onBack() }
        })
        content.addView(MaterialTextView(root.context).apply {
            text = root.context.getString(R.string.location_device, viewModel.deviceId)
            textSize = 14f
        })
        content.addView(MaterialButton(root.context).apply {
            text = root.context.getString(R.string.location_refresh)
            contentDescription = root.context.getString(R.string.location_refresh)
            minHeight = root.resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
            setOnClickListener { viewModel.refresh() }
        })

        when (state) {
            LocationUiState.Loading -> content.addView(message(R.string.location_loading))
            LocationUiState.NeverReported -> content.addView(message(R.string.location_never_reported))
            LocationUiState.Unavailable -> content.addView(message(R.string.location_unavailable))
            LocationUiState.Unauthorized -> content.addView(message(R.string.location_unauthorized))
            LocationUiState.Revoked -> content.addView(message(R.string.location_revoked))
            is LocationUiState.Error -> {
                content.addView(messageText(state.message))
            }
            is LocationUiState.Available,
            is LocationUiState.Stale,
            is LocationUiState.VeryStale -> {
                latestLocation = when (state) {
                    is LocationUiState.Available -> state.location
                    is LocationUiState.Stale -> state.location
                    is LocationUiState.VeryStale -> state.location
                    else -> null
                }
                val location = latestLocation!!
                renderDetails(content, location)
                renderMap(content, location)
            }
        }

        scroll.addView(content)
        root.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
    }

    private fun renderDetails(content: LinearLayout, location: DeviceLocation) {
        content.addView(messageText(
            root.context.getString(
                R.string.location_freshness,
                freshnessLabel(location.freshness),
            ),
        ))
        content.addView(messageText(
            root.context.getString(
                R.string.location_coordinates,
                location.latitude,
                location.longitude,
            ),
        ))
        content.addView(messageText(
            if (location.accuracyMeters != null)
                root.context.getString(R.string.location_accuracy, location.accuracyMeters)
            else root.context.getString(R.string.location_accuracy_unavailable),
        ))
        content.addView(messageText(timestampLine(R.string.location_observed_at, location.observedAtEpochMillis)))
        content.addView(messageText(timestampLine(R.string.location_received_at, location.receivedAtEpochMillis)))
    }

    private fun renderMap(content: LinearLayout, location: DeviceLocation) {
        if (!location.hasValidCoordinates()) return
        val view = mapView ?: run {
            content.addView(messageText(root.context.getString(R.string.location_map_not_configured)))
            return
        }
        content.addView(view, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            root.resources.getDimensionPixelSize(R.dimen.location_map_height),
        ))
        view.getMapAsync { map ->
            val point = LatLng(location.latitude!!, location.longitude!!)
            map.clear()
            map.uiSettings.isMapToolbarEnabled = false
            map.uiSettings.isZoomControlsEnabled = true
            map.addMarker(MarkerOptions().position(point).title(root.context.getString(R.string.location_marker_title)))
            location.accuracyMeters?.takeIf { it.isFinite() && it >= 0.0 }?.let { accuracy ->
                map.addCircle(
                    CircleOptions()
                        .center(point)
                        .radius(accuracy)
                        .strokeWidth(2f)
                        .strokeColor(Color.DKGRAY)
                        .fillColor(0x22000000),
                )
            }
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 15f))
        }
    }

    private fun message(resId: Int): View = messageText(root.context.getString(resId))

    private fun messageText(value: String) = MaterialTextView(root.context).apply {
        text = value
        textSize = 16f
        setPadding(0, root.resources.getDimensionPixelSize(R.dimen.item_spacing), 0, root.resources.getDimensionPixelSize(R.dimen.section_spacing))
    }

    private fun freshnessLabel(freshness: LocationFreshness): String = when (freshness) {
        LocationFreshness.FRESH -> root.context.getString(R.string.location_fresh)
        LocationFreshness.STALE -> root.context.getString(R.string.location_stale)
        LocationFreshness.VERY_STALE -> root.context.getString(R.string.location_very_stale)
        LocationFreshness.UNKNOWN -> root.context.getString(R.string.location_freshness_unknown)
    }

    private fun timestampLine(labelRes: Int, epochMillis: Long?): String =
        if (epochMillis == null) {
            root.context.getString(R.string.location_timestamp_unavailable, root.context.getString(labelRes))
        } else {
            root.context.getString(labelRes, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(epochMillis)))
        }
}