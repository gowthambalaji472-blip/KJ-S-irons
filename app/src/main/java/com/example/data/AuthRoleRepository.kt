package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.model.AdminAccount
import com.example.model.AdminRegistry
import com.example.model.CustomerProfile
import com.example.model.PortalChoice
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface RoleResolution {
    data class AdminReady(val admin: AdminAccount) : RoleResolution
    data class CustomerReady(val profile: CustomerProfile) : RoleResolution
    data class CustomerNeedsProfile(
        val uid: String,
        val email: String,
        val suggestedName: String,
    ) : RoleResolution
    data class RoleConflict(val message: String) : RoleResolution
    data class AdminLimitReached(val message: String) : RoleResolution
    data class Failure(val message: String) : RoleResolution
}

class AuthRoleRepository(
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

    suspend fun attemptSilentGoogleSignIn(
        context: Context,
        credentialManager: CredentialManager,
    ): Boolean {
        if (auth.currentUser != null) return true
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return false
        }
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun interactiveGoogleSignIn(
        activity: Activity,
        credentialManager: CredentialManager,
    ): GoogleSignInResult {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return GoogleSignInResult.Error(
                "Google Sign-In configuration missing: default_web_client_id not found"
            )
        }
        return try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                GoogleSignInResult.Success
            } else {
                GoogleSignInResult.Error("Unexpected credential type received.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            GoogleSignInResult.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            GoogleSignInResult.Error(e.localizedMessage ?: "Google Sign-In failed.")
        }
    }

    suspend fun signOut(credentialManager: CredentialManager) {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    suspend fun detectExistingRole(): RoleResolution? {
        val user = auth.currentUser ?: return null
        val uid = user.uid
        return try {
            val adminSnap = db.collection("admins").document(uid).get().await()
            if (adminSnap.exists()) {
                val admin = adminSnap.toObject(
                    AdminAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: AdminAccount(uid = uid, email = user.email.orEmpty(), displayName = user.displayName ?: "Admin")
                return RoleResolution.AdminReady(admin)
            }
            val userSnap = db.collection("users").document(uid).get().await()
            if (userSnap.exists()) {
                val profile = userSnap.toObject(
                    CustomerProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (profile != null) {
                    return RoleResolution.CustomerReady(profile)
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "admins/$uid")
            RoleResolution.Failure(unwrapFirestoreErrorMessage(e, "Failed to verify account role."))
        }
    }

    suspend fun resolveRoleForPortal(portal: PortalChoice): RoleResolution {
        val user = auth.currentUser
            ?: return RoleResolution.Failure("Please sign in with Google first.")
        val uid = user.uid
        val email = user.email.orEmpty()
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@").ifBlank { "KJ Member" }

        return try {
            val adminDocRef = db.collection("admins").document(uid)
            val userDocRef = db.collection("users").document(uid)
            val adminSnap = adminDocRef.get().await()
            val userSnap = userDocRef.get().await()

            when (portal) {
                PortalChoice.CUSTOMER -> {
                    if (adminSnap.exists()) {
                        return RoleResolution.RoleConflict(
                            "This Google account ($email) is already registered as an Admin. " +
                                "Customer and Admin roles cannot be combined on the same account."
                        )
                    }
                    if (userSnap.exists()) {
                        val profile = userSnap.toObject(
                            CustomerProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        if (profile != null) {
                            return RoleResolution.CustomerReady(profile)
                        }
                    }
                    RoleResolution.CustomerNeedsProfile(
                        uid = uid,
                        email = email,
                        suggestedName = displayName,
                    )
                }

                PortalChoice.ADMIN -> {
                    if (userSnap.exists()) {
                        return RoleResolution.RoleConflict(
                            "This Google account ($email) is already registered as a Customer. " +
                                "Customer and Admin roles cannot be combined on the same account."
                        )
                    }
                    if (adminSnap.exists()) {
                        val admin = adminSnap.toObject(
                            AdminAccount::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: AdminAccount(uid = uid, email = email, displayName = displayName)
                        return RoleResolution.AdminReady(admin)
                    }

                    // Check admin registry capacity (strict 2-admin limit)
                    val registryRef = db.collection("config").document("adminRegistry")
                    val registrySnap = registryRef.get().await()
                    val existingUids = if (registrySnap.exists()) {
                        registrySnap.toObject(
                            AdminRegistry::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.adminUids.orEmpty()
                    } else {
                        emptyList()
                    }

                    if (existingUids.size >= 2 && uid !in existingUids) {
                        return RoleResolution.AdminLimitReached(
                            "Admin access is strictly limited to the first 2 authorized accounts. " +
                                "All new accounts can sign in using the Customer portal."
                        )
                    }

                    val slotNumber = if (existingUids.isEmpty()) 1L else 2L
                    val batch = db.batch()
                    if (!registrySnap.exists()) {
                        batch.set(
                            registryRef,
                            mapOf(
                                "adminUids" to listOf(uid),
                                "maxAdmins" to 2L,
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                    } else {
                        batch.update(
                            registryRef,
                            mapOf(
                                "adminUids" to existingUids + uid,
                                "maxAdmins" to 2L,
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                    }
                    batch.set(
                        adminDocRef,
                        mapOf(
                            "uid" to uid,
                            "email" to email.take(160),
                            "displayName" to displayName.take(100),
                            "slotNumber" to slotNumber,
                            "createdAt" to FieldValue.serverTimestamp(),
                        )
                    )
                    batch.commit().await()

                    val claimed = AdminAccount(
                        id = uid,
                        uid = uid,
                        email = email,
                        displayName = displayName,
                        slotNumber = slotNumber,
                    )
                    RoleResolution.AdminReady(claimed)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "admins/$uid")
            val msg = unwrapFirestoreErrorMessage(e, "Failed to verify role access.")
            if (portal == PortalChoice.ADMIN && msg.contains("Permission denied", ignoreCase = true)) {
                RoleResolution.AdminLimitReached(
                    "Admin registration denied: either 2 admins are already authorized or this account is locked to Customer."
                )
            } else {
                RoleResolution.Failure(msg)
            }
        }
    }

    suspend fun saveCustomerProfile(
        name: String,
        mobile: String,
        address: String,
        locationLabel: String,
        latitude: Double,
        longitude: Double,
        photoBase64: String = "",
        isUpdate: Boolean,
    ): Result<CustomerProfile> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Must be signed in with Google."))
        val uid = user.uid
        val trimmedName = name.trim()
        val trimmedMobile = mobile.trim()
        val trimmedAddress = address.trim()
        val trimmedLabel = locationLabel.trim().ifBlank { "Pinned Location" }.take(160)
        val trimmedPhoto = photoBase64.trim()

        if (trimmedName.isEmpty() || trimmedName.length > 100) {
            return Result.failure(IllegalArgumentException("Name must be between 1 and 100 characters."))
        }
        if (trimmedMobile.length < 6 || trimmedMobile.length > 24) {
            return Result.failure(IllegalArgumentException("Please enter a valid mobile number (6–24 digits)."))
        }
        if (trimmedAddress.length < 3 || trimmedAddress.length > 300) {
            return Result.failure(IllegalArgumentException("Address must be at least 3 characters."))
        }
        if (trimmedPhoto.length > 80_000) {
            return Result.failure(IllegalArgumentException("Profile photo is too large. Please choose another image."))
        }

        val docRef = db.collection("users").document(uid)
        return try {
            if (isUpdate) {
                docRef.update(
                    mapOf(
                        "name" to trimmedName,
                        "mobile" to trimmedMobile,
                        "address" to trimmedAddress,
                        "locationLabel" to trimmedLabel,
                        "latitude" to latitude.coerceIn(-90.0, 90.0),
                        "longitude" to longitude.coerceIn(-180.0, 180.0),
                        "photoBase64" to trimmedPhoto,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "uid" to uid,
                        "role" to "customer",
                        "name" to trimmedName,
                        "email" to (user.email.orEmpty().take(160)),
                        "mobile" to trimmedMobile,
                        "address" to trimmedAddress,
                        "locationLabel" to trimmedLabel,
                        "latitude" to latitude.coerceIn(-90.0, 90.0),
                        "longitude" to longitude.coerceIn(-180.0, 180.0),
                        "photoBase64" to trimmedPhoto,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            val savedSnap = docRef.get().await()
            val saved = savedSnap.toObject(
                CustomerProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: CustomerProfile(
                id = uid,
                uid = uid,
                role = "customer",
                name = trimmedName,
                email = user.email.orEmpty(),
                mobile = trimmedMobile,
                address = trimmedAddress,
                locationLabel = trimmedLabel,
                latitude = latitude,
                longitude = longitude,
                photoBase64 = trimmedPhoto,
            )
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(Exception(unwrapFirestoreErrorMessage(e, "Failed to save customer profile."), e))
        }
    }

    suspend fun isAdminSlotsFull(): Boolean {
        return try {
            val snap = db.collection("config").document("adminRegistry").get().await()
            if (!snap.exists()) return false
            val reg = snap.toObject(AdminRegistry::class.java)
            (reg?.adminUids?.size ?: 0) >= 2
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}

sealed interface GoogleSignInResult {
    data object Success : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
}
