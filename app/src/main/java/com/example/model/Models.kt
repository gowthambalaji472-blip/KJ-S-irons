package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PortalChoice {
    CUSTOMER,
    ADMIN,
}

enum class OrderStatus(val raw: String, val label: String, val stepIndex: Int) {
    PENDING("pending", "Pending Approval", 0),
    ACCEPTED("accepted", "Accepted", 1),
    PROCESSING("processing", "Processing", 2),
    READY("ready", "Ready", 3),
    OUT_FOR_DELIVERY("out_for_delivery", "Out for Delivery", 4),
    DELIVERED("delivered", "Delivered", 5),
    REJECTED("rejected", "Rejected", -1);

    fun nextAdminStatus(): OrderStatus? = when (this) {
        PENDING -> ACCEPTED
        ACCEPTED -> PROCESSING
        PROCESSING -> READY
        READY -> OUT_FOR_DELIVERY
        OUT_FOR_DELIVERY -> DELIVERED
        DELIVERED, REJECTED -> null
    }

    fun nextAdminActionLabel(): String? = when (this) {
        PENDING -> "Accept Order"
        ACCEPTED -> "Start Processing"
        PROCESSING -> "Mark Ready"
        READY -> "Send for Delivery"
        OUT_FOR_DELIVERY -> "Mark Delivered"
        DELIVERED, REJECTED -> null
    }

    val isTerminal: Boolean
        get() = this == DELIVERED || this == REJECTED

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.raw == raw } ?: PENDING
    }
}

enum class DeliveryMethod(val raw: String, val label: String, val subtitle: String) {
    DOORSTEP(
        raw = "doorstep_pickup_delivery",
        label = "Doorstep Pickup & Delivery",
        subtitle = "Rider picks up & returns to your pinned map location",
    ),
    SELF_DROP(
        raw = "self_dropoff_pickup",
        label = "Self Drop-off & Store Pickup",
        subtitle = "Drop off and collect directly at KJ IRON'S studio",
    );

    companion object {
        fun fromRaw(raw: String?): DeliveryMethod =
            entries.firstOrNull { it.raw == raw } ?: DOORSTEP
    }
}

@Keep
@IgnoreExtraProperties
data class AdminRegistry(
    @DocumentId val id: String = "",
    val adminUids: List<String> = emptyList(),
    val maxAdmins: Long = 2L,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AdminAccount(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val slotNumber: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CustomerProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val role: String = "customer",
    val name: String = "",
    val email: String = "",
    val mobile: String = "",
    val address: String = "",
    val locationLabel: String = "",
    val latitude: Double = 12.9716,
    val longitude: Double = 77.5946,
    val photoBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CatalogPricing(
    @DocumentId val id: String = "",
    val servicePrices: Map<String, Double> = defaultServices(),
    val fabricPrices: Map<String, Double> = defaultFabrics(),
    val dressTypePrices: Map<String, Double> = defaultDressTypes(),
    val roseWaterFee: Double = 15.0,
    val extraStarchFee: Double = 20.0,
    val doorstepDeliveryFee: Double = 25.0,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        fun defaultServices(): Map<String, Double> = linkedMapOf(
            "Steam Ironing" to 25.0,
            "Wash & Fold" to 45.0,
            "Wash & Steam Iron" to 65.0,
            "Dry Cleaning" to 120.0,
            "Premium Silk Care" to 150.0,
        )

        fun defaultFabrics(): Map<String, Double> = linkedMapOf(
            "Cotton" to 0.0,
            "Starched Shirt" to 20.0,
            "Sarees" to 55.0,
            "Silk" to 40.0,
            "Linen" to 25.0,
            "Wool" to 35.0,
            "Synthetic / Blend" to 5.0,
            "Delicate / Embroidered" to 50.0,
        )

        fun defaultDressTypes(): Map<String, Double> = linkedMapOf(
            "Shirt / Kurta" to 10.0,
            "Trousers / Jeans" to 15.0,
            "Saree" to 35.0,
            "Suit / Blazer" to 60.0,
            "Dress / Gown" to 40.0,
            "Bedsheet / Curtain" to 30.0,
        )

        fun default(): CatalogPricing = CatalogPricing(
            id = "pricing",
            servicePrices = defaultServices(),
            fabricPrices = defaultFabrics(),
            dressTypePrices = defaultDressTypes(),
            roseWaterFee = 15.0,
            extraStarchFee = 20.0,
            doorstepDeliveryFee = 25.0,
        )
    }
}

@Keep
@IgnoreExtraProperties
data class LaundryOrder(
    @DocumentId val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerMobile: String = "",
    val serviceType: String = "",
    val fabricType: String = "",
    val dressType: String = "",
    val quantity: Long = 1L,
    val roseWaterAdded: Boolean = false,
    val extraStarchAdded: Boolean = false,
    val deliveryMethod: String = DeliveryMethod.DOORSTEP.raw,
    val pickupAddress: String = "",
    val locationLabel: String = "",
    val latitude: Double = 12.9716,
    val longitude: Double = 77.5946,
    val notes: String = "",
    val status: String = OrderStatus.PENDING.raw,
    val rejectionReason: String = "",
    val unitPrice: Double = 0.0,
    val addOnPrice: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val totalPrice: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val orderStatus: OrderStatus
        get() = OrderStatus.fromRaw(status)

    val deliveryMethodEnum: DeliveryMethod
        get() = DeliveryMethod.fromRaw(deliveryMethod)
}

@Keep
@IgnoreExtraProperties
data class OrderMessage(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val customerId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "customer",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

data class InAppNotification(
    val id: String,
    val orderId: String,
    val title: String,
    val message: String,
    val timestampMillis: Long,
    val isReadyHighlight: Boolean = false,
    val isRead: Boolean = false,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
