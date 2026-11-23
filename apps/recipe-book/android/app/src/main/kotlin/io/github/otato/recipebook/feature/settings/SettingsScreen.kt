package io.github.otato.recipebook.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.recipebook.designsystem.components.AppDivider
import io.github.otato.recipebook.designsystem.components.AppSegmented
import io.github.otato.recipebook.designsystem.components.AppTopBar
import io.github.otato.recipebook.designsystem.components.ConfirmDialog
import io.github.otato.recipebook.designsystem.components.SectionLabel
import io.github.otato.recipebook.designsystem.components.SettingRow
import io.github.otato.recipebook.designsystem.icons.AppIcons
import io.github.otato.recipebook.designsystem.theme.AppTheme
import io.github.otato.recipebook.domain.model.AppSettings
import io.github.otato.recipebook.domain.model.BackupFormatException
import io.github.otato.recipebook.domain.model.ThemeMode
import io.github.otato.recipebook.domain.repository.SettingsRepository
import io.github.otato.recipebook.platform.BackupFiles
import io.github.otato.recipebook.ui.common.appViewModel
import io.github.otato.recipebook.ui.common.formatDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    data class Share(val intent: Intent) : SettingsEvent
    data class ConfirmRestore(val json: String, val count: Int) : SettingsEvent
}

class SettingsViewModel(private val settings: SettingsRepository, private val backupFiles: BackupFiles) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.update { it.copy(themeMode = mode) } }
    }

    fun export() {
        viewModelScope.launch {
            try {
                _events.emit(SettingsEvent.Share(backupFiles.export()))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.Message("書き出せませんでした"))
            }
        }
    }

    fun inspect(uri: Uri) {
        viewModelScope.launch {
            try {
                val (json, count) = backupFiles.read(uri)
                _events.emit(SettingsEvent.ConfirmRestore(json, count))
            } catch (e: BackupFormatException) {
                _events.emit(SettingsEvent.Message(e.message ?: "読み込めませんでした"))
            }
        }
    }

    fun restore(json: String) {
        viewModelScope.launch {
            try {
                backupFiles.restore(json)
                _events.emit(SettingsEvent.Message("読み込みました"))
            } catch (e: BackupFormatException) {
                _events.emit(SettingsEvent.Message(e.message ?: "読み込めませんでした"))
            }
        }
    }
}

/** 設定（4.8）とバックアップ（F-DA-02）。 */
@Composable
fun SettingsRoute(onBack: () -> Unit, onOpenTags: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.data.settings, it.backupFiles) }
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<SettingsEvent.ConfirmRestore?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::inspect) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> launch { snackbar.showSnackbar(event.text) }
                is SettingsEvent.Share -> context.startActivity(event.intent)
                is SettingsEvent.ConfirmRestore -> confirm = event
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(title = "設定", onBack = onBack)
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel("見た目", modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
                val themes = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)
                AppSegmented(
                    options = listOf("端末に合わせる", "ライト", "ダーク"),
                    selectedIndex = themes.indexOf(settings.themeMode),
                    onSelect = { viewModel.setTheme(themes[it]) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SectionLabel("レシピ", modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp))
                SettingRow(
                    title = "タグの管理",
                    subtitle = "名前の変更・並べ替え・削除",
                    onClick = onOpenTags,
                    trailing = { Icon(AppIcons.Right, contentDescription = null, tint = AppTheme.colors.sub) },
                )
                AppDivider()
                SectionLabel("バックアップ", modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp))
                SettingRow(
                    title = "書き出す",
                    subtitle = settings.lastExportAtMillis?.let {
                        "最後に書き出した日: " + formatDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
                    } ?: "機種変更に備えて、全部のレシピを 1 つのファイルにします。保存先（Google ドライブなど）を選べます",
                    onClick = viewModel::export,
                    trailing = { Icon(AppIcons.Export, contentDescription = null, tint = AppTheme.colors.sub) },
                )
                AppDivider()
                SettingRow(
                    title = "読み込む",
                    subtitle = "書き出したファイルから戻します（いまのデータは置き換わります）",
                    onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    trailing = { Icon(AppIcons.Import, contentDescription = null, tint = AppTheme.colors.sub) },
                )
                AppDivider()
                Text(
                    "データは、この端末の中だけに保存しています。",
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.sub,
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    "バージョン ${appVersion(context)}",
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.sub,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
    }

    confirm?.let { event ->
        ConfirmDialog(
            title = "読み込みますか？",
            message = "ファイルには ${event.count} 件のレシピがあります。いまのレシピは、すべて置き換わります（置き換える前のデータは、アプリの中に残します）。",
            confirmText = "読み込む",
            destructive = true,
            onConfirm = {
                confirm = null
                viewModel.restore(event.json)
            },
            onDismiss = { confirm = null },
        )
    }
}

private fun appVersion(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
