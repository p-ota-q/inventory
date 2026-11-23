package io.github.otato.recipebook.feature.cooklog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.otato.recipebook.designsystem.components.AppChip
import io.github.otato.recipebook.designsystem.components.AppDivider
import io.github.otato.recipebook.designsystem.components.AppIconButton
import io.github.otato.recipebook.designsystem.components.AppTopBar
import io.github.otato.recipebook.designsystem.components.ConfirmDialog
import io.github.otato.recipebook.designsystem.components.PrimaryButton
import io.github.otato.recipebook.designsystem.components.SectionLabel
import io.github.otato.recipebook.designsystem.components.TextAction
import io.github.otato.recipebook.designsystem.components.dismissKeyboardOnTouch
import io.github.otato.recipebook.designsystem.icons.AppIcons
import io.github.otato.recipebook.designsystem.theme.AppTheme
import io.github.otato.recipebook.domain.model.Rating
import io.github.otato.recipebook.ui.common.Labels
import io.github.otato.recipebook.ui.common.appViewModel
import io.github.otato.recipebook.ui.common.formatDate
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookLogRoute(
    recipeId: String,
    logId: String?,
    onBack: () -> Unit,
    onReflect: (logId: String) -> Unit,
) {
    val viewModel = appViewModel(key = "cooklog-$recipeId-$logId") { CookLogViewModel(recipeId, logId, it.data.recipes, it.clock) }
    var pickDate by remember { mutableStateOf(false) }
    var askReflect by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (viewModel.loading) return
    val input = viewModel.input
    val colors = AppTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = if (viewModel.isNew) "作った記録" else "作った記録を直す",
            onBack = onBack,
            actions = {
                if (!viewModel.isNew) AppIconButton(AppIcons.Trash, "この記録を削除", { confirmDelete = true }, tint = colors.error)
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .dismissKeyboardOnTouch()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(viewModel.recipeTitle, style = AppTheme.typography.heading, color = colors.fg)
            viewModel.previousNote?.let { log ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.note)
                        .padding(12.dp),
                ) {
                    Text("前回のメモ（${formatDate(log.cookedOn)}）", style = AppTheme.typography.label, color = colors.onNote)
                    Text(log.nextNote, style = AppTheme.typography.body, color = colors.onNote)
                }
            }

            SectionLabel("作った日", modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(input.cookedOn), style = AppTheme.typography.bodyStrong, color = colors.fg, modifier = Modifier.weight(1f))
                TextAction("日付を変える", { pickDate = true })
            }

            SectionLabel("評価", modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Rating.entries.forEach { rating ->
                    AppChip(
                        text = Labels.rating(rating),
                        selected = input.rating == rating,
                        onClick = { viewModel.toggleRating(rating) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            MemoField("感想", input.impression, "例: 少ししょっぱかった。子どもがよく食べた", viewModel::setImpression)
            MemoField("変えたところ（アレンジ）", input.changes, "例: 塩を小さじ1/3にした。きのこを足した", viewModel::setChanges)
            MemoField("次に作るときのメモ", input.nextNote, "例: 片栗粉を多めに。長ねぎを 1.5 本に", viewModel::setNextNote)
            Text(
                "評価だけでも記録になります。「次に作るときのメモ」は、次にレシピを開いたとき、いちばん上に出ます。",
                style = AppTheme.typography.caption,
                color = colors.sub,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
            AppDivider()
            PrimaryButton(
                "保存",
                {
                    viewModel.save { id, ask -> if (ask) askReflect = id else onBack() }
                },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = input.cookedOn.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDate = false
                }) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("キャンセル") } },
        ) {
            DatePicker(state = state)
        }
    }

    askReflect?.let { id ->
        ConfirmDialog(
            title = "レシピに反映しますか？",
            message = "「変えたところ」に合わせて、材料や作り方を直せます。直すと新しい版になり、前の版も残ります。",
            confirmText = "反映する",
            dismissText = "あとで",
            onConfirm = {
                askReflect = null
                onReflect(id)
            },
            onDismiss = {
                askReflect = null
                onBack()
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "この記録を削除しますか？",
            message = "削除した記録は、元に戻せません。",
            confirmText = "削除",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun MemoField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        minLines = 2,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )
}

