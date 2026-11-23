package io.github.otato.recipebook.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 文字のスタイル（大きさは training-log と同じ）。フォントは端末の標準（Roboto / Noto Sans JP）。
 * 文字の大きさは sp で決めるので、端末の「文字サイズ」の設定に追従する。
 */
@Immutable
data class AppTypography(
    /** アプリバーの題名 */
    val title: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    /** 見出し（カードの題名・料理名） */
    val heading: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp),
    val body: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    val bodyStrong: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    /** 小さな見出し（セクションの名前） */
    val label: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, lineHeight = 17.sp),
    /** ボタンの文字 */
    val button: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    /** キッチン向けの表示の本文（離れて見ても読める大きさ。F-CK-01） */
    val kitchenBody: TextStyle = TextStyle(fontSize = 20.sp, lineHeight = 30.sp),
    /** キッチン向けの表示の見出し */
    val kitchenHeading: TextStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
)

/** Material3 の部品（スイッチ・ダイアログなど）の文字も、同じ大きさにそろえる。 */
internal fun AppTypography.toMaterial(): Typography = Typography(
    titleLarge = title,
    titleMedium = heading,
    bodyLarge = bodyStrong,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = button.copy(fontSize = 14.sp),
    labelMedium = label,
    labelSmall = caption,
)
