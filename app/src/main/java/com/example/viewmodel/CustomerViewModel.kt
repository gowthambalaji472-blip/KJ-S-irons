package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LaundryRepository
import com.example.data.NotificationHelper
import com.example.data.unwrapFirestoreErrorMessage
import com.example.model.CatalogPricing
import com.example.model.CustomerProfile
import com.example.model.DeliveryMethod
import com.example.model.InAppNotification
import com.example.model.LaundryOrder
import com.example.model.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustomerViewModel(
    private val repository: LaundryRepository,
    private val notificationHelper: NotificationHelper,
    private val customerId: String,
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<InAppNotification>>(emptyList())
    val notifications: StateFlow<List<InAppNotification>> = _notifications.asStateFlow()

    private val _activeBanner = MutableStateFlow<InAppNotification?>(null)
    val activeBanner: StateFlow<InAppNotification?> = _activeBanner.asStateFlow()

    private val _isSubmittingOrder = MutableStateFlow(false)
    val isSubmittingOrder: StateFlow<Boolean> = _isSubmittingOrder.asStateFlow()

    private val _submitError = MutableStateFlow<String?>(null)
    val submitError: StateFlow<String?> = _submitError.asStateFlow()

    val catalogPricing: StateFlow<CatalogPricing> = repository.observeCatalogPricing()
        .catch { emit(CatalogPricing.default()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = CatalogPricing.default(),
        )

    val ordersState: StateFlow<UiState<List<LaundryOrder>>> = repository.observeCustomerOrders(customerId)
        .onEach { orders ->
            val newAlerts = notificationHelper.detectAndNotifyOrderChanges(
                userId = customerId,
                isAdmin = false,
                orders = orders,
            )
            if (newAlerts.isNotEmpty()) {
                _notifications.value = (newAlerts + _notifications.value).take(30)
                _activeBanner.value = newAlerts.firstOrNull { it.isReadyHighlight } ?: newAlerts.first()
            }
        }
        .map<List<LaundryOrder>, UiState<List<LaundryOrder>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreErrorMessage(error, "Failed to load your orders.")))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    fun placeOrder(
        profile: CustomerProfile,
        serviceType: String,
        fabricType: String,
        dressType: String,
        quantity: Int,
        roseWaterAdded: Boolean,
        extraStarchAdded: Boolean,
        deliveryMethod: DeliveryMethod,
        pickupAddress: String,
        locationLabel: String,
        latitude: Double,
        longitude: Double,
        notes: String,
        pricing: CatalogPricing,
        onOrderCreated: (String) -> Unit,
    ) {
        if (_isSubmittingOrder.value) return
        _submitError.value = null
        _isSubmittingOrder.value = true

        val serviceRate = pricing.servicePrices[serviceType] ?: 30.0
        val fabricRate = pricing.fabricPrices[fabricType] ?: 0.0
        val dressRate = pricing.dressTypePrices[dressType] ?: 0.0
        val unitPrice = serviceRate + fabricRate + dressRate
        val addOnPrice =
            (if (roseWaterAdded) pricing.roseWaterFee else 0.0) +
                (if (extraStarchAdded) pricing.extraStarchFee else 0.0)
        val deliveryFee =
            if (deliveryMethod == DeliveryMethod.DOORSTEP) pricing.doorstepDeliveryFee else 0.0
        val totalPrice = (unitPrice * quantity) + addOnPrice + deliveryFee

        val order = LaundryOrder(
            customerId = customerId,
            customerName = profile.name,
            customerMobile = profile.mobile,
            serviceType = serviceType,
            fabricType = fabricType,
            dressType = dressType,
            quantity = quantity.toLong(),
            roseWaterAdded = roseWaterAdded,
            extraStarchAdded = extraStarchAdded,
            deliveryMethod = deliveryMethod.raw,
            pickupAddress = pickupAddress,
            locationLabel = locationLabel,
            latitude = latitude,
            longitude = longitude,
            notes = notes,
            unitPrice = unitPrice,
            addOnPrice = addOnPrice,
            deliveryFee = deliveryFee,
            totalPrice = totalPrice,
        )

        viewModelScope.launch {
            val result = repository.createOrder(order)
            _isSubmittingOrder.value = false
            result.onSuccess { orderId ->
                onOrderCreated(orderId)
            }.onFailure { err ->
                _submitError.value = err.message ?: "Could not confirm order."
            }
        }
    }

    fun dismissBanner() {
        _activeBanner.value = null
    }

    fun markAllRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }

    fun clearSubmitError() {
        _submitError.value = null
    }
}
