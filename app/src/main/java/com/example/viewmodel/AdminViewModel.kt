package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LaundryRepository
import com.example.data.NotificationHelper
import com.example.data.unwrapFirestoreErrorMessage
import com.example.model.CatalogPricing
import com.example.model.InAppNotification
import com.example.model.LaundryOrder
import com.example.model.OrderStatus
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

class AdminViewModel(
    private val repository: LaundryRepository,
    private val notificationHelper: NotificationHelper,
    private val adminId: String,
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<InAppNotification>>(emptyList())
    val notifications: StateFlow<List<InAppNotification>> = _notifications.asStateFlow()

    private val _activeBanner = MutableStateFlow<InAppNotification?>(null)
    val activeBanner: StateFlow<InAppNotification?> = _activeBanner.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _updatingOrderId = MutableStateFlow<String?>(null)
    val updatingOrderId: StateFlow<String?> = _updatingOrderId.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultCatalogSeededByAdmin()
        }
    }

    val catalogPricing: StateFlow<CatalogPricing> = repository.observeCatalogPricing()
        .catch { emit(CatalogPricing.default()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = CatalogPricing.default(),
        )

    val allOrdersState: StateFlow<UiState<List<LaundryOrder>>> = repository.observeAllOrdersForAdmin()
        .onEach { orders ->
            val newAlerts = notificationHelper.detectAndNotifyOrderChanges(
                userId = adminId,
                isAdmin = true,
                orders = orders,
            )
            if (newAlerts.isNotEmpty()) {
                _notifications.value = (newAlerts + _notifications.value).take(40)
                _activeBanner.value = newAlerts.first()
            }
        }
        .map<List<LaundryOrder>, UiState<List<LaundryOrder>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreErrorMessage(error, "Failed to load central order queue.")))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        rejectionReason: String = "",
    ) {
        if (_updatingOrderId.value != null) return
        _updatingOrderId.value = orderId
        viewModelScope.launch {
            val result = repository.updateOrderStatus(orderId, newStatus, rejectionReason)
            _updatingOrderId.value = null
            result.onSuccess {
                _actionMessage.value = "Order moved to ${newStatus.label}."
            }.onFailure { err ->
                _actionMessage.value = err.message ?: "Failed to update status."
            }
        }
    }

    fun saveCatalogPricing(pricing: CatalogPricing, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.updateCatalogPricing(pricing)
            result.onSuccess {
                _actionMessage.value = "Catalog pricing updated for all customers."
                onSaved()
            }.onFailure { err ->
                _actionMessage.value = err.message ?: "Failed to save catalog pricing."
            }
        }
    }

    fun dismissBanner() {
        _activeBanner.value = null
    }

    fun markAllRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
