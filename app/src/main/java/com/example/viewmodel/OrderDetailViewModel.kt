package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LaundryRepository
import com.example.data.NotificationHelper
import com.example.data.unwrapFirestoreErrorMessage
import com.example.model.LaundryOrder
import com.example.model.OrderMessage
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

class OrderDetailViewModel(
    private val repository: LaundryRepository,
    private val notificationHelper: NotificationHelper,
    private val orderId: String,
    private val currentUserId: String,
    private val isAdmin: Boolean,
) : ViewModel() {

    private var lastKnownMessageCount = -1

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val orderState: StateFlow<UiState<LaundryOrder?>> = repository.observeOrder(orderId)
        .map<LaundryOrder?, UiState<LaundryOrder?>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreErrorMessage(error, "Failed to load order details.")))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    val messagesState: StateFlow<UiState<List<OrderMessage>>> = repository
        .observeOrderMessages(orderId = orderId, customerId = currentUserId, isAdmin = isAdmin)
        .onEach { messages ->
            if (lastKnownMessageCount >= 0 && messages.size > lastKnownMessageCount) {
                val latest = messages.lastOrNull()
                if (latest != null && latest.senderId != currentUserId) {
                    notificationHelper.postSystemNotification(
                        notificationId = ("chat_$orderId").hashCode(),
                        channelId = NotificationHelper.CHANNEL_ORDER_CHAT,
                        title = "New message from ${latest.senderName}",
                        body = latest.text,
                        orderId = orderId,
                    )
                }
            }
            lastKnownMessageCount = messages.size
        }
        .map<List<OrderMessage>, UiState<List<OrderMessage>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreErrorMessage(error, "Failed to load order chat.")))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    fun sendMessage(orderCustomerId: String, senderName: String, text: String, onSent: () -> Unit) {
        if (_isSendingMessage.value || text.isBlank()) return
        _actionError.value = null
        _isSendingMessage.value = true
        viewModelScope.launch {
            val result = repository.sendOrderMessage(
                orderId = orderId,
                orderCustomerId = orderCustomerId,
                senderName = senderName,
                isAdmin = isAdmin,
                text = text,
            )
            _isSendingMessage.value = false
            result.onSuccess { onSent() }
                .onFailure { _actionError.value = it.message ?: "Failed to send message." }
        }
    }

    fun updateStatus(newStatus: OrderStatus, rejectionReason: String = "") {
        viewModelScope.launch {
            val result = repository.updateOrderStatus(orderId, newStatus, rejectionReason)
            result.onFailure {
                _actionError.value = it.message ?: "Could not update order status."
            }
        }
    }

    fun clearError() {
        _actionError.value = null
    }
}
