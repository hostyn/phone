package dev.hostyn.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.hostyn.phone.ui.PermissionGate
import dev.hostyn.phone.ui.detail.DetailScreen
import dev.hostyn.phone.ui.dialer.DialerScreen
import dev.hostyn.phone.ui.edit.EditScreen
import dev.hostyn.phone.ui.home.HomeScreen
import dev.hostyn.phone.ui.theme.PhoneTheme

class MainActivity : ComponentActivity() {
    /** Number requested by another app (ACTION_DIAL / tel:), waiting to be opened in the dialpad. */
    private val dialRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val app = application as PhoneApp

        setContent {
            PhoneTheme {
                PermissionGate {
                    AppNavigation(app, dialRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW ->
                dialRequest.value = intent.data?.schemeSpecificPart.orEmpty()
        }
    }
}

@Composable
private fun AppNavigation(app: PhoneApp, dialRequest: MutableState<String?>) {
    val nav = rememberNavController()

    LaunchedEffect(dialRequest.value) {
        val number = dialRequest.value ?: return@LaunchedEffect
        nav.navigate("dialpad?number=${Uri.encode(number)}") { launchSingleTop = true }
        dialRequest.value = null
    }

    NavHost(
        navController = nav,
        startDestination = "home",
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(300), initialOffset = { it / 5 }) + fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(300), targetOffset = { it / 5 }) + fadeOut(tween(200)) },
    ) {
        composable("home") {
            HomeScreen(
                repo = app.repository,
                callLog = app.callLog,
                onOpenContact = { nav.navigate("detail/$it") },
                onCreateContact = { phone ->
                    nav.navigate(if (phone == null) "edit" else "edit?phone=${Uri.encode(phone)}")
                },
                onDialpad = { nav.navigate("dialpad") },
            )
        }
        composable(
            "dialpad?number={number}",
            arguments = listOf(navArgument("number") { type = NavType.StringType; defaultValue = "" }),
            enterTransition = { slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(250)) },
            popExitTransition = { slideOutVertically(tween(250)) { it / 3 } + fadeOut(tween(200)) },
        ) { entry ->
            DialerScreen(
                repo = app.repository,
                initialNumber = entry.arguments?.getString("number").orEmpty(),
                onBack = { if (!nav.popBackStack()) nav.navigate("home") },
                onAddContact = { nav.navigate("edit?phone=${Uri.encode(it)}") },
            )
        }
        composable(
            "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments!!.getLong("id")
            DetailScreen(
                repo = app.repository,
                contactId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("edit?id=$id") },
            )
        }
        composable(
            "edit?id={id}&phone={phone}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("phone") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val id = entry.arguments!!.getLong("id").takeIf { it > 0 }
            EditScreen(
                repo = app.repository,
                contactId = id,
                initialPhone = entry.arguments?.getString("phone"),
                onClose = { nav.popBackStack() },
                onCreated = { newId ->
                    nav.navigate("detail/$newId") { popUpTo("home") }
                },
            )
        }
    }
}
