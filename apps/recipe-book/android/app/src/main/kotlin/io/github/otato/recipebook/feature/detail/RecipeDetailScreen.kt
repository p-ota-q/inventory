package io.github.otato.recipebook.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otato.recipebook.designsystem.components.AppCard
import io.github.otato.recipebook.designsystem.components.AppChip
import io.github.otato.recipebook.designsystem.components.AppDivider
import io.github.otato.recipebook.designsystem.components.AppIconButton
import io.github.otato.recipebook.designsystem.components.AppTopBar
import io.github.otato.recipebook.designsystem.components.ConfirmDialog
import io.github.otato.recipebook.designsystem.components.OutlineButton
import io.github.otato.recipebook.designsystem.components.PrimaryButton
import io.github.otato.recipebook.designsystem.components.SectionLabel
import io.github.otato.recipebook.designsystem.components.Tag
import io.github.otato.recipebook.designsystem.components.TextAction
import io.github.otato.recipebook.designsystem.icons.AppIcons
import io.github.otato.recipebook.designsystem.theme.AppTheme
import io.github.otato.recipebook.domain.logic.Versioning
import io.github.otato.recipebook.domain.model.CookLog
import io.github.otato.recipebook.domain.model.Rating
import io.github.otato.recipebook.domain.model.RecipeDetail
import io.github.otato.recipebook.domain.model.RecipeSource
import io.github.otato.recipebook.domain.model.RecipeStatus
import io.github.otato.recipebook.ui.common.Labels
import io.github.otato.recipebook.ui.common.appViewModel
import io.github.otato.recipebook.ui.common.formatDate
import io.github.otato.recipebook.ui.common.openUrl

