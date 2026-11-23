package io.github.otato.recipebook.data.backup

import io.github.otato.recipebook.domain.model.BackupFormatException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 全データの書き出しファイル（JSON）の形（F-DA-02）。テーブルの行を、そのまま持つ。
 *
 * - ファイルには、形式の版（`formatVersion`）を入れる。新しすぎる版は断り、知らない項目は読み飛ばす。
 *   形式を変えるときは、版を上げ、古い版も読めるようにする
 * - 列挙（状態・評価など）は名前（文字）、日付は `yyyy-MM-dd`、時刻はエポックミリ秒
 */
@Serializable
internal data class BackupFile(
    val app: String = APP_NAME,
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: Long = 0,
    val recipes: List<RecipeRow> = emptyList(),
    val versions: List<VersionRow> = emptyList(),
    val ingredients: List<IngredientRow> = emptyList(),
    val steps: List<StepRow> = emptyList(),
    val sources: List<SourceRow> = emptyList(),
    val cookLogs: List<CookLogRow> = emptyList(),
    val tags: List<TagRow> = emptyList(),
    val recipeTags: List<RecipeTagRow> = emptyList(),
    val importedTexts: List<ImportedTextRow> = emptyList(),
) {
    companion object {
        const val APP_NAME = "recipe-book"
        const val FORMAT_VERSION = 1
    }
}

@Serializable
internal data class RecipeRow(
    val id: String,
    val title: String,
    val status: String,
    val currentVersionId: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class VersionRow(
    val id: String,
    val recipeId: String,
    val number: Long,
    val isOriginal: Boolean,
    val servings: String = "",
    val tips: String = "",
    val reason: String = "",
    val fromCookLogId: String? = null,
    val createdBy: String = "User",
    val createdAt: Long,
)

@Serializable
internal data class IngredientRow(
    val versionId: String,
    val position: Long,
    val name: String,
    val amount: String = "",
    val isHeading: Boolean = false,
)

@Serializable
internal data class StepRow(
    val versionId: String,
    val position: Long,
    val text: String,
)

@Serializable
internal data class SourceRow(
    val id: String,
    val recipeId: String,
    val position: Long,
    val kind: String,
    val url: String? = null,
    val title: String = "",
    val note: String = "",
)

@Serializable
internal data class CookLogRow(
    val id: String,
    val recipeId: String,
    val versionId: String? = null,
    val cookedOn: String,
    val rating: String? = null,
    val impression: String = "",
    val changes: String = "",
    val nextNote: String = "",
    val createdBy: String = "User",
    val createdAt: Long,
)

@Serializable
internal data class TagRow(
    val id: String,
    val name: String,
    val position: Long,
)

@Serializable
internal data class RecipeTagRow(
    val recipeId: String,
    val tagId: String,
)

@Serializable
internal data class ImportedTextRow(
    val id: String,
    val recipeId: String,
    val text: String,
    val createdAt: Long,
)

internal object BackupJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** @throws BackupFormatException ファイルが読めないとき、このアプリより新しい形式のとき */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupFormatException(NOT_OURS)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException(NOT_OURS)
        }
        if (file.app != BackupFile.APP_NAME) throw BackupFormatException(NOT_OURS)
        if (file.formatVersion > BackupFile.FORMAT_VERSION) {
            throw BackupFormatException("このファイルは、新しい版のアプリで書き出したものです。アプリを新しくしてから、読み込んでください")
        }
        return file
    }

    private const val NOT_OURS = "このファイルは、読み込めません（このアプリの書き出しファイルではないようです）"
}
