package io.github.otato.recipebook.feature.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otato.recipebook.designsystem.components.AppCard
import io.github.otato.recipebook.designsystem.components.AppChip
import io.github.otato.recipebook.designsystem.components.AppDivider
import io.github.otato.recipebook.designsystem.components.AppIconButton
import io.github.otato.recipebook.designsystem.components.AppTopBar
import io.github.otato.recipebook.designsystem.components.OutlineButton
import io.github.otato.recipebook.designsystem.components.PrimaryButton
import io.github.otato.recipebook.designsystem.components.Tag
import io.github.otato.recipebook.designsystem.components.dismissKeyboardOnTouch
import io.github.otato.recipebook.designsystem.icons.AppIcons
import io.github.otato.recipebook.designsystem.theme.AppTheme
import io.github.otato.recipebook.domain.logic.StatusFilter
import io.github.otato.recipebook.domain.model.RecipeSort
import io.github.otato.recipebook.domain.model.RecipeStatus
import io.github.otato.recipebook.domain.model.RecipeSummary
import io.github.otato.recipebook.ui.common.Labels
import io.github.otato.recipebook.ui.common.appViewModel
import io.github.otato.recipebook.ui.common.formatShortDate
import io.github.otato.recipebook.ui.common.readClipboard
import io.github.otato.recipebook.ui.common.rememberContainer
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(
    onOpenRecipe: (String) -> Unit,
    onNewRecipe: () -> Unit,
    onPasteRecipe: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = rememberContainer()
    val viewModel = appViewModel { HomeViewModel(it.data.recipes, it.data.settings, it.deletedRecipe) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(deleted) {
        val item = deleted ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("「${item.title}」を削除しました", actionLabel = "元に戻す", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(item) else viewModel.dismissDeleted()
    }

    HomeScreen(
        state = state,
        snackbar = snackbar,
        onOpenRecipe = onOpenRecipe,
        onNewRecipe = onNewRecipe,
        onPaste = {
            // 「貼り付け」: コピーしてある文章を、材料と手順に分けて入れる（F-CL-04、F-CL-05）
            val text = readClipboard(context)
            if (text == null) {
                scope.launch { snackbar.showSnackbar("コピーした文章がありません。レシピの文章をコピーしてから押してください") }
            } else {
                container.pendingImport.value = text
                onPasteRecipe()
            }
        },
        onOpenSettings = onOpenSettings,
        onQueryChange = viewModel::setQuery,
        onStatusChange = viewModel::setStatus,
        onToggleTag = viewModel::toggleTag,
        onSortChange = viewModel::setSort,
    )
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    snackbar: SnackbarHostState,
    onOpenRecipe: (String) -> Unit,
    onNewRecipe: () -> Unit,
    onPaste: () -> Unit,
    onOpenSettings: () -> Unit,
    onQueryChange: (String) -> Unit,
    onStatusChange: (StatusFilter) -> Unit,
    onToggleTag: (String) -> Unit,
    onSortChange: (RecipeSort) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "レシピ帳",
            actions = { AppIconButton(AppIcons.Settings, "設定", onOpenSettings) },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Unit
                state.totalCount == 0 -> EmptyGuide()
                state.recipes.isEmpty() -> Text(
                    "見つかりませんでした",
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.sub,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().dismissKeyboardOnTouch(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.recipes, key = { it.id }) { recipe ->
                        RecipeCard(recipe, onClick = { onOpenRecipe(recipe.id) })
                    }
                }
            }
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        HomeDock(
            state = state,
            onNewRecipe = onNewRecipe,
            onPaste = onPaste,
            onQueryChange = onQueryChange,
            onStatusChange = onStatusChange,
            onToggleTag = onToggleTag,
            onSortChange = onSortChange,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeCard(recipe: RecipeSummary, onClick: () -> Unit) {
    val colors = AppTheme.colors
    AppCard(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    recipe.title,
                    style = AppTheme.typography.heading,
                    color = colors.fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (recipe.status == RecipeStatus.Staple) {
                    Icon(AppIcons.Star, contentDescription = "定番", tint = colors.accent, modifier = Modifier.size(20.dp))
                }
            }
            Text(metaLine(recipe), style = AppTheme.typography.caption, color = colors.sub)
            recipe.latestNextNote?.let { note ->
                Text(
                    "次は: $note",
                    style = AppTheme.typography.caption,
                    color = colors.onNote,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.note)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (recipe.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    recipe.tags.forEach { Tag(it.name) }
                }
            }
        }
    }
}

/** 「作った · 3 回 · 最後 11/20 · また作りたい」 */
private fun metaLine(recipe: RecipeSummary): String = buildList {
    add(Labels.status(recipe.status))
    if (recipe.cookCount > 0) add("${recipe.cookCount} 回")
    recipe.lastCookedOn?.let { add("最後 ${formatShortDate(it)}") }
    recipe.lastRating?.let { add(Labels.rating(it)) }
}.joinToString(" · ")

@Composable
private fun EmptyGuide() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("まだレシピがありません", style = AppTheme.typography.heading, color = AppTheme.colors.fg)
        Spacer(Modifier.height(12.dp))
        Text(
            "YouTube やブラウザで見つけたレシピは、その画面の「共有」から「レシピ帳に追加」を選ぶと、ここに入ります。",
            style = AppTheme.typography.body,
            color = AppTheme.colors.sub,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Gemini などの AI の回答や、レシピの文章は、コピーしてから下の「貼り付け」を押すと、材料と作り方に分けて入ります。",
            style = AppTheme.typography.body,
            color = AppTheme.colors.sub,
        )
    }
}

