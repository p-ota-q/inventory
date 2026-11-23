package io.github.otato.recipebook.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import io.github.otato.recipebook.domain.model.Rating
import io.github.otato.recipebook.domain.model.RecipeStatus
import io.github.otato.recipebook.domain.model.SourceKind
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 画面に出す言葉。 */
object Labels {
    fun status(status: RecipeStatus): String = when (status) {
        RecipeStatus.Want -> "気になる"
        RecipeStatus.Made -> "作った"
        RecipeStatus.Staple -> "定番"
    }

    fun rating(rating: Rating): String = when (rating) {
        Rating.Again -> "また作りたい"
        Rating.Okay -> "ふつう"
        Rating.Meh -> "いまいち"
    }

    fun sourceKind(kind: SourceKind): String = when (kind) {
        SourceKind.YouTube -> "YouTube"
        SourceKind.Web -> "Web"
        SourceKind.Ai -> "AI"
        SourceKind.Book -> "本・テレビ"
        SourceKind.Person -> "人から"
        SourceKind.Own -> "自分で考えた"
    }
}

private val JA = Locale.JAPANESE

/** 「11/20（金）」。今年でなければ「2025/11/20（木）」。 */
fun formatDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, JA)
    return if (date.year == today.year) "${date.monthValue}/${date.dayOfMonth}（$dow）"
    else "${date.year}/${date.monthValue}/${date.dayOfMonth}（$dow）"
}

/** 「11/20」。今年でなければ「2025/11/20」。 */
fun formatShortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    if (date.year == today.year) "${date.monthValue}/${date.dayOfMonth}" else "${date.year}/${date.monthValue}/${date.dayOfMonth}"

/** URL を、ブラウザや YouTube のアプリで開く（F-RC-02）。 */
fun openUrl(context: Context, url: String) {
    val fixed = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, fixed.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "開けるアプリが見つかりませんでした", Toast.LENGTH_SHORT).show()
    }
}

/** クリップボードの文字（なければ null）。 */
fun readClipboard(context: Context): String? {
    val manager = context.getSystemService(android.content.ClipboardManager::class.java) ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
}
