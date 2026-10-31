package com.someone1703.dailyread

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.someone1703.dailyread.reminders.Notifications
import com.someone1703.dailyread.ui.BookScreen
import com.someone1703.dailyread.ui.ImportScreen
import com.someone1703.dailyread.ui.LibraryScreen
import com.someone1703.dailyread.ui.ReaderScreen
import com.someone1703.dailyread.ui.theme.DailyReadTheme

class MainActivity : ComponentActivity() {

    /** Route requested by a notification tap; consumed once navigation happens. */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingRoute.value = routeFrom(intent)

        setContent {
            DailyReadTheme {
                val nav = rememberNavController()

                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !Notifications.canPost(this@MainActivity)
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val route = pendingRoute.value
                LaunchedEffect(route) {
                    if (route != null) {
                        nav.navigate(route)
                        pendingRoute.value = null
                    }
                }

                NavHost(
                    navController = nav,
                    startDestination = "library",
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(120)) },
                ) {
                    composable("library") {
                        LibraryScreen(
                            onImport = { uri -> nav.navigate("import/${Uri.encode(uri.toString())}") },
                            onOpenBook = { id -> nav.navigate("book/$id") },
                            onRead = { id, index -> nav.navigate("reader/$id/$index") },
                        )
                    }
                    composable(
                        "import/{uri}",
                        arguments = listOf(navArgument("uri") { type = NavType.StringType }),
                    ) { entry ->
                        // Navigation already decodes the argument once; don't decode again.
                        val uri = Uri.parse(entry.arguments?.getString("uri").orEmpty())
                        ImportScreen(
                            uri = uri,
                            onBack = { nav.popBackStack() },
                            onSaved = { id ->
                                nav.navigate("book/$id") { popUpTo("library") }
                            },
                        )
                    }
                    composable(
                        "book/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        BookScreen(
                            bookId = entry.arguments?.getString("id").orEmpty(),
                            onBack = { nav.popBackStack() },
                            onRead = { id, index -> nav.navigate("reader/$id/$index") },
                        )
                    }
                    composable(
                        "reader/{id}/{index}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType },
                            navArgument("index") { type = NavType.IntType },
                        ),
                    ) { entry ->
                        ReaderScreen(
                            bookId = entry.arguments?.getString("id").orEmpty(),
                            index = entry.arguments?.getInt("index") ?: 0,
                            onBack = { nav.popBackStack() },
                            onOpen = { id, index ->
                                nav.popBackStack()
                                nav.navigate("reader/$id/$index")
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFrom(intent)?.let { pendingRoute.value = it }
    }

    private fun routeFrom(intent: Intent?): String? {
        val id = intent?.getStringExtra(Notifications.EXTRA_BOOK_ID) ?: return null
        val index = intent.getIntExtra(Notifications.EXTRA_READING_INDEX, -1)
        return if (index >= 0) "reader/$id/$index" else "book/$id"
    }
}
