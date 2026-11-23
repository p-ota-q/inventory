package io.github.otato.recipebook.feature.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otato.recipebook.designsystem.components.AppDivider
import io.github.otato.recipebook.designsystem.components.AppTopBar
import io.github.otato.recipebook.designsystem.components.PrimaryButton
import io.github.otato.recipebook.designsystem.components.TextAction
import io.github.otato.recipebook.designsystem.components.TonalButton
import io.github.otato.recipebook.designsystem.icons.AppIcons
import io.github.otato.recipebook.designsystem.theme.AppTheme
import io.github.otato.recipebook.domain.model.RecipeDetail
import io.github.otato.recipebook.feature.detail.RecipeDetailViewModel
import io.github.otato.recipebook.ui.common.appViewModel
import io.github.otato.recipebook.ui.common.openUrl

/**
 * キッチン向けの表示（4.3）。文字を大きくし、表示している間は画面を消さない。
 * 材料の「用意した」と手順の「終わった」のチェックは、この回だけ（レシピには残さない。F-CK-03）。
 */
@Composable
fun KitchenRoute(
    recipeId: String,
    onClose: () -> Unit,
    onFinished: () -> Unit,
) {
    val viewModel = appViewModel(key = "detail-$recipeId") { RecipeDetailViewModel(recipeId, it.data.recipes, it.deletedRecipe) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 表示している間は、画面を消さない（F-CK-01）
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val detail = state.detail ?: return
    KitchenScreen(detail, onClose, onFinished)
}

@Composable
private fun KitchenScreen(detail: RecipeDetail, onClose: () -> Unit, onFinished: () -> Unit) {
    val content = detail.recipe.content
    val colors = AppTheme.colors
    val type = AppTheme.typography
    val context = LocalContext.current
    var checkedIngredients by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var doneSteps by rememberSaveable { mutableStateOf(setOf<Int>()) }
    val sourceUrl = detail.recipe.sources.firstOrNull { it.url != null }?.url

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = detail.recipe.title,
            onBack = onClose,
            actions = { if (sourceUrl != null) TextAction("元の動画・ページ", { openUrl(context, sourceUrl) }) },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            // 前回の反省と、自分のポイント（F-CK-02）
            val notes = listOfNotNull(
                detail.latestNextNote?.nextNote?.let { "前回のメモ: $it" },
                content.tips.ifBlank { null }?.let { "ポイント: $it" },
            )
            if (notes.isNotEmpty()) {
                item(key = "notes") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.note)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        notes.forEach { Text(it, style = type.kitchenBody, color = colors.onNote) }
                    }
                }
            }
            item(key = "ingredients-title") {
                Heading(if (content.servings.isNotBlank()) "材料（${content.servings}）" else "材料")
            }
            itemsIndexed(content.ingredients, key = { i, _ -> "ing-$i" }) { index, item ->
                if (item.isHeading) {
                    Text(item.name, style = type.kitchenHeading, color = colors.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                } else {
                    val checked = index in checkedIngredients
                    CheckRow(
                        checked = checked,
                        onToggle = { checkedIngredients = if (checked) checkedIngredients - index else checkedIngredients + index },
                    ) {
                        Text(
                            item.name,
                            style = type.kitchenBody,
                            color = colors.fg,
                            textDecoration = if (checked) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            item.amount,
                            style = type.kitchenBody,
                            color = colors.fg,
                            textDecoration = if (checked) TextDecoration.LineThrough else null,
                        )
                    }
                }
            }
            item(key = "steps-title") { Heading("作り方") }
            itemsIndexed(content.steps, key = { i, _ -> "step-$i" }) { index, text ->
                val done = index in doneSteps
                CheckRow(
                    checked = done,
                    onToggle = { doneSteps = if (done) doneSteps - index else doneSteps + index },
                    modifier = Modifier.alpha(if (done) 0.45f else 1f),
                ) {
                    Text("${index + 1}", style = type.kitchenHeading, color = colors.primary, modifier = Modifier.width(36.dp))
                    Text(text, style = type.kitchenBody, color = colors.fg, modifier = Modifier.weight(1f))
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
            AppDivider()
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TonalButton("とじる", onClose, modifier = Modifier.weight(1f))
                PrimaryButton("作り終えた", onFinished, icon = AppIcons.Check, modifier = Modifier.weight(1.4f))
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = AppTheme.typography.kitchenHeading,
        color = AppTheme.colors.fg,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

/** 行のどこを押しても、チェックが切り替わる（濡れた指でも押しやすいように、行全体を大きく）。 */
@Composable
private fun CheckRow(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val colors = AppTheme.colors
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.primary else colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Icon(AppIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
            }
            content()
        }
        AppDivider()
    }
}
