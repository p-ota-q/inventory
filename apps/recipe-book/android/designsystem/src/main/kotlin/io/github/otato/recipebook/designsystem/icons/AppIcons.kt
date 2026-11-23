package io.github.otato.recipebook.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * アイコン（24 × 24、線の太さ 2、丸い端）。training-log と同じ作り方で、パスの文字列から作る。
 * 色は、使うときの tint で決まる。アイコンのライブラリは使わない（軽さのため）。
 */
object AppIcons {
    private fun stroke(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { data ->
                    addPath(
                        pathData = PathParser().parsePathString(data).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    private fun filled(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { data ->
                    addPath(pathData = PathParser().parsePathString(data).toNodes(), fill = SolidColor(Color.Black))
                }
            }
            .build()

    val Back = stroke("back", "M15 5l-7 7 7 7")
    val Close = stroke("close", "M6 6l12 12M18 6L6 18")
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7.5")
    val Plus = stroke("plus", "M12 5v14M5 12h14")
    val Up = stroke("up", "M6 15l6-6 6 6")
    val Down = stroke("down", "M6 9l6 6 6-6")
    val Right = stroke("right", "M9 6l6 6-6 6")
    val Drag = stroke("drag", "M5 9h14M5 15h14")

    /** 縦に 3 つの点（その画面・その行のメニューを開く） */
    val More = stroke("more", "M12 5h.01M12 12h.01M12 19h.01")
    val Search = stroke("search", "M4 10.5a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0", "M15.5 15.5L20 20")
    val Trash = stroke("trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")

    /** 鉛筆（編集） */
    val Edit = stroke("edit", "M4 20l1-4.5L16.5 4a2.1 2.1 0 0 1 3 3L8 18.5z", "M14.5 6l3 3")

    /** 調整のつまみ（設定）。歯車を線で描くと太陽に見えるので、つまみにした */
    val Settings = stroke("settings", "M4 7h9M17 7h3M4 17h3M11 17h9", "M13 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M7 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")

    /** 貼り付け（クリップボード） */
    val Paste = stroke("paste", "M9 4h6v3H9z", "M9 5.5H6.5A1.5 1.5 0 0 0 5 7v12.5A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V7a1.5 1.5 0 0 0-1.5-1.5H15")

    /** 鍋（作る） */
    val Pot = stroke("pot", "M4 10h16v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4z", "M2 10h2M20 10h2M9 6.5c0-1 1-1 1-2M14 6.5c0-1 1-1 1-2")

    /** リンク（出典を開く） */
    val Link = stroke("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")

    /** 履歴（版の履歴） */
    val History = stroke("history", "M4 12a8 8 0 1 0 2.6-5.9L4 8.5M4 4v4.5h4.5", "M12 8v4l3 2")

    /** メモ */
    val Note = stroke("note", "M5 4h14v16H5z", "M8.5 9h7M8.5 13h7")

    /** 書き出す（上向きの矢印と受け皿） */
    val Export = stroke("export", "M12 15V4M8 8l4-4 4 4M5 19h14")

    /** 取り込む（下向きの矢印と受け皿） */
    val Import = stroke("import", "M12 4v11M8 11l4 4 4-4M5 19h14")

    /** 並べ替え（上下の矢印） */
    val Sort = stroke("sort", "M8 4v16M4.5 7.5L8 4l3.5 3.5M16 20V4M12.5 16.5L16 20l3.5-3.5")

    /** タグ */
    val Tag = stroke("tag", "M4 4h7l9 9-7 7-9-9z", "M8 8h.01")

    /** 星（定番） */
    val Star = filled("star", "M12 3l2.7 5.6 6.1.8-4.5 4.2 1.2 6.1-5.5-3-5.5 3 1.2-6.1-4.5-4.2 6.1-.8z")
    val StarOutline = stroke("star-outline", "M12 3l2.7 5.6 6.1.8-4.5 4.2 1.2 6.1-5.5-3-5.5 3 1.2-6.1-4.5-4.2 6.1-.8z")
}
