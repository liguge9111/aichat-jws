package com.lirui.charchat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lirui.charchat.ui.attr.AttributeScreen
import com.lirui.charchat.ui.chat.ChatScreen
import com.lirui.charchat.ui.gate.AgeGateScreen
import com.lirui.charchat.ui.group.GroupChatScreen
import com.lirui.charchat.ui.group.GroupListScreen
import com.lirui.charchat.ui.home.HomeScreen
import com.lirui.charchat.ui.import.ImportScreen
import com.lirui.charchat.ui.profile.ProfilesScreen
import com.lirui.charchat.ui.settings.SettingsScreen
import com.lirui.charchat.ui.settings.SettingsViewModel
import com.lirui.charchat.ui.worldbook.WorldBookScreen

object Routes {
    const val GATE = "gate"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val CHAT = "chat"
    const val ATTR = "attr"
    const val GROUP_LIST = "groups"
    const val GROUP_CHAT = "groupchat"
    const val PROFILES = "profiles"
    const val WORLD_BOOK = "worldbook"
}

@Composable
fun AppNav(nav: NavHostController = rememberNavController()) {
    NavHost(navController = nav, startDestination = Routes.GATE) {
        composable(Routes.GATE) {
            val settingsVm: SettingsViewModel = hiltViewModel()
            AgeGateScreen(
                onVerified = {
                    settingsVm.setAgeVerified()
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.GATE) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onImport = { nav.navigate(Routes.IMPORT) },
                onOpenChat = { cardId -> nav.navigate("${Routes.CHAT}/$cardId") },
                onOpenGroups = { nav.navigate(Routes.GROUP_LIST) },
                onOpenProfiles = { nav.navigate(Routes.PROFILES) }
            )
        }
        composable(Routes.PROFILES) {
            ProfilesScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.IMPORT) {
            ImportScreen(
                onSaved = { nav.popBackStack() },
                onBack = { nav.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
        composable(
            route = "${Routes.CHAT}/{cardId}",
            arguments = listOf(navArgument("cardId") { type = NavType.StringType })
        ) { backStack ->
            val cardId = backStack.arguments?.getString("cardId") ?: ""
            ChatScreen(
                cardId = cardId,
                onBack = { nav.popBackStack() },
                onOpenAttrs = { nav.navigate("${Routes.ATTR}/$cardId") },
                onOpenWorldBook = { nav.navigate("${Routes.WORLD_BOOK}/$cardId") }
            )
        }
        composable(
            route = "${Routes.ATTR}/{cardId}",
            arguments = listOf(navArgument("cardId") { type = NavType.StringType })
        ) { backStack ->
            val cardId = backStack.arguments?.getString("cardId") ?: ""
            AttributeScreen(
                onBack = { nav.popBackStack() },
                onOpenWorldBook = { nav.navigate("${Routes.WORLD_BOOK}/$cardId") }
            )
        }
        composable(
            route = "${Routes.WORLD_BOOK}/{cardId}",
            arguments = listOf(navArgument("cardId") { type = NavType.StringType })
        ) {
            WorldBookScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.GROUP_LIST) {
            GroupListScreen(
                onBack = { nav.popBackStack() },
                onOpenGroup = { groupId -> nav.navigate("${Routes.GROUP_CHAT}/$groupId") }
            )
        }
        composable(
            route = "${Routes.GROUP_CHAT}/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) {
            GroupChatScreen(onBack = { nav.popBackStack() })
        }
    }
}
