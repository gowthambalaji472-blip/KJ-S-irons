package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

@Composable
fun LocationMapPicker(
    latitude: Double,
    longitude: Double,
    locationLabel: String,
    onLocationPicked: (lat: Double, lng: Double, label: String, suggestedAddress: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDetecting by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf<String?>(null) }

    val hasValidMapsKey = remember {
        val key = BuildConfig.MAPS_API_KEY
        key.isNotBlank() && key != "MY_MAPS_API_KEY"
    }

    val fetchCurrentGps: () -> Unit = {
        scope.launch {
            isDetecting = true
            statusNote = null
            val coords = detectDeviceCoordinates(context)
            if (coords != null) {
                val geo = reverseGeocodeAddress(context, coords.first, coords.second)
                onLocationPicked(
                    coords.first,
                    coords.second,
                    geo.first,
                    geo.second,
                )
                statusNote = "GPS pin updated"
            } else {
                statusNote = "GPS unavailable on this device. Enter address or tap map."
            }
            isDetecting = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchCurrentGps()
        } else {
            statusNote = "Location permission denied. You can still pin manually."
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pickup & Delivery Map Pin",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = locationLabel.ifBlank {
                        String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = {
                    val hasFine = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasFine || hasCoarse) {
                        fetchCurrentGps()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            )
                        )
                    }
                },
                enabled = !isDetecting,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.testTag("detect_gps_button"),
            ) {
                if (isDetecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Detect GPS",
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text("Use GPS")
            }
        }

        Spacer(Modifier.height(10.dp))

        if (hasValidMapsKey) {
            val targetLatLng = LatLng(latitude, longitude)
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(targetLatLng, 15f)
            }
            LaunchedEffect(latitude, longitude) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 15f)
                )
            }
            val markerState = remember(latitude, longitude) {
                MarkerState(position = targetLatLng)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        myLocationButtonEnabled = false,
                    ),
                    onMapClick = { latLng ->
                        scope.launch {
                            val geo = reverseGeocodeAddress(context, latLng.latitude, latLng.longitude)
                            onLocationPicked(latLng.latitude, latLng.longitude, geo.first, geo.second)
                        }
                    },
                ) {
                    Marker(
                        state = markerState,
                        title = "Pickup Pin",
                        snippet = locationLabel,
                    )
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Pinned Coordinates",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = locationLabel.ifBlank { "Selected Pickup Pin" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Lat %.5f, Lng %.5f",
                                    latitude,
                                    longitude
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tip: Add MAPS_API_KEY in AI Studio Secrets to render interactive Google Map tiles here. GPS coordinate detection works right away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (statusNote != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusNote.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun detectDeviceCoordinates(context: Context): Pair<Double, Double>? {
    return try {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val current = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        if (current != null) {
            current.latitude to current.longitude
        } else {
            val last = client.lastLocation.await()
            if (last != null) last.latitude to last.longitude else null
        }
    } catch (_: Exception) {
        null
    }
}

private suspend fun reverseGeocodeAddress(
    context: Context,
    lat: Double,
    lng: Double,
): Pair<String, String?> = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lng, 1) { list ->
                    if (cont.isActive) cont.resume(list.firstOrNull())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
        }
        if (address != null) {
            val shortLabel = listOfNotNull(
                address.subLocality,
                address.locality,
                address.adminArea
            ).distinct().joinToString(", ").ifBlank { "Pinned Location" }
            val fullLine = (0..address.maxAddressLineIndex)
                .mapNotNull { address.getAddressLine(it) }
                .joinToString(", ")
                .ifBlank { null }
            shortLabel to fullLine
        } else {
            String.format(Locale.US, "Pin (%.4f, %.4f)", lat, lng) to null
        }
    } catch (_: Exception) {
        String.format(Locale.US, "Pin (%.4f, %.4f)", lat, lng) to null
    }
}
