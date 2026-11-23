package io.github.otato.recipebook.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import io.github.otato.recipebook.designsystem.theme.AppTheme
import io.github.otato.recipebook.designsystem.theme.RecipeBookTheme
import io.github.otato.recipebook.domain.model.AppSettings
import io.github.otato.recipebook.domain.model.ThemeMode
import io.github.otato.recipebook.ui.common.rememberContainer
import io.github.otato.recipebook.ui.navigation.AppNavHost
import io.github.otato.recipebook.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow

/** アプリ全体の入口。テーマ（ライト / ダーク / 端末に合わせる）と、画面のつながりを組み立てる。 */
@Composable
fun AppRoot(openRecipe: MutableStateFlow<String?>) {
    val container = rememberContainer()
    val settingsFlow = remember(container) { container.data.settings.observe() }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val dark = when (settings.themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> null
    }

    RecipeBookTheme(darkTheme = dark) {
        SystemBarsEffect(dark = AppTheme.colors.isDark)
        Surface(color = AppTheme.colors.bg, modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            // 共有の画面の「保存して開く」から来たら、そのレシピを開く
            val target by openRecipe.collectAsStateWithLifecycle()
            LaunchedEffect(target) {
                val id = target ?: return@LaunchedEffect
                openRecipe.value = null
                navController.navigate(Routes.detail(id)) {
                    popUpTo(Routes.HOME)
                    launchSingleTop = true
                }
            }
            // キーボードが出たら、画面をその分だけ縮める（入力欄・下のボタンがキーボードに隠れないように）
            AppNavHost(navController = navController, modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding())
        }
    }
}

/** ステータスバー・ナビゲーションバーの文字の色を、アプリのテーマ（ライト / ダーク）に合わせる。 */
@Composable
private fun SystemBarsEffect(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(dark) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
}
