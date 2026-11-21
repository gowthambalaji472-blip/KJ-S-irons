package com.example.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.MainActivity
import com.example.R
import com.example.model.InAppNotification
import com.example.model.LaundryOrder
import com.example.model.OrderStatus
import kotlinx.coroutines.flow.first

private val Context.notificationDataStore by preferencesDataStore(name = "kj_irons_notifications")

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ORDER_UPDATES = "kj_order_updates"
        const val CHANNEL_ORDER_CHAT = "kj_order_chat"
        private const val EXTRA_ORDER_ID = "extra_order_id"
    }

    init {
        ensureChannels()
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val orderChannel = NotificationChannel(
                CHANNEL_ORDER_UPDATES,
                "Order Status Updates",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Real-time notifications when orders are placed, accepted, processing, ready, or delivered."
            }
            val chatChannel = NotificationChannel(
                CHANNEL_ORDER_CHAT,
                "Order Messages",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Messages from KJ IRON'S staff or customers on active orders."
            }
            manager.createNotificationChannel(orderChannel)
            manager.createNotificationChannel(chatChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun postSystemNotification(
        notificationId: Int,
        channelId: String,
        title: String,
        body: String,
        orderId: String,
    ) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ORDER_ID, orderId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }

    suspend fun detectAndNotifyOrderChanges(
        userId: String,
        isAdmin: Boolean,
        orders: List<LaundryOrder>,
    ): List<InAppNotification> {
        val prefs = context.notificationDataStore.data.first()
        val initializedKey = stringPreferencesKey("initialized_${userId}_${if (isAdmin) "admin" else "cust"}")
        val isInitialized = prefs[initializedKey] == "true"
        val emitted = mutableListOf<InAppNotification>()
        val updates = mutableMapOf<String, String>()

        if (!isInitialized) {
            updates[initializedKey.name] = "true"
            orders.forEach { order ->
                updates["status_${userId}_${order.id}"] = order.status
            }
            context.notificationDataStore.edit { mutablePrefs ->
                updates.forEach { (k, v) -> mutablePrefs[stringPreferencesKey(k)] = v }
            }
            return emptyList()
        }

        orders.forEach { order ->
            val key = stringPreferencesKey("status_${userId}_${order.id}")
            val previousStatus = prefs[key]
            val currentStatus = order.status
            if (previousStatus == null) {
                updates[key.name] = currentStatus
                if (isAdmin && order.orderStatus == OrderStatus.PENDING) {
                    val title = "New Laundry Order"
                    val body = "${order.customerName} placed ${order.quantity}x ${order.fabricType} (${order.serviceType})."
                    emitted += InAppNotification(
                        id = "${order.id}_new_${System.currentTimeMillis()}",
                        orderId = order.id,
                        title = title,
                        message = body,
                        timestampMillis = System.currentTimeMillis(),
                        isReadyHighlight = false,
                    )
                    postSystemNotification(
                        notificationId = order.id.hashCode(),
                        channelId = CHANNEL_ORDER_UPDATES,
                        title = title,
                        body = body,
                        orderId = order.id,
                    )
                }
            } else if (previousStatus != currentStatus) {
                updates[key.name] = currentStatus
                val statusEnum = order.orderStatus
                val (title, body) = if (isAdmin) {
                    "Order #${order.id.takeLast(5).uppercase()} Updated" to
                        "${order.customerName}'s order is now ${statusEnum.label}."
                } else {
                    when (statusEnum) {
                        OrderStatus.ACCEPTED -> "Order Accepted" to
                            "KJ IRON'S accepted your ${order.fabricType} order."
                        OrderStatus.REJECTED -> "Order Declined" to
                            (order.rejectionReason.ifBlank { "Your order could not be fulfilled right now." })
                        OrderStatus.PROCESSING -> "Garment Care In Progress" to
                            "Your ${order.quantity}x ${order.dressType} (${order.fabricType}) is now being processed."
                        OrderStatus.READY -> "Order Ready!" to
                            "Fresh & crisp! Your ${order.fabricType} order is ready for ${order.deliveryMethodEnum.label}."
                        OrderStatus.OUT_FOR_DELIVERY -> "Out for Delivery" to
                            "Our rider is on the way to ${order.locationLabel.ifBlank { "your address" }}."
                        OrderStatus.DELIVERED -> "Order Delivered" to
                            "Your clothes have been delivered. Thank you for choosing KJ IRON'S!"
                        OrderStatus.PENDING -> "Order Placed" to
                            "Your order is awaiting shop confirmation."
                    }
                }
                emitted += InAppNotification(
                    id = "${order.id}_${currentStatus}_${System.currentTimeMillis()}",
                    orderId = order.id,
                    title = title,
                    message = body,
                    timestampMillis = System.currentTimeMillis(),
                    isReadyHighlight = statusEnum == OrderStatus.READY,
                )
                postSystemNotification(
                    notificationId = (order.id + currentStatus).hashCode(),
                    channelId = CHANNEL_ORDER_UPDATES,
                    title = title,
                    body = body,
                    orderId = order.id,
                )
            }
        }

        if (updates.isNotEmpty()) {
            context.notificationDataStore.edit { mutablePrefs ->
                updates.forEach { (k, v) -> mutablePrefs[stringPreferencesKey(k)] = v }
            }
        }
        return emitted
    }
}
