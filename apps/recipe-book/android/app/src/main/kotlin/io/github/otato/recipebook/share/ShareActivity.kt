package io.github.otato.recipebook.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.otato.recipebook.MainActivity
import io.github.otato.recipebook.RecipeBookApplication
import io.github.otato.recipebook.designsystem.components.AppChip
import io.github.otato.recipebook.designsystem.components.OutlineButton
import io.github.otato.recipebook.designsystem.components.PrimaryButton
import io.github.otato.recipebook.designsystem.components.TextAction
import io.github.otato.recipebook.designsystem.theme.AppTheme
import io.github.otato.recipebook.designsystem.theme.RecipeBookTheme
import io.github.otato.recipebook.domain.logic.SharedContent
import io.github.otato.recipebook.domain.logic.SharedContentReader
import io.github.otato.recipebook.domain.model.AppSettings
import io.github.otato.recipebook.domain.model.RecipeContent
import io.github.otato.recipebook.domain.model.RecipeDraft
import io.github.otato.recipebook.domain.model.RecipeSummary
import io.github.otato.recipebook.domain.model.SourceInput
import io.github.otato.recipebook.domain.model.SourceKind
import io.github.otato.recipebook.domain.model.ThemeMode
import io.github.otato.recipebook.ui.common.Labels
import kotlinx.coroutines.launch

/**
 * ほかのアプリの「共有」から、URL や文章を受け取る（F-CL-01〜03）。
 * 元のアプリの上に、下から小さなパネルを出す。料理名を確かめて保存したら、元のアプリに戻る。
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent?.getStringExtra(Intent.EXTRA_TITLE)
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "受け取れる文字がありませんでした", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val shared = SharedContentReader.read(text, subject)
        setContent { ShareRoot(shared) }
    }

    @Composable
    private fun ShareRoot(shared: SharedContent) {
        val container = (application as RecipeBookApplication).container
        val settings by remember { container.data.settings.observe() }.collectAsStateWithLifecycle(AppSettings())
        val dark = when (settings.themeMode) {
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
            ThemeMode.System -> null
        }
        var duplicate by remember { mutableStateOf<RecipeSummary?>(null) }
        LaunchedEffect(shared.url) {
            shared.url?.let { duplicate = container.data.recipes.findByUrl(it) }
        }
        val scope = rememberCoroutineScope()

        RecipeBookTheme(darkTheme = dark) {
            SharePanel(
                shared = shared,
                duplicate = duplicate,
                onCancel = { finish() },
                onOpenDuplicate = { id ->
                    startActivity(MainActivity.openRecipeIntent(this, id))
                    finish()
                },
                onSave = { title, kind, open ->
                    scope.launch {
                        val parsed = shared.parsed
                        val id = container.data.recipes.create(
                            RecipeDraft(
                                title = title,
                                content = parsed?.content ?: RecipeContent(),
                                sources = listOf(
                                    SourceInput(
                                        kind = kind,
                                        url = shared.url,
                                        // URL の題名（料理名と違うことが多い）。URL がなければ、出どころの名前
                                        title = if (shared.url != null) shared.title else if (kind == SourceKind.Ai) "AI の回答" else "",
                                    ),
                                ),
                                // 文章を受け取ったときは、そのまま残す（F-CL-05）
                                importedText = if (parsed != null) shared.rawText else null,
                            ),
                        )
                        if (open) {
                            startActivity(MainActivity.openRecipeIntent(this@ShareActivity, id))
                        } else {
                            Toast.makeText(this@ShareActivity, "「$title」をレシピ帳に入れました", Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                },
            )
        }
    }
}

@Composable
private fun SharePanel(
    shared: SharedContent,
    duplicate: RecipeSummary?,
    onCancel: () -> Unit,
    onOpenDuplicate: (String) -> Unit,
    onSave: (title: String, kind: SourceKind, open: Boolean) -> Unit,
) {
    val colors = AppTheme.colors
    var title by remember { mutableStateOf(shared.title) }
    var kind by remember { mutableStateOf(shared.kind) }
    var titleError by remember { mutableStateOf(false) }
    val save = { open: Boolean ->
        if (title.isBlank()) titleError = true else onSave(title.trim(), kind, open)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.surface)
                // パネルの中を押しても、閉じないようにする
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("レシピ帳に追加", style = AppTheme.typography.title, color = colors.fg)
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) titleError = false
                },
                label = { Text("料理名") },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("料理名を入れてください") }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceKind.entries.forEach { k ->
                    AppChip(text = Labels.sourceKind(k), selected = kind == k, onClick = { kind = k })
                }
            }
            shared.url?.let {
                Text(it, style = AppTheme.typography.caption, color = colors.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            shared.parsed?.let { parsed ->
                val ingredients = parsed.content.ingredients.count { !it.isHeading }
                val steps = parsed.content.steps.size
                Text(
                    if (ingredients + steps > 0) "材料 $ingredients 件・作り方 $steps 件も入ります" else "受け取った文章は、レシピの中に残します",
                    style = AppTheme.typography.body,
                    color = colors.fg,
                )
            }
            duplicate?.let { dup ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.note)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("同じ URL のレシピがあります:「${dup.title}」", style = AppTheme.typography.body, color = colors.onNote)
                    TextAction("そのレシピを開く", { onOpenDuplicate(dup.id) }, color = colors.onNote)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("保存して開く", { save(true) }, modifier = Modifier.weight(1f))
                PrimaryButton("保存", { save(false) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
