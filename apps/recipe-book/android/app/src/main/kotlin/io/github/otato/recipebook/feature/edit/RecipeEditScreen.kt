package io.github.otato.recipebook.feature.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otato.recipebook.designsystem.components.AppChip
import io.github.otato.recipebook.designsystem.components.AppDivider
import io.github.otato.recipebook.designsystem.components.AppIconButton
import io.github.otato.recipebook.designsystem.components.AppTopBar
import io.github.otato.recipebook.designsystem.components.ConfirmDialog
import io.github.otato.recipebook.designsystem.components.PrimaryButton
import io.github.otato.recipebook.designsystem.components.ReorderHandle
import io.github.otato.recipebook.designsystem.components.SectionLabel
import io.github.otato.recipebook.designsystem.components.TextAction
import io.github.otato.recipebook.designsystem.components.TextInputDialog
import io.github.otato.recipebook.designsystem.components.dismissKeyboardOnTouch
import io.github.otato.recipebook.designsystem.icons.AppIcons
import io.github.otato.recipebook.designsystem.theme.AppTheme
import io.github.otato.recipebook.domain.model.RecipeStatus
import io.github.otato.recipebook.domain.model.SourceKind
import io.github.otato.recipebook.ui.common.Labels
import io.github.otato.recipebook.ui.common.appViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RecipeEditRoute(
    recipeId: String?,
    from: String,
    logId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val viewModel = appViewModel(key = "edit-$recipeId-$from-$logId") {
        RecipeEditViewModel(recipeId, from, logId, it.data.recipes, it.pendingImport)
    }
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var newTagDialog by remember { mutableStateOf(false) }

    val leave = { if (viewModel.isDirty) confirmLeave = true else onBack() }
    BackHandler(onBack = leave)

    if (viewModel.loading) return
    val form = viewModel.form
    // 作った記録から反映するときは、材料から見せる（料理名・出典は、ふつう直さないので）
    val reflecting = from == RecipeEditViewModel.FROM_LOG
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (reflecting) 2 + form.sources.size else 0)
    val reorder = rememberReorderableLazyListState(listState) { from, to -> viewModel.move(from.key, to.key) }
    val colors = AppTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = if (viewModel.isNew) "レシピを書く" else "レシピを直す", onBack = leave)
        // 反映するときの「変えたところ」は、直している間ずっと見えるように、一覧の外（上）に置く
        viewModel.reflectNote?.let { note ->
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                Banner("作った記録の「変えたところ」: $note\n直して保存すると新しい版になります（前の版も残ります）")
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).dismissKeyboardOnTouch(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            viewModel.importInfo?.let { info ->
                item(key = "import") { ImportBanner(info) }
            }
            item(key = "title") {
                OutlinedTextField(
                    value = form.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("料理名") },
                    isError = viewModel.titleError,
                    supportingText = if (viewModel.titleError) {
                        { Text("料理名を入れてください") }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecipeStatus.entries.forEach { status ->
                        AppChip(
                            text = Labels.status(status),
                            selected = form.status == status,
                            onClick = { viewModel.setStatus(status) },
                            icon = if (status == RecipeStatus.Staple) AppIcons.Star else null,
                        )
                    }
                }
            }

            // 出典
            item(key = "sources-title") { SectionTitle("出典") { TextAction("＋ 出典", viewModel::addSource) } }
            items(form.sources, key = { "src-${it.key}" }) { source ->
                SourceEditor(
                    source = source,
                    onKind = { viewModel.updateSource(source.key, kind = it) },
                    onUrl = { viewModel.updateSource(source.key, url = it) },
                    onTitle = { viewModel.updateSource(source.key, title = it) },
                    onRemove = { viewModel.removeSource(source.key) },
                )
            }

            // 材料
            item(key = "ingredients-title") {
                SectionTitle("材料")
                OutlinedTextField(
                    value = form.servings,
                    onValueChange = viewModel::setServings,
                    label = { Text("何人分（例: 2人分）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            items(form.ingredients, key = { "${RecipeEditViewModel.ING}${it.key}" }) { item ->
                ReorderableItem(reorder, key = "${RecipeEditViewModel.ING}${item.key}") { dragging ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(if (dragging) colors.surface else colors.bg).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReorderHandle(modifier = Modifier.draggableHandle())
                        if (item.isHeading) {
                            OutlinedTextField(
                                value = item.name,
                                onValueChange = { viewModel.updateIngredient(item.key, name = it) },
                                placeholder = { Text("見出し（例: A・タレ）") },
                                textStyle = AppTheme.typography.bodyStrong.copy(color = colors.primary),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            OutlinedTextField(
                                value = item.name,
                                onValueChange = { viewModel.updateIngredient(item.key, name = it) },
                                placeholder = { Text("材料") },
                                singleLine = true,
                                modifier = Modifier.weight(1.15f),
                            )
                            Spacer(Modifier.width(6.dp))
                            OutlinedTextField(
                                value = item.amount,
                                onValueChange = { viewModel.updateIngredient(item.key, amount = it) },
                                placeholder = { Text("分量") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        RowMenu(
                            items = listOf(
                                (if (item.isHeading) "材料の行にする" else "見出しにする") to { viewModel.toggleHeading(item.key) },
                                "削除" to { viewModel.removeIngredient(item.key) },
                            ),
                        )
                    }
                }
            }
            item(key = "ingredients-add") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextAction("＋ 材料", { viewModel.addIngredient() })
                    TextAction("＋ 見出し", { viewModel.addIngredient(heading = true) })
                }
            }

            // 作り方
            item(key = "steps-title") { SectionTitle("作り方") }
            items(form.steps, key = { "${RecipeEditViewModel.STEP}${it.key}" }) { step ->
                val number = form.steps.indexOfFirst { it.key == step.key } + 1
                ReorderableItem(reorder, key = "${RecipeEditViewModel.STEP}${step.key}") { dragging ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(if (dragging) colors.surface else colors.bg).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReorderHandle(modifier = Modifier.draggableHandle())
                        Text("$number", style = AppTheme.typography.bodyStrong, color = colors.primary, modifier = Modifier.width(24.dp))
                        OutlinedTextField(
                            value = step.text,
                            onValueChange = { viewModel.updateStep(step.key, it) },
                            placeholder = { Text("手順") },
                            minLines = 1,
                            maxLines = 6,
                            modifier = Modifier.weight(1f),
                        )
                        AppIconButton(AppIcons.Close, "この手順を削除", { viewModel.removeStep(step.key) }, tint = colors.sub)
                    }
                }
            }
            item(key = "steps-add") { TextAction("＋ 手順", viewModel::addStep) }

            // ポイント・タグ・変えた理由
            item(key = "tips") {
                SectionTitle("自分のポイント")
                OutlinedTextField(
                    value = form.tips,
                    onValueChange = viewModel::setTips,
                    placeholder = { Text("コツや注意（例: 片栗粉は多めに）") },
                    minLines = 2,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "tags") {
                SectionTitle("タグ") { TextAction("＋ タグ", { newTagDialog = true }) }
                if (tags.isEmpty()) {
                    Text("「鶏肉」「10分」「作り置き」など、探しやすくする印を付けられます", style = AppTheme.typography.caption, color = colors.sub)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            AppChip(
                                text = tag.name,
                                selected = tag.id in form.tagIds,
                                onClick = { viewModel.toggleTag(tag.id) },
                                icon = if (tag.id in form.tagIds) AppIcons.Check else null,
                            )
                        }
                    }
                }
            }
            if (viewModel.willMakeVersion) {
                item(key = "reason") {
                    SectionTitle("変えた理由（新しい版に残ります）")
                    OutlinedTextField(
                        value = form.reason,
                        onValueChange = viewModel::setReason,
                        placeholder = { Text("例: しょっぱかったので、塩を減らした") },
                        minLines = 1,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        // 保存は、画面の下に固定する
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
            AppDivider()
            PrimaryButton("保存", { viewModel.save(onSaved) }, modifier = Modifier.fillMaxWidth().padding(12.dp))
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "保存せずに戻りますか？",
            message = "入力した内容は消えます。",
            confirmText = "保存せずに戻る",
            destructive = true,
            onConfirm = {
                confirmLeave = false
                onBack()
            },
            onDismiss = { confirmLeave = false },
        )
    }
    if (newTagDialog) {
        TextInputDialog(
            title = "タグを作る",
            initialText = "",
            label = "タグの名前（例: 鶏肉）",
            onConfirm = {
                newTagDialog = false
                viewModel.createTag(it)
            },
            onDismiss = { newTagDialog = false },
        )
    }
}

@Composable
private fun SectionTitle(title: String, trailing: @Composable () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Banner(text: String) {
    Text(
        text,
        style = AppTheme.typography.body,
        color = AppTheme.colors.onContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.container)
            .padding(12.dp),
    )
}

@Composable
private fun ImportBanner(info: ImportInfo) {
    var open by remember { mutableStateOf(false) }
    val message = if (info.ingredientCount + info.stepCount > 0) {
        "文章から、材料 ${info.ingredientCount} 件・作り方 ${info.stepCount} 件を入れました。違っていたら直してください。"
    } else {
        "材料と作り方に分けられませんでした。下に入れてください。取り込んだ文章は、そのまま残します。"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.container)
            .padding(12.dp),
    ) {
        Text(message, style = AppTheme.typography.body, color = AppTheme.colors.onContainer)
        val rest = info.leftover.ifBlank { null } ?: if (info.ingredientCount + info.stepCount == 0) info.rawText else null
        if (rest != null) {
            TextAction(if (open) "分けられなかった文章をとじる" else "分けられなかった文章を見る", { open = !open }, color = AppTheme.colors.onContainer)
            if (open) Text(rest, style = AppTheme.typography.caption, color = AppTheme.colors.onContainer)
        }
    }
}

@Composable
private fun SourceEditor(
    source: SourceField,
    onKind: (SourceKind) -> Unit,
    onUrl: (String) -> Unit,
    onTitle: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SourceKind.entries.forEach { kind ->
                    AppChip(text = Labels.sourceKind(kind), selected = source.kind == kind, onClick = { onKind(kind) })
                }
            }
            AppIconButton(AppIcons.Close, "この出典を削除", onRemove, tint = AppTheme.colors.sub)
        }
        OutlinedTextField(
            value = source.title,
            onValueChange = onTitle,
            placeholder = { Text("題名・メモ（例: 〇〇さんの動画）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        OutlinedTextField(
            value = source.url,
            onValueChange = onUrl,
            placeholder = { Text("URL（あれば）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

/** 行の右端の ⋮ メニュー。 */
@Composable
private fun RowMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppIconButton(AppIcons.More, "この行の操作", { open = true }, tint = AppTheme.colors.sub)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = FontWeight.Normal) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}
