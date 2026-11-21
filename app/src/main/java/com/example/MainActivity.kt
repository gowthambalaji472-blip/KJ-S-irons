package com.example

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.AuthRoleRepository
import com.example.data.LaundryRepository
import com.example.data.NotificationHelper
import com.example.model.AdminAccount
import com.example.model.CustomerProfile
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.CustomerHomeScreen
import com.example.ui.screens.CustomerProfileSetupScreen
import com.example.ui.screens.NewOrderWizardScreen
import com.example.ui.screens.OrderDetailAndChatScreen
import com.example.ui.screens.RoleGatewayScreen
import com.example.ui.theme.KjIronsTheme
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.AppSessionState
import com.example.viewmodel.CustomerViewModel
import com.example.viewmodel.OrderDetailViewModel
import com.example.viewmodel.SessionViewModel

private const val ROUTE_CUSTOMER_HOME = "customer_home"
private const val ROUTE_NEW_ORDER = "new_order"
private const val ROUTE_EDIT_PROFILE = "edit_profile"
private const val ROUTE_ADMIN_DASHBOARD = "admin_dashboard"
private const val ROUTE_ORDER_DETAIL = "order_detail/{orderId}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialOrderId = intent?.getStringExtra("extra_order_id")
        setContent {
            KjIronsTheme {
                KjIronsApp(initialNotificationOrderId = initialOrderId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

private fun resolveDatabaseId(app: Application): String =
    app.getString(R.string.firestore_database_id)

@Composable
fun KjIronsApp(initialNotificationOrderId: String?) {
    val context = LocalContext.current
    val sessionViewModel: SessionViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                SessionViewModel(AuthRoleRepository(resolveDatabaseId(app)))
            }
        }
    )

    val sessionState by sessionViewModel.sessionState.collectAsStateWithLifecycle()
    val isBusy by sessionViewModel.isBusy.collectAsStateWithLifecycle()
    val authMessage by sessionViewModel.authMessage.collectAsStateWithLifecycle()
    val adminSlotsFull by sessionViewModel.adminSlotsFull.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        sessionViewModel.bootstrapSession(context)
    }

    when (val state = sessionState) {
        AppSessionState.Checking -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
        }

        AppSessionState.Unauthenticated -> {
            RoleGatewayScreen(
                isBusy = isBusy,
                authMessage = authMessage,
                adminSlotsFull = adminSlotsFull,
                onSelectPortal = { activity, portal ->
                    sessionViewModel.signInWithPortal(activity, portal)
                },
                onDismissMessage = sessionViewModel::clearAuthMessage,
            )
        }

        is AppSessionState.CustomerProfileRequired -> {
            key(state.uid) {
                CustomerProfileSetupScreen(
                    initialName = state.suggestedName,
                    initialEmail = state.email,
                    existingProfile = null,
                    isBusy = isBusy,
                    errorMessage = authMessage,
                    onSaveProfile = { name, mobile, address, label, lat, lng, photoBase64 ->
                        sessionViewModel.saveCustomerProfile(
                            name = name,
                            mobile = mobile,
                            address = address,
                            locationLabel = label,
                            latitude = lat,
                            longitude = lng,
                            photoBase64 = photoBase64,
                            isUpdate = false,
                        )
                    },
                    onBack = null,
                    onSignOut = { sessionViewModel.signOut(context) },
                )
            }
        }

        is AppSessionState.CustomerAuthenticated -> {
            key(state.profile.uid) {
                CustomerNavigationFlow(
                    profile = state.profile,
                    sessionViewModel = sessionViewModel,
                    initialNotificationOrderId = initialNotificationOrderId,
                )
            }
        }

        is AppSessionState.AdminAuthenticated -> {
            key(state.admin.uid) {
                AdminNavigationFlow(
                    admin = state.admin,
                    sessionViewModel = sessionViewModel,
                    initialNotificationOrderId = initialNotificationOrderId,
                )
            }
        }
    }
}

