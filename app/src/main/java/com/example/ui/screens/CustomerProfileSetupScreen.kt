package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.ProfilePhotoEncoder
import com.example.model.CustomerProfile
import com.example.ui.components.LocationMapPicker
import com.example.ui.components.ProfileAvatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProfileSetupScreen(
    initialName: String,
    initialEmail: String,
    existingProfile: CustomerProfile?,
    isBusy: Boolean,
    errorMessage: String?,
    onSaveProfile: (
        name: String,
        mobile: String,
        address: String,
        locationLabel: String,
        lat: Double,
        lng: Double,
        photoBase64: String,
    ) -> Unit,
    onBack: (() -> Unit)?,
    onSignOut: () -> Unit,
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember(existingProfile) {
        mutableStateOf(existingProfile?.name ?: initialName)
    }
    var mobile by remember(existingProfile) {
        mutableStateOf(existingProfile?.mobile ?: "")
    }
    var address by remember(existingProfile) {
        mutableStateOf(existingProfile?.address ?: "")
    }
    var locationLabel by remember(existingProfile) {
        mutableStateOf(existingProfile?.locationLabel ?: "Bengaluru")
    }
    var latitude by remember(existingProfile) {
        mutableDoubleStateOf(existingProfile?.latitude ?: 12.9716)
    }
    var longitude by remember(existingProfile) {
        mutableDoubleStateOf(existingProfile?.longitude ?: 77.5946)
    }
    var photoBase64 by remember(existingProfile) {
        mutableStateOf(existingProfile?.photoBase64 ?: "")
    }
    var isEncodingPhoto by remember { mutableStateOf(false) }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isEncodingPhoto = true
            localValidationError = null
            ProfilePhotoEncoder.encodeUriToBase64(context, uri)
                .onSuccess { encoded ->
                    photoBase64 = encoded
                }
                .onFailure {
                    localValidationError = "Could not process the selected photo. Please try a different image."
                }
            isEncodingPhoto = false
        }
    }

    val openPhotoPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(
                        text = if (existingProfile == null) "Complete Customer Profile" else "Edit Profile & DP",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut, modifier = Modifier.testTag("profile_sign_out_button")) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = !isBusy && !isEncodingPhoto) { openPhotoPicker() }
                                .testTag("profile_dp_avatar_picker"),
                        ) {
                            ProfileAvatar(
                                photoBase64 = photoBase64,
                                name = name,
                                size = 76.dp,
                                contentDescription = "Customer profile picture",
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                                modifier = Modifier.size(26.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isEncodingPhoto) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PhotoCamera,
                                            contentDescription = "Choose profile picture",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Profile Picture (DP)",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (photoBase64.isBlank()) {
                                    "Add a photo so your pickup rider recognizes you"
                                } else {
                                    "Your DP syncs securely with your profile"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = openPhotoPicker,
                                    enabled = !isBusy && !isEncodingPhoto,
                                    modifier = Modifier.testTag("pick_profile_dp_button"),
                                ) {
                                    Text(if (photoBase64.isBlank()) "Add DP" else "Change DP")
                                }
                                if (photoBase64.isNotBlank()) {
                                    TextButton(
                                        onClick = { photoBase64 = "" },
                                        enabled = !isBusy && !isEncodingPhoto,
                                        modifier = Modifier.testTag("remove_profile_dp_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (initialEmail.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Signed in as $initialEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                val displayError = localValidationError ?: errorMessage
                if (displayError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                    ) {
                        Text(
                            text = displayError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; localValidationError = null },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it; localValidationError = null },
                    label = { Text("Mobile Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_mobile_input"),
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; localValidationError = null },
                    label = { Text("Doorstep Pickup Address & Landmark") },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_address_input"),
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = locationLabel,
                    onValueChange = { locationLabel = it },
                    label = { Text("Area / Locality Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_location_label_input"),
                )

                Spacer(Modifier.height(18.dp))

                LocationMapPicker(
                    latitude = latitude,
                    longitude = longitude,
                    locationLabel = locationLabel,
                    onLocationPicked = { lat, lng, label, suggestedAddress ->
                        latitude = lat
                        longitude = lng
                        locationLabel = label
                        if (address.isBlank() && !suggestedAddress.isNullOrBlank()) {
                            address = suggestedAddress
                        }
                    },
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        when {
                            name.trim().isEmpty() -> localValidationError = "Please enter your name."
                            mobile.trim().length < 6 -> localValidationError = "Please enter a valid mobile number (at least 6 digits)."
                            address.trim().length < 3 -> localValidationError = "Please enter your pickup address."
                            else -> {
                                localValidationError = null
                                onSaveProfile(
                                    name,
                                    mobile,
                                    address,
                                    locationLabel,
                                    latitude,
                                    longitude,
                                    photoBase64,
                                )
                            }
                        }
                    },
                    enabled = !isBusy && !isEncodingPhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_profile_button"),
                ) {
                    if (isBusy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Saving...")
                        }
                    } else {
                        Text(if (existingProfile == null) "Save & Start Ordering" else "Save Changes")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
