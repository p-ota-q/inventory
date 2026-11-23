package io.github.otato.recipebook.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 色のトークン。役割の名前は training-log と同じにし、値だけ、料理のアプリらしい暖かい色にした。
 * 色は、画面のコードに直接書かず、必ずここから取る。
 */
@Immutable
data class AppColors(
    /** 画面の背景 */
    val bg: Color,
    /** カード・パネルの面 */
    val surface: Color,
    /** 一段くぼんだ面（入力欄の背景など） */
    val surfaceVariant: Color,
    /** 文字 */
    val fg: Color,
    /** 補助の文字 */
    val sub: Color,
    /** 主役の色（主ボタン） */
    val primary: Color,
    val onPrimary: Color,
    /** 選択中などの、淡い面 */
    val container: Color,
    val onContainer: Color,
    /** 区切り線・枠 */
    val line: Color,
    val error: Color,
    /** 逆色の面（スナックバー） */
    val inverse: Color,
    val onInverse: Color,
    val inverseAction: Color,
    /** 「定番」と「また作りたい」の印 */
    val accent: Color,
    /** 「次に作るときのメモ」の面（目立たせる） */
    val note: Color,
    val onNote: Color,
    val isDark: Boolean,
)

val LightAppColors = AppColors(
    bg = Color(0xFFF8F5F2),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEFE8E2),
    fg = Color(0xFF1F1A17),
    sub = Color(0xFF5C534D),
    primary = Color(0xFF8A3B12),
    onPrimary = Color(0xFFFFFFFF),
    container = Color(0xFFF8DFD1),
    onContainer = Color(0xFF3A1606),
    line = Color(0xFFD8CEC6),
    error = Color(0xFFBA1A1A),
    inverse = Color(0xFF362F2B),
    onInverse = Color(0xFFF8EFEA),
    inverseAction = Color(0xFFFFB68F),
    accent = Color(0xFFB26A00),
    note = Color(0xFFFFF1C7),
    onNote = Color(0xFF3D2E00),
    isDark = false,
)

val DarkAppColors = AppColors(
    bg = Color(0xFF15110F),
    surface = Color(0xFF1E1916),
    surfaceVariant = Color(0xFF2A231F),
    fg = Color(0xFFEEE6E1),
    sub = Color(0xFFB3A79F),
    primary = Color(0xFFFFB68F),
    onPrimary = Color(0xFF4F2005),
    container = Color(0xFF5A2A12),
    onContainer = Color(0xFFFFDBCB),
    line = Color(0xFF463B35),
    error = Color(0xFFFFB4AB),
    inverse = Color(0xFFEEE6E1),
    onInverse = Color(0xFF1F1A17),
    inverseAction = Color(0xFF8A3B12),
    accent = Color(0xFFFFB95C),
    note = Color(0xFF3D3214),
    onNote = Color(0xFFFFE8A8),
    isDark = true,
)