@Composable
private fun CustomerNavigationFlow(
    profile: CustomerProfile,
    sessionViewModel: SessionViewModel,
    initialNotificationOrderId: String?,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val isBusy by sessionViewModel.isBusy.collectAsStateWithLifecycle()
    val authMessage by sessionViewModel.authMessage.collectAsStateWithLifecycle()

    val customerViewModel: CustomerViewModel = viewModel(
        key = "customer_${profile.uid}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                CustomerViewModel(
                    repository = LaundryRepository(resolveDatabaseId(app)),
                    notificationHelper = NotificationHelper(app),
                    customerId = profile.uid,
                )
            }
        }
    )

    LaunchedEffect(initialNotificationOrderId) {
        if (!initialNotificationOrderId.isNullOrBlank()) {
            navController.navigate("order_detail/$initialNotificationOrderId") {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_CUSTOMER_HOME,
    ) {
        composable(ROUTE_CUSTOMER_HOME) {
            CustomerHomeScreen(
                profile = profile,
                viewModel = customerViewModel,
                onNewOrder = { navController.navigate(ROUTE_NEW_ORDER) },
                onOpenOrder = { orderId -> navController.navigate("order_detail/$orderId") },
                onEditProfile = { navController.navigate(ROUTE_EDIT_PROFILE) },
                onSignOut = { sessionViewModel.signOut(context) },
            )
        }

        composable(ROUTE_NEW_ORDER) {
            NewOrderWizardScreen(
                profile = profile,
                viewModel = customerViewModel,
                onBack = { navController.popBackStack() },
                onOrderPlaced = { orderId ->
                    navController.navigate("order_detail/$orderId") {
                        popUpTo(ROUTE_CUSTOMER_HOME)
                    }
                },
            )
        }

        composable(ROUTE_EDIT_PROFILE) {
            CustomerProfileSetupScreen(
                initialName = profile.name,
                initialEmail = profile.email,
                existingProfile = profile,
                isBusy = isBusy,
                errorMessage = authMessage,
                onSaveProfile = { name, mobile, address, label, lat, lng, photoBase64 ->
                    sessionViewModel.saveCustomerProfile(
                        name = name,
                        mobile = mobile,
                        address = address,
                        locationLabel = label,
                        latitude = lat,
                        longitude = lng,
                        photoBase64 = photoBase64,
                        isUpdate = true,
                        onComplete = { navController.popBackStack() },
                    )
                },
                onBack = {
                    sessionViewModel.clearAuthMessage()
                    navController.popBackStack()
                },
                onSignOut = { sessionViewModel.signOut(context) },
            )
        }

        composable(
            route = ROUTE_ORDER_DETAIL,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId").orEmpty()
            val detailViewModel: OrderDetailViewModel = viewModel(
                key = "detail_cust_${profile.uid}_$orderId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        OrderDetailViewModel(
                            repository = LaundryRepository(resolveDatabaseId(app)),
                            notificationHelper = NotificationHelper(app),
                            orderId = orderId,
                            currentUserId = profile.uid,
                            isAdmin = false,
                        )
                    }
                }
            )
            OrderDetailAndChatScreen(
                orderId = orderId,
                currentUserId = profile.uid,
                senderName = profile.name,
                isAdmin = false,
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun AdminNavigationFlow(
    admin: AdminAccount,
    sessionViewModel: SessionViewModel,
    initialNotificationOrderId: String?,
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val adminViewModel: AdminViewModel = viewModel(
        key = "admin_${admin.uid}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                AdminViewModel(
                    repository = LaundryRepository(resolveDatabaseId(app)),
                    notificationHelper = NotificationHelper(app),
                    adminId = admin.uid,
                )
            }
        }
    )

    LaunchedEffect(initialNotificationOrderId) {
        if (!initialNotificationOrderId.isNullOrBlank()) {
            navController.navigate("order_detail/$initialNotificationOrderId") {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_ADMIN_DASHBOARD,
    ) {
        composable(ROUTE_ADMIN_DASHBOARD) {
            AdminDashboardScreen(
                admin = admin,
                viewModel = adminViewModel,
                onOpenOrder = { orderId -> navController.navigate("order_detail/$orderId") },
                onSignOut = { sessionViewModel.signOut(context) },
            )
        }

        composable(
            route = ROUTE_ORDER_DETAIL,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId").orEmpty()
            val detailViewModel: OrderDetailViewModel = viewModel(
                key = "detail_admin_${admin.uid}_$orderId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        OrderDetailViewModel(
                            repository = LaundryRepository(resolveDatabaseId(app)),
                            notificationHelper = NotificationHelper(app),
                            orderId = orderId,
                            currentUserId = admin.uid,
                            isAdmin = true,
                        )
                    }
                }
            )
            OrderDetailAndChatScreen(
                orderId = orderId,
                currentUserId = admin.uid,
                senderName = "KJ Iron's Admin",
                isAdmin = true,
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
