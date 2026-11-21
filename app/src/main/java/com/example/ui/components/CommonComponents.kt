package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.InAppNotification
import com.example.model.LaundryOrder
import com.example.model.OrderStatus
import com.example.ui.theme.StatusAcceptedBg
import com.example.ui.theme.StatusAcceptedFg
import com.example.ui.theme.StatusDeliveredBg
import com.example.ui.theme.StatusDeliveredFg
import com.example.ui.theme.StatusDeliveryBg
import com.example.ui.theme.StatusDeliveryFg
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingFg
import com.example.ui.theme.StatusProcessingBg
import com.example.ui.theme.StatusProcessingFg
import com.example.ui.theme.StatusReadyBg
import com.example.ui.theme.StatusReadyFg
import com.example.ui.theme.StatusRejectedBg
import com.example.ui.theme.StatusRejectedFg
import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.maximumFractionDigits = 0
    return formatter.format(amount)
}

fun formatTimestamp(ts: Timestamp?): String {
    val date = (ts ?: Timestamp.now()).toDate()
    return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(date)
}

@Composable
fun ProfileAvatar(
    photoBase64: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val bitmap = remember(photoBase64) {
        if (photoBase64.isBlank()) {
            null
        } else {
            try {
                val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    val initials = remember(name) {
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (initials.isNotBlank()) {
            Text(
                text = initials,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).coerceIn(11f, 32f).sp,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.52f),
            )
        }
    }
}

@Composable
fun statusColors(status: OrderStatus): Pair<Color, Color> {
    val targetBg = when (status) {
        OrderStatus.PENDING -> StatusPendingBg
        OrderStatus.ACCEPTED -> StatusAcceptedBg
        OrderStatus.PROCESSING -> StatusProcessingBg
        OrderStatus.READY -> StatusReadyBg
        OrderStatus.OUT_FOR_DELIVERY -> StatusDeliveryBg
        OrderStatus.DELIVERED -> StatusDeliveredBg
        OrderStatus.REJECTED -> StatusRejectedBg
    }
    val targetFg = when (status) {
        OrderStatus.PENDING -> StatusPendingFg
        OrderStatus.ACCEPTED -> StatusAcceptedFg
        OrderStatus.PROCESSING -> StatusProcessingFg
        OrderStatus.READY -> StatusReadyFg
        OrderStatus.OUT_FOR_DELIVERY -> StatusDeliveryFg
        OrderStatus.DELIVERED -> StatusDeliveredFg
        OrderStatus.REJECTED -> StatusRejectedFg
    }
    val bg by animateColorAsState(targetBg, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "statusBg")
    val fg by animateColorAsState(targetFg, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "statusFg")
    return bg to fg
}

@Composable
fun OrderStatusBadge(
    status: OrderStatus,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = statusColors(status)
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier,
    ) {
        Text(
            text = status.label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun OrderStepTimeline(
    currentStatus: OrderStatus,
    rejectionReason: String,
    modifier: Modifier = Modifier,
) {
    if (currentStatus == OrderStatus.REJECTED) {
        Surface(
            color = StatusRejectedBg,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, StatusRejectedFg.copy(alpha = 0.25f)),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Order Declined",
                    style = MaterialTheme.typography.titleSmall,
                    color = StatusRejectedFg,
                )
                if (rejectionReason.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = rejectionReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusRejectedFg.copy(alpha = 0.9f),
                    )
                }
            }
        }
        return
    }

    val steps = listOf(
        OrderStatus.PENDING to "Pending",
        OrderStatus.ACCEPTED to "Accepted",
        OrderStatus.PROCESSING to "Processing",
        OrderStatus.READY to "Ready",
        OrderStatus.OUT_FOR_DELIVERY to "Delivery",
        OrderStatus.DELIVERED to "Delivered",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, (stepStatus, shortLabel) ->
            val reached = currentStatus.stepIndex >= stepStatus.stepIndex
            val isCurrent = currentStatus == stepStatus
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val dotColor by animateColorAsState(
                        targetValue = when {
                            reached -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        },
                        label = "stepDot",
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (reached) dotColor else MaterialTheme.colorScheme.surfaceVariant
                            ),
                    ) {
                        if (reached) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                    if (index < steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(24.dp)
                                .background(
                                    if (currentStatus.stepIndex > stepStatus.stepIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }
                                )
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = stepStatus.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (reached) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    )
                    if (isCurrent) {
                        Text(
                            text = when (stepStatus) {
                                OrderStatus.PENDING -> "Waiting for KJ IRON'S admin confirmation"
                                OrderStatus.ACCEPTED -> "Order confirmed and queued for garment care"
                                OrderStatus.PROCESSING -> "Washing, starching, and steam ironing in progress"
                                OrderStatus.READY -> "Freshly finished & ready! Tap below for delivery/pickup"
                                OrderStatus.OUT_FOR_DELIVERY -> "Rider is en route to your location"
                                OrderStatus.DELIVERED -> "Order completed and delivered"
                                OrderStatus.REJECTED -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = shortLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InAppNotificationBanner(
    notification: InAppNotification?,
    onOpenOrder: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        notification?.let { item ->
            Surface(
                color = if (item.isReadyHighlight) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable {
                        onOpenOrder(item.orderId)
                        onDismiss()
                    }
                    .testTag("in_app_notification_banner"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = if (item.isReadyHighlight) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (item.isReadyHighlight) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                        Text(
                            text = item.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isReadyHighlight) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dismiss_banner_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss notification",
                            tint = if (item.isReadyHighlight) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationBellButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.testTag("notification_bell_button"),
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(unreadCount.coerceAtMost(99).toString())
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "Order notifications",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsBottomSheet(
    notifications: List<InAppNotification>,
    onDismiss: () -> Unit,
    onSelectOrder: (String) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Real-Time Alerts",
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (notifications.isNotEmpty()) {
                    TextButton(onClick = onMarkAllRead) {
                        Text("Mark all read")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (notifications.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No new alerts yet",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Every status change and Ready alert appears here in real time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                notifications.forEach { alert ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (alert.isReadyHighlight) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                onDismiss()
                                onSelectOrder(alert.orderId)
                            },
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = alert.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = alert.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun OrderSummaryCard(
    order: LaundryOrder,
    showCustomerInfo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("order_card_${order.id}"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (showCustomerInfo) {
                            "${order.customerName} • #${order.id.takeLast(5).uppercase()}"
                        } else {
                            "Order #${order.id.takeLast(5).uppercase()}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatTimestamp(order.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                OrderStatusBadge(status = order.orderStatus)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${order.quantity}x ${order.dressType} • ${order.fabricType}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${order.serviceType} • ${order.deliveryMethodEnum.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (order.roseWaterAdded || order.extraStarchAdded) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (order.roseWaterAdded) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(999.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocalFlorist,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Rose Water",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                }
                            }
                            if (order.extraStarchAdded) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(999.dp),
                                ) {
                                    Text(
                                        text = "Extra Starch",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatCurrency(order.totalPrice),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
