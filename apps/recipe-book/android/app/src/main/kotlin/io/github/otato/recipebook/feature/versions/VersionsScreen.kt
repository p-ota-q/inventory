package io.github.otato.recipebook.feature.versions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.otato.recipebook.designsystem.components.AppCard
import io.github.otato.recipebook.designsystem.components.AppTopBar
import io.github.otato.recipebook.designsystem.components.ConfirmDialog
import io.github.otato.recipebook.designsystem.components.SectionLabel
import io.github.otato.recipebook.designsystem.components.TextAction
import io.github.otato.recipebook.designsystem.theme.AppTheme
import io.github.otato.recipebook.domain.model.Author
import io.github.otato.recipebook.domain.model.RecipeVersion
import io.github.otato.recipebook.domain.repository.RecipeRepository
import io.github.otato.recipebook.feature.detail.IngredientList
import io.github.otato.recipebook.feature.detail.StepList
import io.github.otato.recipebook.ui.common.appViewModel
import io.github.otato.recipebook.ui.common.formatDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

data class VersionsUiState(
    val versions: List<RecipeVersion> = emptyList(),
    val currentVersionId: String? = null,
)

class VersionsViewModel(private val recipeId: String, private val recipes: RecipeRepository) : ViewModel() {
    val state: StateFlow<VersionsUiState> = combine(recipes.observeVersions(recipeId), recipes.observeDetail(recipeId)) { versions, detail ->
        VersionsUiState(versions, detail?.recipe?.currentVersion?.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VersionsUiState())

    fun restore(versionId: String) {
        viewModelScope.launch { recipes.restoreVersion(recipeId, versionId) }
    }
}

/** 版の履歴（F-RC-07）。版ごとに、いつ・なぜ変えたかと、中身を見られる。前の版に戻せる（戻した内容は、新しい版になる）。 */
@Composable
fun VersionsRoute(recipeId: String, onBack: () -> Unit) {
    val viewModel = appViewModel(key = "versions-$recipeId") { VersionsViewModel(recipeId, it.data.recipes) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openId by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<RecipeVersion?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "版の履歴", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.versions, key = { it.id }) { version ->
                val current = version.id == state.currentVersionId
                val open = openId == version.id
                AppCard(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { openId = if (open) null else version.id }) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(versionTitle(version, current), style = AppTheme.typography.bodyStrong, color = AppTheme.colors.fg, modifier = Modifier.weight(1f))
                            Text(
                                formatDate(version.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()),
                                style = AppTheme.typography.caption,
                                color = AppTheme.colors.sub,
                            )
                        }
                        if (version.reason.isNotBlank()) Text(version.reason, style = AppTheme.typography.body, color = AppTheme.colors.fg)
                        if (version.author == Author.Gemini) Text("Gemini から", style = AppTheme.typography.caption, color = AppTheme.colors.sub)
                        if (open) {
                            SectionLabel("材料", modifier = Modifier.padding(top = 8.dp))
                            IngredientList(version.content.ingredients)
                            SectionLabel("作り方", modifier = Modifier.padding(top = 12.dp))
                            StepList(version.content.steps, modifier = Modifier.padding(top = 4.dp))
                            if (version.content.tips.isNotBlank()) {
                                SectionLabel("自分のポイント", modifier = Modifier.padding(top = 12.dp))
                                Text(version.content.tips, style = AppTheme.typography.body, color = AppTheme.colors.fg)
                            }
                            if (!current) TextAction("この版に戻す", { confirm = version })
                        } else {
                            Text("押すと中身を見られます", style = AppTheme.typography.caption, color = AppTheme.colors.sub)
                        }
                    }
                }
            }
        }
    }

    confirm?.let { version ->
        ConfirmDialog(
            title = "版 ${version.number} に戻しますか？",
            message = "戻した内容を、新しい版として保存します。いまの版も、履歴に残ります。",
            confirmText = "戻す",
            onConfirm = {
                confirm = null
                viewModel.restore(version.id)
            },
            onDismiss = { confirm = null },
        )
    }
}

private fun versionTitle(version: RecipeVersion, current: Boolean): String = buildString {
    append("版 ${version.number}")
    val marks = listOfNotNull(if (current) "いま" else null, if (version.isOriginal) "元のレシピ" else null, if (version.fromCookLogId != null) "作った記録から" else null)
    if (marks.isNotEmpty()) append("（${marks.joinToString("・")}）")
}
