package io.github.otato.recipebook.domain.model

enum class ThemeMode { System, Light, Dark }

/** 一覧の並べ替え（F-SR-03） */
enum class RecipeSort { Added, RecentlyCooked, MostCooked, BestRated }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val sort: RecipeSort = RecipeSort.Added,
    /** 最後に書き出した時刻（バックアップの案内に使う） */
    val lastExportAtMillis: Long? = null,
)

/** 書き出したファイルが読めないとき。[message] は画面にそのまま出す。 */
class BackupFormatException(message: String) : Exception(message)
