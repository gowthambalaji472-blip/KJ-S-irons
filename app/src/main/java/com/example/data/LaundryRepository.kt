package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CatalogPricing
import com.example.model.DeliveryMethod
import com.example.model.LaundryOrder
import com.example.model.OrderMessage
import com.example.model.OrderStatus
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class LaundryRepository(
    private val dbProvider: () -> FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    private val db: FirebaseFirestore by lazy { dbProvider() }

    constructor(db: FirebaseFirestore, auth: FirebaseAuth = Firebase.auth) : this({ db }, auth)

    constructor(databaseId: String, auth: FirebaseAuth = Firebase.auth) : this(
        { FirebaseFirestore.getInstance(databaseId) },
        auth
    )

    constructor(context: Context) : this(
        context.applicationContext.getString(R.string.firestore_database_id)
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeCatalogPricing(): Flow<CatalogPricing> = flow {
        val uid = requireUserId()
        val docRef = db.collection("catalog").document("pricing")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            CatalogPricing::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: CatalogPricing.default()
                    } else {
                        CatalogPricing.default()
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureDefaultCatalogSeededByAdmin(): Result<Unit> {
        val path = "catalog/pricing"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("catalog").document("pricing")
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val defaults = CatalogPricing.default()
                docRef.set(
                    mapOf(
                        "servicePrices" to defaults.servicePrices,
                        "fabricPrices" to defaults.fabricPrices,
                        "dressTypePrices" to defaults.dressTypePrices,
                        "roseWaterFee" to defaults.roseWaterFee,
                        "extraStarchFee" to defaults.extraStarchFee,
                        "doorstepDeliveryFee" to defaults.doorstepDeliveryFee,
                        "updatedBy" to uid,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCatalogPricing(pricing: CatalogPricing): Result<Unit> {
        val path = "catalog/pricing"
        return try {
            val uid = requireUserId()
            db.collection("catalog").document("pricing").set(
                mapOf(
                    "servicePrices" to pricing.servicePrices.mapValues { it.value.coerceIn(0.0, 50000.0) },
                    "fabricPrices" to pricing.fabricPrices.mapValues { it.value.coerceIn(0.0, 50000.0) },
                    "dressTypePrices" to pricing.dressTypePrices.mapValues { it.value.coerceIn(0.0, 50000.0) },
                    "roseWaterFee" to pricing.roseWaterFee.coerceIn(0.0, 5000.0),
                    "extraStarchFee" to pricing.extraStarchFee.coerceIn(0.0, 5000.0),
                    "doorstepDeliveryFee" to pricing.doorstepDeliveryFee.coerceIn(0.0, 5000.0),
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(Exception(unwrapFirestoreErrorMessage(e, "Failed to update pricing."), e))
        }
    }

    fun observeCustomerOrders(customerId: String): Flow<List<LaundryOrder>> = flow {
        val uid = requireUserId()
        check(uid == customerId) { "Refusing cross-user order listener." }
        val path = "orders"
        emitAll(
            db.collection(path)
                .whereEqualTo("customerId", customerId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LaundryOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllOrdersForAdmin(): Flow<List<LaundryOrder>> = flow {
        val uid = requireUserId()
        val path = "orders"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LaundryOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeOrder(orderId: String): Flow<LaundryOrder?> = flow {
        val uid = requireUserId()
        require(orderId.isNotBlank()) { "Order ID cannot be blank" }
        val docRef = db.collection("orders").document(orderId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            LaundryOrder::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun createOrder(order: LaundryOrder): Result<String> {
        var docPath = "orders"
        return try {
            val uid = requireUserId()
            val qty = order.quantity.coerceIn(1L, 200L)
            val address = order.pickupAddress.trim()
            require(address.length in 3..300) { "Pickup address must be 3 to 300 characters." }
            require(order.customerName.trim().isNotEmpty()) { "Customer name is required." }
            require(order.customerMobile.trim().length >= 6) { "Valid mobile number is required." }

            val docRef = db.collection("orders").document()
            docPath = docRef.path

            val payload = mapOf(
                "customerId" to uid,
                "customerName" to order.customerName.trim().take(100),
                "customerMobile" to order.customerMobile.trim().take(24),
                "serviceType" to order.serviceType.take(80),
                "fabricType" to order.fabricType.take(80),
                "dressType" to order.dressType.take(80),
                "quantity" to qty,
                "roseWaterAdded" to order.roseWaterAdded,
                "extraStarchAdded" to order.extraStarchAdded,
                "deliveryMethod" to order.deliveryMethodEnum.raw,
                "pickupAddress" to address,
                "locationLabel" to order.locationLabel.trim().take(160),
                "latitude" to order.latitude.coerceIn(-90.0, 90.0),
                "longitude" to order.longitude.coerceIn(-180.0, 180.0),
                "notes" to order.notes.trim().take(300),
                "status" to OrderStatus.PENDING.raw,
                "rejectionReason" to "",
                "unitPrice" to order.unitPrice.coerceAtLeast(0.0),
                "addOnPrice" to order.addOnPrice.coerceAtLeast(0.0),
                "deliveryFee" to order.deliveryFee.coerceAtLeast(0.0),
                "totalPrice" to order.totalPrice.coerceAtLeast(0.0),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(Exception(unwrapFirestoreErrorMessage(e, "Failed to place order."), e))
        }
    }

    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        rejectionReason: String = "",
    ): Result<Unit> {
        val docPath = "orders/$orderId"
        return try {
            requireUserId()
            require(orderId.isNotBlank()) { "Order ID cannot be blank" }
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.raw,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (newStatus == OrderStatus.REJECTED) {
                updates["rejectionReason"] = rejectionReason.trim().take(300)
            }
            db.collection("orders").document(orderId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(Exception(unwrapFirestoreErrorMessage(e, "Failed to update order status."), e))
        }
    }

    fun observeOrderMessages(
        orderId: String,
        customerId: String,
        isAdmin: Boolean,
    ): Flow<List<OrderMessage>> = flow {
        val uid = requireUserId()
        val messagesRef = db.collection("orders").document(orderId).collection("messages")
        val query = if (isAdmin) {
            messagesRef
        } else {
            messagesRef.whereEqualTo("customerId", uid)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        OrderMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(
                            firestoreError ?: error,
                            OperationType.LIST,
                            "orders/$orderId/messages"
                        )
                    }
                    throw error
                }
        )
    }

    suspend fun sendOrderMessage(
        orderId: String,
        orderCustomerId: String,
        senderName: String,
        isAdmin: Boolean,
        text: String,
    ): Result<Unit> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Message cannot be empty."))
        if (trimmed.length > 1000) return Result.failure(IllegalArgumentException("Message is too long."))

        val docPath = "orders/$orderId/messages"
        return try {
            val uid = requireUserId()
            val msgRef = db.collection("orders").document(orderId).collection("messages").document()
            msgRef.set(
                mapOf(
                    "orderId" to orderId,
                    "customerId" to orderCustomerId,
                    "senderId" to uid,
                    "senderName" to senderName.trim().ifBlank { if (isAdmin) "KJ Iron's Admin" else "Customer" }.take(100),
                    "senderRole" to if (isAdmin) "admin" else "customer",
                    "text" to trimmed,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(Exception(unwrapFirestoreErrorMessage(e, "Failed to send message."), e))
        }
    }
}
