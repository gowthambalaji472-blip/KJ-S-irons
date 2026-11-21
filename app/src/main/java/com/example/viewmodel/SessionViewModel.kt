package com.example.viewmodel

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRoleRepository
import com.example.data.GoogleSignInResult
import com.example.data.RoleResolution
import com.example.model.AdminAccount
import com.example.model.CustomerProfile
import com.example.model.PortalChoice
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppSessionState {
    data object Checking : AppSessionState
    data object Unauthenticated : AppSessionState
    data class CustomerProfileRequired(
        val uid: String,
        val email: String,
        val suggestedName: String,
    ) : AppSessionState
    data class CustomerAuthenticated(val profile: CustomerProfile) : AppSessionState
    data class AdminAuthenticated(val admin: AdminAccount) : AppSessionState
}

class SessionViewModel(
    private val authRoleRepository: AuthRoleRepository,
) : ViewModel() {

    private val _sessionState = MutableStateFlow<AppSessionState>(AppSessionState.Checking)
    val sessionState: StateFlow<AppSessionState> = _sessionState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage: StateFlow<String?> = _authMessage.asStateFlow()

    private val _adminSlotsFull = MutableStateFlow(false)
    val adminSlotsFull: StateFlow<Boolean> = _adminSlotsFull.asStateFlow()

    fun bootstrapSession(context: Context) {
        if (_sessionState.value !is AppSessionState.Checking) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            val existingUser = Firebase.auth.currentUser
            if (existingUser == null) {
                // Render RoleGatewayScreen immediately on frame 1 so SurfaceSyncGroup never stalls
                _sessionState.value = AppSessionState.Unauthenticated
                kotlinx.coroutines.delay(650)
                if (_isBusy.value || _sessionState.value !is AppSessionState.Unauthenticated) return@launch
                val signedInSilently = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val credentialManager = CredentialManager.create(appContext)
                    authRoleRepository.attemptSilentGoogleSignIn(appContext, credentialManager)
                }
                if (!signedInSilently) {
                    _adminSlotsFull.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        authRoleRepository.isAdminSlotsFull()
                    }
                    return@launch
                }
            }

            val (slotsFull, detected) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                authRoleRepository.isAdminSlotsFull() to authRoleRepository.detectExistingRole()
            }
            _adminSlotsFull.value = slotsFull
            when (detected) {
                is RoleResolution.AdminReady -> {
                    _sessionState.value = AppSessionState.AdminAuthenticated(detected.admin)
                }
                is RoleResolution.CustomerReady -> {
                    _sessionState.value = AppSessionState.CustomerAuthenticated(detected.profile)
                }
                else -> {
                    _sessionState.value = AppSessionState.Unauthenticated
                }
            }
        }
    }

    fun signInWithPortal(activity: Activity, portal: PortalChoice) {
        if (_isBusy.value) return
        _authMessage.value = null
        _isBusy.value = true
        val credentialManager = CredentialManager.create(activity)
        viewModelScope.launch {
            try {
                // Always verify or trigger interactive Google Sign-In
                if (Firebase.auth.currentUser == null) {
                    when (val result = authRoleRepository.interactiveGoogleSignIn(activity, credentialManager)) {
                        GoogleSignInResult.Success -> Unit
                        GoogleSignInResult.Cancelled -> {
                            _isBusy.value = false
                            return@launch
                        }
                        is GoogleSignInResult.Error -> {
                            _authMessage.value = result.message
                            _isBusy.value = false
                            return@launch
                        }
                    }
                }

                when (val resolution = authRoleRepository.resolveRoleForPortal(portal)) {
                    is RoleResolution.AdminReady -> {
                        _adminSlotsFull.value = authRoleRepository.isAdminSlotsFull()
                        _sessionState.value = AppSessionState.AdminAuthenticated(resolution.admin)
                    }
                    is RoleResolution.CustomerReady -> {
                        _sessionState.value = AppSessionState.CustomerAuthenticated(resolution.profile)
                    }
                    is RoleResolution.CustomerNeedsProfile -> {
                        _sessionState.value = AppSessionState.CustomerProfileRequired(
                            uid = resolution.uid,
                            email = resolution.email,
                            suggestedName = resolution.suggestedName,
                        )
                    }
                    is RoleResolution.RoleConflict -> {
                        // Sign out so user can pick a different Google account for the requested role
                        authRoleRepository.signOut(credentialManager)
                        _authMessage.value = resolution.message
                        _sessionState.value = AppSessionState.Unauthenticated
                    }
                    is RoleResolution.AdminLimitReached -> {
                        authRoleRepository.signOut(credentialManager)
                        _adminSlotsFull.value = true
                        _authMessage.value = resolution.message
                        _sessionState.value = AppSessionState.Unauthenticated
                    }
                    is RoleResolution.Failure -> {
                        _authMessage.value = resolution.message
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun saveCustomerProfile(
        name: String,
        mobile: String,
        address: String,
        locationLabel: String,
        latitude: Double,
        longitude: Double,
        photoBase64: String = "",
        isUpdate: Boolean,
        onComplete: () -> Unit = {},
    ) {
        if (_isBusy.value) return
        _authMessage.value = null
        _isBusy.value = true
        viewModelScope.launch {
            val result = authRoleRepository.saveCustomerProfile(
                name = name,
                mobile = mobile,
                address = address,
                locationLabel = locationLabel,
                latitude = latitude,
                longitude = longitude,
                photoBase64 = photoBase64,
                isUpdate = isUpdate,
            )
            _isBusy.value = false
            result.onSuccess { profile ->
                _sessionState.value = AppSessionState.CustomerAuthenticated(profile)
                onComplete()
            }.onFailure { err ->
                _authMessage.value = err.message ?: "Failed to save profile."
            }
        }
    }

    fun clearAuthMessage() {
        _authMessage.value = null
    }

    fun signOut(context: Context) {
        val credentialManager = CredentialManager.create(context)
        viewModelScope.launch {
            authRoleRepository.signOut(credentialManager)
            _authMessage.value = null
            _sessionState.value = AppSessionState.Unauthenticated
        }
    }
}
