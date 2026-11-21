package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CatalogPricing
import com.example.model.CustomerProfile
import com.example.model.DeliveryMethod
import com.example.ui.components.LocationMapPicker
import com.example.ui.components.formatCurrency
import com.example.viewmodel.CustomerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderWizardScreen(
    profile: CustomerProfile,
    viewModel: CustomerViewModel,
    onBack: () -> Unit,
    onOrderPlaced: (String) -> Unit,
) {
    val pricing by viewModel.catalogPricing.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmittingOrder.collectAsStateWithLifecycle()
    val submitError by viewModel.submitError.collectAsStateWithLifecycle()

    var currentStep by remember { mutableIntStateOf(0) }
    val stepTitles = listOf(
        "1. Select Service",
        "2. Select Fabric",
        "3. Select Dress Type",
        "4. Quantity & Care",
        "5. Delivery Method",
        "6. Bill Summary",
    )

    val services = pricing.servicePrices.keys.toList().ifEmpty { CatalogPricing.defaultServices().keys.toList() }
    val fabrics = pricing.fabricPrices.keys.toList().ifEmpty { CatalogPricing.defaultFabrics().keys.toList() }
    val dressTypes = pricing.dressTypePrices.keys.toList().ifEmpty { CatalogPricing.defaultDressTypes().keys.toList() }

    var selectedService by remember(services) { mutableStateOf(services.first()) }
    var selectedFabric by remember(fabrics) { mutableStateOf(fabrics.first()) }
    var selectedDressType by remember(dressTypes) { mutableStateOf(dressTypes.first()) }
    var quantity by remember { mutableIntStateOf(1) }
    var roseWaterAdded by remember { mutableStateOf(false) }
    var extraStarchAdded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var deliveryMethod by remember { mutableStateOf(DeliveryMethod.DOORSTEP) }
    var pickupAddress by remember(profile) { mutableStateOf(profile.address) }
    var locationLabel by remember(profile) { mutableStateOf(profile.locationLabel) }
    var latitude by remember(profile) { mutableDoubleStateOf(profile.latitude) }
    var longitude by remember(profile) { mutableDoubleStateOf(profile.longitude) }

    BackHandler {
        if (currentStep > 0) currentStep -= 1 else onBack()
    }

    val serviceRate = pricing.servicePrices[selectedService] ?: 30.0
    val fabricRate = pricing.fabricPrices[selectedFabric] ?: 0.0
    val dressRate = pricing.dressTypePrices[selectedDressType] ?: 0.0
    val unitPrice = serviceRate + fabricRate + dressRate
    val addOnPrice =
        (if (roseWaterAdded) pricing.roseWaterFee else 0.0) +
            (if (extraStarchAdded) pricing.extraStarchFee else 0.0)
    val deliveryFee =
        if (deliveryMethod == DeliveryMethod.DOORSTEP) pricing.doorstepDeliveryFee else 0.0
    val totalPrice = (unitPrice * quantity) + addOnPrice + deliveryFee

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text(
                            text = stepTitles[currentStep],
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Step ${currentStep + 1} of ${stepTitles.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (currentStep > 0) currentStep -= 1 else onBack() },
                        modifier = Modifier.testTag("wizard_back_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Column {
                        Text(
                            text = "Estimated Total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatCurrency(totalPrice),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (currentStep > 0) {
                            OutlinedButton(
                                onClick = { currentStep -= 1 },
                                modifier = Modifier.testTag("wizard_prev_button"),
                            ) {
                                Text("Back")
                            }
                        }
                        if (currentStep < stepTitles.lastIndex) {
                            Button(
                                onClick = { currentStep += 1 },
                                modifier = Modifier.testTag("wizard_next_button"),
                            ) {
                                Text("Next")
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.placeOrder(
                                        profile = profile,
                                        serviceType = selectedService,
                                        fabricType = selectedFabric,
                                        dressType = selectedDressType,
                                        quantity = quantity,
                                        roseWaterAdded = roseWaterAdded,
                                        extraStarchAdded = extraStarchAdded,
                                        deliveryMethod = deliveryMethod,
                                        pickupAddress = pickupAddress.ifBlank { profile.address },
                                        locationLabel = locationLabel,
                                        latitude = latitude,
                                        longitude = longitude,
                                        notes = notes,
                                        pricing = pricing,
                                        onOrderCreated = onOrderPlaced,
                                    )
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier.testTag("confirm_order_button"),
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text("Confirm Order")
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
            ) {
                LinearProgressIndicator(
                    progress = { (currentStep + 1).toFloat() / stepTitles.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )

                if (submitError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = submitError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                AnimatedContent(
                    targetState = currentStep,
                    label = "orderWizardStep",
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) { step ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (step) {
                            0 -> {
                                Text(
                                    text = "Which garment care service do you need?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                services.forEach { name ->
                                    val price = pricing.servicePrices[name] ?: 0.0
                                    OptionSelectCard(
                                        title = name,
                                        subtitle = "Base rate ${formatCurrency(price)} per garment",
                                        selected = selectedService == name,
                                        onClick = { selectedService = name },
                                        testTag = "service_option_$name",
                                    )
                                }
                            }

                            1 -> {
                                Text(
                                    text = "Choose the fabric or specialty finish (includes Starched Shirt & Sarees).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                fabrics.forEach { fabric ->
                                    val extra = pricing.fabricPrices[fabric] ?: 0.0
                                    val subtitle = when {
                                        fabric.equals("Starched Shirt", ignoreCase = true) ->
                                            "Crisp collar & cuff starch finish (+${formatCurrency(extra)})"
                                        fabric.equals("Sarees", ignoreCase = true) ->
                                            "Delicate fold, pleat press & saree care (+${formatCurrency(extra)})"
                                        extra == 0.0 -> "Standard fabric rate (included)"
                                        else -> "+${formatCurrency(extra)} fabric care"
                                    }
                                    OptionSelectCard(
                                        title = fabric,
                                        subtitle = subtitle,
                                        selected = selectedFabric == fabric,
                                        onClick = {
                                            selectedFabric = fabric
                                            if (fabric.equals("Sarees", ignoreCase = true) && dressTypes.contains("Saree")) {
                                                selectedDressType = "Saree"
                                            } else if (fabric.equals("Starched Shirt", ignoreCase = true) && dressTypes.contains("Shirt / Kurta")) {
                                                selectedDressType = "Shirt / Kurta"
                                            }
                                        },
                                        testTag = "fabric_option_$fabric",
                                    )
                                }
                            }

                            2 -> {
                                Text(
                                    text = "Select the dress or garment type.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                dressTypes.forEach { dress ->
                                    val rate = pricing.dressTypePrices[dress] ?: 0.0
                                    OptionSelectCard(
                                        title = dress,
                                        subtitle = "+${formatCurrency(rate)} garment handling",
                                        selected = selectedDressType == dress,
                                        onClick = { selectedDressType = dress },
                                        testTag = "dress_option_$dress",
                                    )
                                }
                            }

                            3 -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(16.dp),
                                    ) {
                                        Column {
                                            Text("Garment Quantity", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = "${formatCurrency(unitPrice)} per item",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { if (quantity > 1) quantity -= 1 },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("quantity_decrease_button"),
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Decrease quantity")
                                            }
                                            Text(
                                                text = quantity.toString(),
                                                style = MaterialTheme.typography.titleLarge,
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                            )
                                            IconButton(
                                                onClick = { if (quantity < 200) quantity += 1 },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("quantity_increase_button"),
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Increase quantity")
                                            }
                                        }
                                    }
                                }

                                // Rose Water Option (explicitly requested!)
                                Surface(
                                    color = if (roseWaterAdded) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(
                                        1.dp,
                                        if (roseWaterAdded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(16.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocalFlorist,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Rose Water Fragrance Rinse",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "Botanical rose mist during steam finishing (+${formatCurrency(pricing.roseWaterFee)})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = roseWaterAdded,
                                            onCheckedChange = { roseWaterAdded = it },
                                            modifier = Modifier.testTag("rose_water_switch"),
                                        )
                                    }
                                }

                                // Extra Starch Option
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
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Extra Crisp Starch Finish",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "Double-crisp finish for shirts & cotton sarees (+${formatCurrency(pricing.extraStarchFee)})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = extraStarchAdded,
                                            onCheckedChange = { extraStarchAdded = it },
                                            modifier = Modifier.testTag("extra_starch_switch"),
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { if (it.length <= 300) notes = it },
                                    label = { Text("Special Care Instructions (Optional)") },
                                    placeholder = { Text("e.g. Fold sarees flat, light starch on cuffs") },
                                    minLines = 2,
                                    maxLines = 3,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("order_notes_input"),
                                )
                            }

                            4 -> {
                                DeliveryMethod.entries.forEach { method ->
                                    val feeText = if (method == DeliveryMethod.DOORSTEP) {
                                        "+${formatCurrency(pricing.doorstepDeliveryFee)}"
                                    } else {
                                        "Free"
                                    }
                                    OptionSelectCard(
                                        title = "${method.label} ($feeText)",
                                        subtitle = method.subtitle,
                                        selected = deliveryMethod == method,
                                        onClick = { deliveryMethod = method },
                                        testTag = "delivery_option_${method.raw}",
                                    )
                                }

                                if (deliveryMethod == DeliveryMethod.DOORSTEP) {
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = pickupAddress,
                                        onValueChange = { pickupAddress = it },
                                        label = { Text("Pickup & Delivery Address") },
                                        minLines = 2,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("order_address_input"),
                                    )
                                    LocationMapPicker(
                                        latitude = latitude,
                                        longitude = longitude,
                                        locationLabel = locationLabel,
                                        onLocationPicked = { lat, lng, label, suggested ->
                                            latitude = lat
                                            longitude = lng
                                            locationLabel = label
                                            if (!suggested.isNullOrBlank()) {
                                                pickupAddress = suggested
                                            }
                                        },
                                    )
                                }
                            }

                            5 -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(18.dp),
                                    ) {
                                        Text(
                                            text = "KJ IRON'S Bill Summary",
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        BillRow("Service", selectedService, formatCurrency(serviceRate))
                                        BillRow("Fabric", selectedFabric, formatCurrency(fabricRate))
                                        BillRow("Dress Type", selectedDressType, formatCurrency(dressRate))
                                        BillRow(
                                            "Garments Subtotal",
                                            "$quantity × ${formatCurrency(unitPrice)}",
                                            formatCurrency(unitPrice * quantity),
                                        )
                                        if (roseWaterAdded) {
                                            BillRow("Add-on", "Rose Water Fragrance Rinse", formatCurrency(pricing.roseWaterFee))
                                        }
                                        if (extraStarchAdded) {
                                            BillRow("Add-on", "Extra Crisp Starch", formatCurrency(pricing.extraStarchFee))
                                        }
                                        BillRow(
                                            "Delivery",
                                            deliveryMethod.label,
                                            if (deliveryFee == 0.0) "Free" else formatCurrency(deliveryFee),
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("Total Payable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = formatCurrency(totalPrice),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        Text(
                                            text = "Customer: ${profile.name} (${profile.mobile})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Location: ${pickupAddress.ifBlank { profile.address }} ($locationLabel)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (notes.isNotBlank()) {
                                            Text(
                                                text = "Notes: $notes",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionSelectCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BillRow(label: String, detail: String, amount: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
        Text(amount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}
