package dev.hostyn.phone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import dev.hostyn.phone.ui.theme.LocalNavAnimatedScope
import dev.hostyn.phone.ui.theme.LocalSharedTransitionScope
import dev.hostyn.phone.ui.theme.LocalTransformOrigin
import dev.hostyn.phone.ui.theme.TransformOrigin
import dev.hostyn.phone.ui.theme.backwardEnter
import dev.hostyn.phone.ui.theme.backwardExit
import dev.hostyn.phone.ui.theme.bottomEnter
import dev.hostyn.phone.ui.theme.bottomExit
import dev.hostyn.phone.ui.theme.forwardEnter
import dev.hostyn.phone.ui.theme.forwardExit
import dev.hostyn.phone.ui.theme.transformEnter
import dev.hostyn.phone.ui.theme.transformExit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.hostyn.phone.call.MissedCallNotifier
import dev.hostyn.phone.ui.PermissionGate
import dev.hostyn.phone.ui.components.ImportDialog
import dev.hostyn.phone.ui.blocked.BlockedNumbersScreen
import dev.hostyn.phone.ui.detail.DetailScreen
import dev.hostyn.phone.ui.dialer.DialerScreen
import dev.hostyn.phone.ui.edit.EditScreen
import dev.hostyn.phone.ui.home.HomeScreen
import dev.hostyn.phone.ui.settings.SettingsScreen
import dev.hostyn.phone.ui.number.NumberScreen
import dev.hostyn.phone.ui.theme.PhoneTheme

class MainActivity : ComponentActivity() {
    /** Number requested by another app (ACTION_DIAL / tel:), waiting to be opened in the dialpad. */
    private val dialRequest = mutableStateOf<String?>(null)
    /** Bumped when the missed calls notification asks to show Recents. */
    private val recentsRequest = mutableIntStateOf(0)
    /** vCard file opened from another app, waiting to be imported. */
    private val importRequest = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val app = application as PhoneApp

        setContent {
            PhoneTheme {
                PermissionGate {
                    AppNavigation(app, dialRequest, recentsRequest, importRequest)
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
            Intent.ACTION_DIAL -> dialRequest.value = intent.data?.schemeSpecificPart.orEmpty()
            Intent.ACTION_VIEW ->
                if (intent.data?.scheme == "tel") dialRequest.value = intent.data?.schemeSpecificPart.orEmpty()
                else importRequest.value = intent.data
            MissedCallNotifier.ACTION_SHOW_RECENTS -> recentsRequest.intValue++
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppNavigation(
    app: PhoneApp,
    dialRequest: MutableState<String?>,
    recentsRequest: MutableIntState,
    importRequest: MutableState<Uri?>,
) {
    val nav = rememberNavController()

    importRequest.value?.let { uri -> ImportDialog(uri, app.repository) { importRequest.value = null } }

    LaunchedEffect(recentsRequest.intValue) {
        if (recentsRequest.intValue > 0) nav.popBackStack("home", inclusive = false)
    }

    LaunchedEffect(dialRequest.value) {
        val number = dialRequest.value ?: return@LaunchedEffect
        nav.navigate("dialpad?number=${Uri.encode(number)}") { launchSingleTop = true }
        dialRequest.value = null
    }

    val density = LocalDensity.current
    val origin = remember { TransformOrigin() }
    /** [route], plus the element it grows out of if a row just asked for a container transform. */
    fun withOrigin(route: String): String {
        val key = origin.take() ?: return route
        return route + (if ('?' in route) "&" else "?") + "from=" + Uri.encode(key)
    }

    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this, LocalTransformOrigin provides origin) {
    NavHost(
        navController = nav,
        startDestination = "home",
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        // M3 forward and backward pattern; screens opened with a container transform only fade
        // underneath it. The dialpad enters from the bottom edge like a sheet.
        enterTransition = {
            when {
                targetState.transformKey() != null -> transformEnter()
                targetState.isDialpad() -> bottomEnter()
                else -> density.forwardEnter()
            }
        },
        exitTransition = { if (targetState.transformKey() != null) transformExit() else density.forwardExit() },
        popEnterTransition = { if (initialState.transformKey() != null) transformEnter() else density.backwardEnter() },
        popExitTransition = {
            when {
                initialState.transformKey() != null -> transformExit()
                initialState.isDialpad() -> bottomExit()
                else -> density.backwardExit()
            }
        },
    ) {
        screen("home") {
            HomeScreen(
                repo = app.repository,
                callLog = app.callLog,
                blocked = app.blocked,
                onOpenContact = { nav.navigate(withOrigin("detail/$it")) },
                onCreateContact = { phone ->
                    nav.navigate(if (phone == null) "edit" else "edit?phone=${Uri.encode(phone)}")
                },
                onDialpad = { nav.navigate(withOrigin("dialpad")) },
                onSettings = { nav.navigate("settings") },
                onOpenNumber = { nav.navigate(withOrigin("number/${Uri.encode(it)}")) },
                recentsRequest = recentsRequest.intValue,
            )
        }
        screen("number/{number}?from={from}", listOf(fromArgument())) { entry ->
            NumberScreen(
                number = entry.arguments?.getString("number").orEmpty(),
                callLog = app.callLog,
                blocked = app.blocked,
                onBack = { nav.popBackStack() },
                onOpenContact = { id ->
                    nav.navigate("detail/$id") { popUpTo("home") }
                },
                onAddContact = { nav.navigate("edit?phone=${Uri.encode(it)}") },
                transformKey = entry.transformKey(),
            )
        }
        screen("settings") {
            SettingsScreen(
                settings = app.settings,
                repo = app.repository,
                onBack = { nav.popBackStack() },
                onBlockedNumbers = { nav.navigate("blocked") },
            )
        }
        screen("blocked") {
            BlockedNumbersScreen(
                blocked = app.blocked,
                onBack = { nav.popBackStack() },
            )
        }
        screen(
            "dialpad?number={number}&from={from}",
            listOf(navArgument("number") { type = NavType.StringType; defaultValue = "" }, fromArgument()),
        ) { entry ->
            DialerScreen(
                repo = app.repository,
                initialNumber = entry.arguments?.getString("number").orEmpty(),
                onBack = { if (!nav.popBackStack()) nav.navigate("home") },
                onAddContact = { nav.navigate("edit?phone=${Uri.encode(it)}") },
                transformKey = entry.transformKey(),
            )
        }
        screen(
            "detail/{id}?from={from}",
            listOf(navArgument("id") { type = NavType.LongType }, fromArgument()),
        ) { entry ->
            val id = entry.arguments!!.getLong("id")
            DetailScreen(
                repo = app.repository,
                blocked = app.blocked,
                callLog = app.callLog,
                contactId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("edit?id=$id") },
                transformKey = entry.transformKey(),
            )
        }
        screen(
            "edit?id={id}&phone={phone}",
            listOf(
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
    }
}

private fun fromArgument() = navArgument("from") { type = NavType.StringType; nullable = true; defaultValue = null }

/** The element this screen grows out of (container transform), if it was opened from one. */
private fun NavBackStackEntry.transformKey(): String? = arguments?.getString("from")

private fun NavBackStackEntry.isDialpad() = destination.route?.startsWith("dialpad") == true

/** A destination whose content can take part in container transforms. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route, arguments) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}
