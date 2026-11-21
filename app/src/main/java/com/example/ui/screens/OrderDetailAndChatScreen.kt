package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.OrderMessage
import com.example.model.OrderStatus
import com.example.model.UiState
import com.example.ui.components.OrderStatusBadge
import com.example.ui.components.OrderStepTimeline
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatTimestamp
import com.example.viewmodel.OrderDetailViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailAndChatScreen(
    orderId: String,
    currentUserId: String,
    senderName: String,
    isAdmin: Boolean,
    viewModel: OrderDetailViewModel,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val orderState by viewModel.orderState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val isSending by viewModel.isSendingMessage.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    var chatInput by remember { mutableStateOf("") }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }

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
                            text = "Order #${orderId.takeLast(5).uppercase()}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = if (isAdmin) "Admin Fulfillment & Chat" else "Live Order Tracking & Chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        bottomBar = {
            val loadedOrder = (orderState as? UiState.Success)?.data
            if (loadedOrder != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.imePadding(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { if (it.length <= 1000) chatInput = it },
                            placeholder = {
                                Text(
                                    if (isAdmin) "Message ${loadedOrder.customerName}..."
                                    else "Chat with KJ IRON'S about this order..."
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("order_chat_input"),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val textToSend = chatInput
                                viewModel.sendMessage(
                                    orderCustomerId = loadedOrder.customerId,
                                    senderName = senderName,
                                    text = textToSend,
                                    onSent = { chatInput = "" },
                                )
                            },
                            enabled = chatInput.isNotBlank() && !isSending,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_chat_button"),
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.cd_send_message),
                                    tint = if (chatInput.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
            when (val state = orderState) {
                UiState.Loading -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }

                is UiState.Success -> {
                    val order = state.data
                    if (order == null) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("Order not found.")
                        }
                    } else {
                        val messages = (messagesState as? UiState.Success)?.data.orEmpty()
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxSize(),
                        ) {
                            if (actionError != null) {
                                item(key = "action_error") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = actionError.orEmpty(),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp),
                                        )
                                    }
                                }
                            }

                            if (!isAdmin && order.orderStatus == OrderStatus.READY) {
                                item(key = "ready_banner") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.large,
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(14.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = "Your clothes are freshly pressed & Ready! Use the chat below for delivery coordination.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }
                            }

                            // Step-by-Step Live Status Card
                            item(key = "status_timeline") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("Live Order Progress", style = MaterialTheme.typography.titleMedium)
                                            OrderStatusBadge(status = order.orderStatus)
                                        }
                                        Spacer(Modifier.height(14.dp))
                                        AnimatedContent(targetState = order.orderStatus, label = "timelineAnim") { liveStatus ->
                                            OrderStepTimeline(
                                                currentStatus = liveStatus,
                                                rejectionReason = order.rejectionReason,
                                            )
                                        }

                                        if (isAdmin && !order.orderStatus.isTerminal) {
                                            Spacer(Modifier.height(16.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                            Spacer(Modifier.height(14.dp))
                                            if (order.orderStatus == OrderStatus.PENDING) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { showRejectDialog = true },
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            contentColor = MaterialTheme.colorScheme.error
                                                        ),
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(48.dp)
                                                            .testTag("detail_reject_button"),
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("Reject")
                                                    }
                                                    Button(
                                                        onClick = { viewModel.updateStatus(OrderStatus.ACCEPTED) },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(48.dp)
                                                            .testTag("detail_accept_button"),
                                                    ) {
                                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("Accept")
                                                    }
                                                }
                                            } else {
                                                val next = order.orderStatus.nextAdminStatus()
                                                val label = order.orderStatus.nextAdminActionLabel()
                                                if (next != null && label != null) {
                                                    Button(
                                                        onClick = { viewModel.updateStatus(next) },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(48.dp)
                                                            .testTag("detail_advance_button"),
                                                    ) {
                                                        Text(label)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Garment & Billing Summary
                            item(key = "garment_summary") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(18.dp),
                                    ) {
                                        Text("Order & Billing Summary", style = MaterialTheme.typography.titleMedium)
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        DetailLine("Customer", "${order.customerName} (${order.customerMobile})")
                                        DetailLine("Service", order.serviceType)
                                        DetailLine("Fabric", order.fabricType)
                                        DetailLine("Dress Type", order.dressType)
                                        DetailLine("Quantity", "${order.quantity} item(s)")
                                        if (order.roseWaterAdded) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.LocalFlorist,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "Rose Water Fragrance Rinse Included",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                        if (order.extraStarchAdded) {
                                            Text(
                                                text = "Extra Crisp Starch Included",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        DetailLine("Delivery", order.deliveryMethodEnum.label)
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Column {
                                                Text(order.pickupAddress, style = MaterialTheme.typography.bodySmall)
                                                Text(
                                                    text = String.format(
                                                        Locale.US,
                                                        "%s (%.4f, %.4f)",
                                                        order.locationLabel.ifBlank { "Pinned" },
                                                        order.latitude,
                                                        order.longitude,
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        if (order.notes.isNotBlank()) {
                                            DetailLine("Care Notes", order.notes)
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("Total Bill", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = formatCurrency(order.totalPrice),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }

                            // Order-Linked Chat Header
                            item(key = "chat_header") {
                                Text(
                                    text = "Order-Linked Chat",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }

                            if (messages.isEmpty()) {
                                item(key = "chat_empty") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = "No messages yet. Ask about pickup timing, rose water finish, or delivery below.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(14.dp),
                                        )
                                    }
                                }
                            } else {
                                items(messages, key = { it.id }) { msg ->
                                    ChatMessageBubble(
                                        message = msg,
                                        isMine = msg.senderId == currentUserId,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Decline Order") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { if (it.length <= 300) rejectReason = it },
                    label = { Text("Reason (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showRejectDialog = false
                        viewModel.updateStatus(
                            newStatus = OrderStatus.REJECTED,
                            rejectionReason = rejectReason.ifBlank { "Declined by shop admin" },
                        )
                    },
                ) {
                    Text("Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChatMessageBubble(
    message: OrderMessage,
    isMine: Boolean,
) {
    Row(
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            color = if (isMine) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp,
            ),
            border = if (isMine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = "${message.senderName} • ${if (message.senderRole == "admin") "Admin" else "Customer"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}