/** 画面の下の操作（絞り込み・検索・並べ替え・追加）。親指で届くように、下にまとめる。 */
@Composable
private fun HomeDock(
    state: HomeUiState,
    onNewRecipe: () -> Unit,
    onPaste: () -> Unit,
    onQueryChange: (String) -> Unit,
    onStatusChange: (StatusFilter) -> Unit,
    onToggleTag: (String) -> Unit,
    onSortChange: (RecipeSort) -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        AppDivider()
        if (state.totalCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusFilter.entries.forEach { filter ->
                    AppChip(
                        text = statusFilterLabel(filter),
                        selected = state.status == filter,
                        onClick = { onStatusChange(filter) },
                        icon = if (filter == StatusFilter.Staple) AppIcons.Star else null,
                    )
                }
                if (state.tags.isNotEmpty()) Spacer(Modifier.width(4.dp))
                state.tags.forEach { tag ->
                    AppChip(
                        text = tag.name,
                        selected = tag.id in state.selectedTagIds,
                        onClick = { onToggleTag(tag.id) },
                        icon = if (tag.id in state.selectedTagIds) AppIcons.Check else AppIcons.Tag,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("料理名・材料・メモで探す") },
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                    trailingIcon = if (state.query.isNotEmpty()) {
                        { AppIconButton(AppIcons.Close, "検索を消す", { onQueryChange("") }) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                SortMenu(state.sort, onSortChange)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlineButton("貼り付け", onPaste, icon = AppIcons.Paste, modifier = Modifier.weight(1f))
            PrimaryButton("新しく書く", onNewRecipe, icon = AppIcons.Plus, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SortMenu(current: RecipeSort, onSortChange: (RecipeSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppIconButton(AppIcons.Sort, "並べ替え: ${sortLabel(current)}", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RecipeSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(sortLabel(sort), fontWeight = if (sort == current) FontWeight.Bold else FontWeight.Normal)
                    },
                    leadingIcon = if (sort == current) {
                        { Icon(AppIcons.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSortChange(sort)
                    },
                )
            }
        }
    }
}

private fun statusFilterLabel(filter: StatusFilter): String = when (filter) {
    StatusFilter.All -> "すべて"
    StatusFilter.Want -> "気になる"
    StatusFilter.Made -> "作った"
    StatusFilter.Staple -> "定番"
}

private fun sortLabel(sort: RecipeSort): String = when (sort) {
    RecipeSort.Added -> "追加した順"
    RecipeSort.RecentlyCooked -> "最近作った順"
    RecipeSort.MostCooked -> "よく作る順"
    RecipeSort.BestRated -> "評価の良い順"
}
