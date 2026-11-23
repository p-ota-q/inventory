package io.github.otato.recipebook.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.recipebook.designsystem.components.AppDivider
import io.github.otato.recipebook.designsystem.components.AppIconButton
import io.github.otato.recipebook.designsystem.components.AppTopBar
import io.github.otato.recipebook.designsystem.components.ConfirmDialog
import io.github.otato.recipebook.designsystem.components.ReorderHandle
import io.github.otato.recipebook.designsystem.components.ReorderableRow
import io.github.otato.recipebook.designsystem.components.TextInputDialog
import io.github.otato.recipebook.designsystem.components.rememberReorderState
import io.github.otato.recipebook.designsystem.icons.AppIcons
import io.github.otato.recipebook.designsystem.theme.AppTheme
import io.github.otato.recipebook.domain.model.Tag
import io.github.otato.recipebook.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.otato.recipebook.ui.common.appViewModel

class TagsViewModel(private val recipes: RecipeRepository) : ViewModel() {
    val tags: StateFlow<List<Tag>> = recipes.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) {
        viewModelScope.launch { recipes.createTag(name) }
    }

    fun rename(tag: Tag, name: String) {
        viewModelScope.launch { recipes.renameTag(tag.id, name) }
    }

    fun delete(tag: Tag) {
        viewModelScope.launch { recipes.deleteTag(tag.id) }
    }

    fun reorder(tags: List<Tag>) {
        viewModelScope.launch { recipes.reorderTags(tags.map { it.id }) }
    }
}

/** タグの管理（F-ST-01）。長押しのつまみで並べ替える。 */
@Composable
fun TagsRoute(onBack: () -> Unit) {
    val viewModel = appViewModel { TagsViewModel(it.data.recipes) }
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val reorder = rememberReorderState(tags, key = { it.id }, onCommit = viewModel::reorder)
    var renaming by remember { mutableStateOf<Tag?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "タグの管理",
            onBack = onBack,
            actions = { AppIconButton(AppIcons.Plus, "タグを作る", { creating = true }) },
        )
        if (tags.isEmpty()) {
            Text(
                "タグはまだありません。右上の ＋ か、レシピを直す画面から作れます。",
                style = AppTheme.typography.body,
                color = AppTheme.colors.sub,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(state = reorder.listState, modifier = Modifier.weight(1f)) {
            items(reorder.items, key = { it.id }) { tag ->
                ReorderableRow(reorder, key = tag.id) { handle, _ ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            ReorderHandle(modifier = handle)
                            Text(tag.name, style = AppTheme.typography.bodyStrong, color = AppTheme.colors.fg, modifier = Modifier.weight(1f))
                            AppIconButton(AppIcons.Edit, "名前を変える", { renaming = tag }, tint = AppTheme.colors.sub)
                            AppIconButton(AppIcons.Trash, "削除", { deleting = tag }, tint = AppTheme.colors.sub)
                        }
                        AppDivider()
                    }
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "タグを作る",
            initialText = "",
            label = "タグの名前",
            onConfirm = {
                creating = false
                viewModel.create(it)
            },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { tag ->
        TextInputDialog(
            title = "名前を変える",
            initialText = tag.name,
            label = "タグの名前",
            onConfirm = {
                renaming = null
                viewModel.rename(tag, it)
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { tag ->
        ConfirmDialog(
            title = "「${tag.name}」を削除しますか？",
            message = "レシピからも、このタグが外れます（レシピは消えません）。",
            confirmText = "削除",
            destructive = true,
            onConfirm = {
                deleting = null
                viewModel.delete(tag)
            },
            onDismiss = { deleting = null },
        )
    }
}
