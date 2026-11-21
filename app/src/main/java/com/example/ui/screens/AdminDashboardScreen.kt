package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.NotificationHelper
import com.example.model.AdminAccount
import com.example.model.CatalogPricing
import com.example.model.LaundryOrder
import com.example.model.OrderStatus
import com.example.model.UiState
import com.example.ui.components.InAppNotificationBanner
import com.example.ui.components.NotificationBellButton
import com.example.ui.components.NotificationsBottomSheet
import com.example.ui.components.OrderSummaryCard
import com.example.viewmodel.AdminViewModel

private enum class AdminOrderFilter(val label: String) {
    PENDING("Pending"),
    ACTIVE("Active"),
    READY("Ready & Delivery"),
    ALL("All"),
    HISTORY("Done / Rejected"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    admin: AdminAccount,
    viewModel: AdminViewModel,
    onOpenOrder: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val ordersState by viewModel.allOrdersState.collectAsStateWithLifecycle()
    val catalogPricing by viewModel.catalogPricing.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val activeBanner by viewModel.activeBanner.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val updatingOrderId by viewModel.updatingOrderId.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf(AdminOrderFilter.ALL) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showPricingEditor by remember { mutableStateOf(false) }
    var rejectTargetOrderId by remember { mutableStateOf<String?>(null) }
    var rejectionReason by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    var hasNotifPermission by remember {
        mutableStateOf(NotificationHelper(context).hasNotificationPermission())
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifPermission = granted }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${stringResource(R.string.brand_title)} Admin",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "Slot #${admin.slotNumber} • Central Queue",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showPricingEditor = true },
                        modifier = Modifier.testTag("admin_pricing_button"),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rates")
                    }
                    NotificationBellButton(
                        unreadCount = notifications.count { !it.isRead },
                        onClick = { showNotificationsSheet = true },
                    )
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("admin_sign_out_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.cd_sign_out))
                    }
                },
            )
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
                    .widthIn(max = 680.dp)
                    .fillMaxSize(),
            ) {
                InAppNotificationBanner(
                    notification = activeBanner,
                    onOpenOrder = onOpenOrder,
                    onDismiss = viewModel::dismissBanner,
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Enable notifications to receive instant alerts for new customer orders.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier.testTag("admin_enable_notifications_button"),
                            ) {
                                Text("Allow")
                            }
                        }
                    }
                }

                when (val state = ordersState) {
                    UiState.Loading -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator()
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }

                    is UiState.Success -> {
                        val allOrders = state.data
                        val pendingCount = allOrders.count { it.orderStatus == OrderStatus.PENDING }
                        val activeCount = allOrders.count {
                            it.orderStatus == OrderStatus.ACCEPTED || it.orderStatus == OrderStatus.PROCESSING
                        }
                        val readyCount = allOrders.count {
                            it.orderStatus == OrderStatus.READY || it.orderStatus == OrderStatus.OUT_FOR_DELIVERY
                        }
                        val doneCount = allOrders.count { it.orderStatus.isTerminal }

                        val filteredOrders = when (selectedFilter) {
                            AdminOrderFilter.ALL -> allOrders
                            AdminOrderFilter.PENDING -> allOrders.filter { it.orderStatus == OrderStatus.PENDING }
                            AdminOrderFilter.ACTIVE -> allOrders.filter {
                                it.orderStatus == OrderStatus.ACCEPTED || it.orderStatus == OrderStatus.PROCESSING
                            }
                            AdminOrderFilter.READY -> allOrders.filter {
                                it.orderStatus == OrderStatus.READY || it.orderStatus == OrderStatus.OUT_FOR_DELIVERY
                            }
                            AdminOrderFilter.HISTORY -> allOrders.filter { it.orderStatus.isTerminal }
                        }

                        // Central Summary KPIs
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            AdminMetricTile("Pending", pendingCount, highlighted = pendingCount > 0, modifier = Modifier.weight(1f))
                            AdminMetricTile("Active", activeCount, highlighted = false, modifier = Modifier.weight(1f))
                            AdminMetricTile("Ready", readyCount, highlighted = false, modifier = Modifier.weight(1f))
                            AdminMetricTile("Done", doneCount, highlighted = false, modifier = Modifier.weight(1f))
                        }

                        // Filter Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            AdminOrderFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text(filter.label) },
                                    modifier = Modifier.testTag("admin_filter_${filter.name.lowercase()}"),
                                )
                            }
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (filteredOrders.isEmpty()) {
                                item(key = "admin_empty") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.large,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(28.dp),
                                        ) {
                                            Text("No orders in this queue", style = MaterialTheme.typography.titleMedium)
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "New customer orders appear here in real time.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(filteredOrders, key = { it.id }) { order ->
                                    AdminOrderCard(
                                        order = order,
                                        isUpdating = updatingOrderId == order.id,
                                        onOpenOrder = { onOpenOrder(order.id) },
                                        onAccept = {
                                            viewModel.updateOrderStatus(order.id, OrderStatus.ACCEPTED)
                                        },
                                        onReject = {
                                            rejectionReason = ""
                                            rejectTargetOrderId = order.id
                                        },
                                        onAdvance = { nextStatus ->
                                            viewModel.updateOrderStatus(order.id, nextStatus)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (rejectTargetOrderId != null) {
        AlertDialog(
            onDismissRequest = { rejectTargetOrderId = null },
            title = { Text("Reject Order") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Optionally add a note for the customer explaining why this order is declined:")
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { if (it.length <= 300) rejectionReason = it },
                        label = { Text("Reason (Optional)") },
                        placeholder = { Text("e.g. Outside pickup slot today") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reject_reason_input"),
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val id = rejectTargetOrderId
                        rejectTargetOrderId = null
                        if (id != null) {
                            viewModel.updateOrderStatus(
                                orderId = id,
                                newStatus = OrderStatus.REJECTED,
                                rejectionReason = rejectionReason.ifBlank { "Declined by shop admin" },
                            )
                        }
                    },
                    modifier = Modifier.testTag("confirm_reject_button"),
                ) {
                    Text("Reject Order")
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectTargetOrderId = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showNotificationsSheet) {
        NotificationsBottomSheet(
            notifications = notifications,
            onDismiss = { showNotificationsSheet = false },
            onSelectOrder = onOpenOrder,
            onMarkAllRead = viewModel::markAllRead,
        )
    }

    if (showPricingEditor) {
        CatalogPricingEditorSheet(
            currentPricing = catalogPricing,
            onDismiss = { showPricingEditor = false },
            onSave = { updated ->
                viewModel.saveCatalogPricing(updated) {
                    showPricingEditor = false
                }
            },
        )
    }
}

@Composable
private fun AdminMetricTile(
    label: String,
    count: Int,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdminOrderCard(
    order: LaundryOrder,
    isUpdating: Boolean,
    onOpenOrder: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onAdvance: (OrderStatus) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OrderSummaryCard(
            order = order,
            showCustomerInfo = true,
            onClick = onOpenOrder,
        )
        val status = order.orderStatus
        if (status == OrderStatus.PENDING) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = !isUpdating,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("reject_order_button_${order.id}"),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reject")
                }
                Button(
                    onClick = onAccept,
                    enabled = !isUpdating,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("accept_order_button_${order.id}"),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Accept")
                }
            }
        } else {
            val nextStatus = status.nextAdminStatus()
            val actionLabel = status.nextAdminActionLabel()
            if (nextStatus != null && actionLabel != null) {
                Button(
                    onClick = { onAdvance(nextStatus) },
                    enabled = !isUpdating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("advance_order_button_${order.id}"),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogPricingEditorSheet(
    currentPricing: CatalogPricing,
    onDismiss: () -> Unit,
    onSave: (CatalogPricing) -> Unit,
) {
    val serviceRates = remember(currentPricing) {
        mutableStateMapOf<String, String>().apply {
            currentPricing.servicePrices.forEach { (k, v) -> put(k, v.toInt().toString()) }
        }
    }
    val fabricRates = remember(currentPricing) {
        mutableStateMapOf<String, String>().apply {
            currentPricing.fabricPrices.forEach { (k, v) -> put(k, v.toInt().toString()) }
        }
    }
    var roseWaterText by remember(currentPricing) {
        mutableStateOf(currentPricing.roseWaterFee.toInt().toString())
    }
    var starchText by remember(currentPricing) {
        mutableStateOf(currentPricing.extraStarchFee.toInt().toString())
    }
    var deliveryText by remember(currentPricing) {
        mutableStateOf(currentPricing.doorstepDeliveryFee.toInt().toString())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("Edit Live Shop Pricing", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Updated rates sync immediately for all customers placing new orders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Text("Services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            serviceRates.keys.toList().forEach { name ->
                PriceFieldRow(
                    label = name,
                    value = serviceRates[name].orEmpty(),
                    onValueChange = { serviceRates[name] = it.filter { ch -> ch.isDigit() || ch == '.' } },
                )
            }

            HorizontalDivider()
            Text("Fabrics (incl. Starched Shirt & Sarees)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            fabricRates.keys.toList().forEach { name ->
                PriceFieldRow(
                    label = name,
                    value = fabricRates[name].orEmpty(),
                    onValueChange = { fabricRates[name] = it.filter { ch -> ch.isDigit() || ch == '.' } },
                )
            }

            HorizontalDivider()
            Text("Add-ons & Delivery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            PriceFieldRow("Rose Water Rinse Fee", roseWaterText) {
                roseWaterText = it.filter { ch -> ch.isDigit() || ch == '.' }
            }
            PriceFieldRow("Extra Crisp Starch Fee", starchText) {
                starchText = it.filter { ch -> ch.isDigit() || ch == '.' }
            }
            PriceFieldRow("Doorstep Pickup & Delivery", deliveryText) {
                deliveryText = it.filter { ch -> ch.isDigit() || ch == '.' }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val updated = currentPricing.copy(
                        servicePrices = serviceRates.mapValues { it.value.toDoubleOrNull() ?: 0.0 },
                        fabricPrices = fabricRates.mapValues { it.value.toDoubleOrNull() ?: 0.0 },
                        roseWaterFee = roseWaterText.toDoubleOrNull() ?: currentPricing.roseWaterFee,
                        extraStarchFee = starchText.toDoubleOrNull() ?: currentPricing.extraStarchFee,
                        doorstepDeliveryFee = deliveryText.toDoubleOrNull() ?: currentPricing.doorstepDeliveryFee,
                    )
                    onSave(updated)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_pricing_button"),
            ) {
                Text("Save Live Catalog Rates")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PriceFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            prefix = { Text("₹") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
    }
}
