package io.github.otato.recipebook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.otato.recipebook.feature.cooklog.CookLogRoute
import io.github.otato.recipebook.feature.detail.RecipeDetailRoute
import io.github.otato.recipebook.feature.edit.RecipeEditRoute
import io.github.otato.recipebook.feature.edit.RecipeEditViewModel
import io.github.otato.recipebook.feature.home.HomeRoute
import io.github.otato.recipebook.feature.kitchen.KitchenRoute
import io.github.otato.recipebook.feature.settings.SettingsRoute
import io.github.otato.recipebook.feature.settings.TagsRoute
import io.github.otato.recipebook.feature.versions.VersionsRoute

private fun NavBackStackEntry.arg(name: String): String = requireNotNull(arguments?.getString(name)) { "引数がありません: $name" }

private fun NavBackStackEntry.optionalArg(name: String): String? = arguments?.getString(name)?.ifEmpty { null }

private fun stringArg(name: String) = navArgument(name) { type = NavType.StringType }

private fun optionalStringArg(name: String, default: String = "") = navArgument(name) {
    type = NavType.StringType
    defaultValue = default
}

/** 画面のつながり。引数の取り出しと、画面を移る操作だけをここに集める。 */
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            HomeRoute(
                onOpenRecipe = { navController.navigate(Routes.detail(it)) },
                onNewRecipe = { navController.navigate(Routes.edit(Routes.NEW_ID)) },
                onPasteRecipe = { navController.navigate(Routes.edit(Routes.NEW_ID, from = RecipeEditViewModel.FROM_PASTE)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.DETAIL, arguments = listOf(stringArg("recipeId"))) { entry ->
            val id = entry.arg("recipeId")
            RecipeDetailRoute(
                recipeId = id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(id)) },
                onCook = { navController.navigate(Routes.kitchen(id)) },
                onAddCookLog = { navController.navigate(Routes.cookLog(id)) },
                onEditCookLog = { navController.navigate(Routes.cookLog(id, it)) },
                onOpenVersions = { navController.navigate(Routes.versions(id)) },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(stringArg("recipeId"), optionalStringArg("from", RecipeEditViewModel.FROM_NONE), optionalStringArg("logId")),
        ) { entry ->
            val id = entry.arg("recipeId").takeIf { it != Routes.NEW_ID }
            RecipeEditRoute(
                recipeId = id,
                from = entry.optionalArg("from") ?: RecipeEditViewModel.FROM_NONE,
                logId = entry.optionalArg("logId"),
                onBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    if (id == null) {
                        // 新しく作ったら、編集の画面を閉じて、そのレシピを開く
                        navController.navigate(Routes.detail(savedId)) {
                            popUpTo(Routes.EDIT) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable(Routes.COOK_LOG, arguments = listOf(stringArg("recipeId"), optionalStringArg("logId"))) { entry ->
            val id = entry.arg("recipeId")
            CookLogRoute(
                recipeId = id,
                logId = entry.optionalArg("logId"),
                onBack = { navController.popBackStack() },
                onReflect = { logId ->
                    navController.navigate(Routes.edit(id, from = RecipeEditViewModel.FROM_LOG, logId = logId)) {
                        popUpTo(Routes.COOK_LOG) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.KITCHEN, arguments = listOf(stringArg("recipeId"))) { entry ->
            val id = entry.arg("recipeId")
            KitchenRoute(
                recipeId = id,
                onClose = { navController.popBackStack() },
                onFinished = {
                    // 作り終えたら、そのまま作った記録の入力へ（F-CK-04）
                    navController.navigate(Routes.cookLog(id)) {
                        popUpTo(Routes.KITCHEN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.VERSIONS, arguments = listOf(stringArg("recipeId"))) { entry ->
            VersionsRoute(recipeId = entry.arg("recipeId"), onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(onBack = { navController.popBackStack() }, onOpenTags = { navController.navigate(Routes.TAGS) })
        }
        composable(Routes.TAGS) { TagsRoute(onBack = { navController.popBackStack() }) }
    }
}