@Composable
fun RecipeDetailRoute(
    recipeId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCook: () -> Unit,
    onAddCookLog: () -> Unit,
    onEditCookLog: (String) -> Unit,
    onOpenVersions: () -> Unit,
) {
    val viewModel = appViewModel(key = "detail-$recipeId") { RecipeDetailViewModel(recipeId, it.data.recipes, it.deletedRecipe) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val result = snackbar.showSnackbar(message.text, actionLabel = "元に戻す")
            if (result == SnackbarResult.ActionPerformed) message.undo()
        }
    }

    // 削除した・見つからないレシピなら、戻る
    LaunchedEffect(state.loading, state.detail) {
        if (!state.loading && state.detail == null) onBack()
    }

    val detail = state.detail ?: return
    RecipeDetailScreen(
        detail = detail,
        showDiff = state.showDiff,
        snackbar = snackbar,
        onBack = onBack,
        onEdit = onEdit,
        onCook = onCook,
        onAddCookLog = onAddCookLog,
        onEditCookLog = onEditCookLog,
        onDeleteCookLog = viewModel::deleteCookLog,
        onOpenVersions = onOpenVersions,
        onToggleDiff = viewModel::toggleDiff,
        onSetStatus = viewModel::setStatus,
        onDelete = { viewModel.deleteRecipe(onBack) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeDetailScreen(
    detail: RecipeDetail,
    showDiff: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCook: () -> Unit,
    onAddCookLog: () -> Unit,
    onEditCookLog: (String) -> Unit,
    onDeleteCookLog: (String) -> Unit,
    onOpenVersions: () -> Unit,
    onToggleDiff: () -> Unit,
    onSetStatus: (RecipeStatus) -> Unit,
    onDelete: () -> Unit,
) {
    val recipe = detail.recipe
    val content = recipe.content
    val colors = AppTheme.colors
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var importedOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "",
            onBack = onBack,
            actions = {
                AppIconButton(AppIcons.Edit, "レシピを直す", onEdit)
                Box {
                    AppIconButton(AppIcons.More, "そのほかの操作", { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("版の履歴（${detail.versionCount}）") },
                            leadingIcon = { Icon(AppIcons.History, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onOpenVersions()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("レシピを削除", color = colors.error) },
                            leadingIcon = { Icon(AppIcons.Trash, contentDescription = null, tint = colors.error) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            },
        )
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            ) {
                // 次に作るときのメモ（いちばん上。F-LG-04）は、料理名と同じ項目に入れる。別の項目にすると、
                // 記録を付けて戻ってきたとき、一覧が料理名の位置を保ったまま、上に足されたメモが画面の外に隠れる
                item(key = "header") {
                    detail.latestNextNote?.let { log ->
                        NextNoteCard(log)
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(recipe.title, style = AppTheme.typography.title, color = colors.fg)
                    Spacer(Modifier.height(4.dp))
                    Text(summaryLine(detail), style = AppTheme.typography.caption, color = colors.sub)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RecipeStatus.entries.forEach { status ->
                            AppChip(
                                text = Labels.status(status),
                                selected = recipe.status == status,
                                onClick = { onSetStatus(status) },
                                icon = if (status == RecipeStatus.Staple) AppIcons.Star else null,
                            )
                        }
                    }
                    if (recipe.tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            recipe.tags.forEach { Tag(it.name) }
                        }
                    }
                }
                if (recipe.sources.isNotEmpty()) {
                    item(key = "sources") {
                        Section("出典")
                        recipe.sources.forEach { source -> SourceRow(source) { url -> openUrl(context, url) } }
                    }
                }
                item(key = "ingredients") {
                    Section(if (content.servings.isNotBlank()) "材料（${content.servings}）" else "材料") {
                        if (detail.originalVersion != null) {
                            TextAction(if (showDiff) "いまの材料だけ" else "元のレシピとの違い", onToggleDiff)
                        }
                    }
                    when {
                        content.ingredients.isEmpty() -> EmptyNote("まだ入っていません。右上の鉛筆から入れられます")
                        showDiff && detail.originalVersion != null -> IngredientDiffList(
                            Versioning.diffIngredients(detail.originalVersion!!.content.ingredients, content.ingredients),
                        )
                        else -> IngredientList(content.ingredients)
                    }
                }
                item(key = "steps") {
                    Section("作り方")
                    if (content.steps.isEmpty()) EmptyNote("まだ入っていません") else StepList(content.steps)
                }
                if (content.tips.isNotBlank()) {
                    item(key = "tips") {
                        Section("自分のポイント")
                        Text(content.tips, style = AppTheme.typography.body, color = colors.fg)
                    }
                }
                item(key = "logs-title") {
                    Section("作った記録（${detail.cookLogs.size}）") {
                        TextAction("＋ 記録する", onAddCookLog)
                    }
                    if (detail.cookLogs.isEmpty()) EmptyNote("作ったら、感想や変えたところを残しておくと、次に作るときに役立ちます")
                }
                items(detail.cookLogs, key = { "log-${it.id}" }) { log ->
                    CookLogCard(
                        log = log,
                        onEdit = { onEditCookLog(log.id) },
                        onDelete = { onDeleteCookLog(log.id) },
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                if (detail.importedTexts.isNotEmpty()) {
                    item(key = "imported") {
                        Section("取り込んだ文章") {
                            TextAction(if (importedOpen) "とじる" else "ひらく", { importedOpen = !importedOpen })
                        }
                        if (importedOpen) {
                            detail.importedTexts.forEach {
                                Text(it.text, style = AppTheme.typography.caption, color = colors.sub, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        } else {
                            Text(
                                "取り込んだときの文章を、そのまま残してあります",
                                style = AppTheme.typography.caption,
                                color = colors.sub,
                            )
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        // 画面の下: 作る・作った（親指で届くように）
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
            AppDivider()
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("記録する", onAddCookLog, icon = AppIcons.Note, modifier = Modifier.weight(1f))
                PrimaryButton("作る", onCook, icon = AppIcons.Pot, modifier = Modifier.weight(1f))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "このレシピを削除しますか？",
            message = "作った記録や、これまでの版も消えます。削除したあと、しばらくは元に戻せます。",
            confirmText = "削除",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun NextNoteCard(log: CookLog) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.note)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("次に作るときのメモ（${formatDate(log.cookedOn)}）", style = AppTheme.typography.label, color = colors.onNote)
        Spacer(Modifier.height(4.dp))
        Text(log.nextNote, style = AppTheme.typography.bodyStrong, color = colors.onNote)
    }
}

@Composable
private fun Section(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = AppTheme.typography.caption, color = AppTheme.colors.sub)
}

@Composable
private fun SourceRow(source: RecipeSource, onOpen: (String) -> Unit) {
    val url = source.url
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (url != null) Modifier.clickable { onOpen(url) } else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val kind = Labels.sourceKind(source.kind)
            val title = source.title.ifBlank { url ?: kind }
            Text(title, style = AppTheme.typography.body, color = AppTheme.colors.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // 題名が「AI の回答」のように、種類の名前を含むときは、種類を重ねて出さない
            val sub = listOfNotNull(kind.takeUnless { title.contains(it) }, source.note.ifBlank { null }).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = AppTheme.typography.caption, color = AppTheme.colors.sub)
        }
        if (url != null) Icon(AppIcons.Link, contentDescription = "開く", tint = AppTheme.colors.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CookLogCard(log: CookLog, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onEdit)) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(log.cookedOn), style = AppTheme.typography.bodyStrong, color = colors.fg)
                log.rating?.let {
                    Text(
                        "  ${Labels.rating(it)}",
                        style = AppTheme.typography.bodyStrong,
                        color = if (it == Rating.Again) colors.accent else colors.sub,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box {
                    AppIconButton(AppIcons.More, "この記録の操作", { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("直す") }, onClick = {
                            menuOpen = false
                            onEdit()
                        })
                        DropdownMenuItem(text = { Text("削除", color = colors.error) }, onClick = {
                            menuOpen = false
                            onDelete()
                        })
                    }
                }
            }
            Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LogLine("感想", log.impression)
                LogLine("変えたところ", log.changes)
                LogLine("次に作るときのメモ", log.nextNote)
            }
        }
    }
}

@Composable
private fun LogLine(label: String, text: String) {
    if (text.isBlank()) return
    Column {
        Text(label, style = AppTheme.typography.caption, color = AppTheme.colors.sub)
        Text(text, style = AppTheme.typography.body, color = AppTheme.colors.fg)
    }
}

/** 「3 回作った · 最後 11/20（金）」 */
private fun summaryLine(detail: RecipeDetail): String {
    val logs = detail.cookLogs
    if (logs.isEmpty()) return "まだ作っていません"
    return "${logs.size} 回作った · 最後 ${formatDate(logs.first().cookedOn)}"
}
