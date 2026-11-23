package io.github.otato.recipebook.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.otato.recipebook.designsystem.components.AppDivider
import io.github.otato.recipebook.designsystem.theme.AppTheme
import io.github.otato.recipebook.domain.logic.Versioning
import io.github.otato.recipebook.domain.model.Ingredient

/** 材料の一覧（名前と分量）。見出し（「A」「タレ」）は太字。 */
@Composable
fun IngredientList(ingredients: List<Ingredient>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ingredients.forEachIndexed { index, item ->
            if (item.isHeading) {
                Text(
                    item.name,
                    style = AppTheme.typography.bodyStrong,
                    color = AppTheme.colors.primary,
                    modifier = Modifier.padding(top = if (index == 0) 4.dp else 12.dp, bottom = 2.dp),
                )
            } else {
                IngredientRow(name = item.name, amount = item.amount)
                AppDivider()
            }
        }
    }
}

@Composable
private fun IngredientRow(name: String, amount: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(name, style = AppTheme.typography.body, color = AppTheme.colors.fg, modifier = Modifier.weight(1f))
        Text(
            amount,
            style = AppTheme.typography.body,
            color = AppTheme.colors.fg,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 180.dp),
        )
    }
}

/** 元のレシピといまの材料の違い（F-RC-08）。変わった分量は「前 → いま」、足したものは「＋」、消したものは取り消し線。 */
@Composable
fun IngredientDiffList(changes: List<Versioning.IngredientChange>, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(modifier = modifier) {
        changes.forEach { change ->
            val item = change.ingredient
            if (item.isHeading) {
                Text(item.name, style = AppTheme.typography.bodyStrong, color = colors.primary, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                return@forEach
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val nameText = buildAnnotatedString {
                    when (change) {
                        is Versioning.IngredientChange.Added -> {
                            withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) { append("＋ ") }
                            append(item.name)
                        }
                        is Versioning.IngredientChange.Removed ->
                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = colors.sub)) { append(item.name) }
                        else -> append(item.name)
                    }
                }
                Text(nameText, style = AppTheme.typography.body, color = colors.fg, modifier = Modifier.weight(1f))
                val amountText = buildAnnotatedString {
                    when (change) {
                        is Versioning.IngredientChange.Changed -> {
                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = colors.sub)) { append(change.before) }
                            append(" → ")
                            withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) { append(item.amount) }
                        }
                        is Versioning.IngredientChange.Removed ->
                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = colors.sub)) { append(item.amount) }
                        is Versioning.IngredientChange.Added ->
                            withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) { append(item.amount) }
                        is Versioning.IngredientChange.Same -> append(item.amount)
                    }
                }
                Text(amountText, style = AppTheme.typography.body, color = colors.fg, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 200.dp))
            }
            AppDivider()
        }
    }
}

/** 番号付きの手順。 */
@Composable
fun StepList(steps: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEachIndexed { index, text ->
            Row {
                Text(
                    "${index + 1}",
                    style = AppTheme.typography.bodyStrong,
                    color = AppTheme.colors.primary,
                    modifier = Modifier.width(28.dp),
                )
                Text(text, style = AppTheme.typography.body, color = AppTheme.colors.fg, modifier = Modifier.weight(1f))
            }
        }
    }
}
