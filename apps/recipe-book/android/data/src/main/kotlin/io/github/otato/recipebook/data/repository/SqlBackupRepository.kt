package io.github.otato.recipebook.data.repository

import io.github.otato.recipebook.data.backup.BackupFile
import io.github.otato.recipebook.data.backup.BackupJson
import io.github.otato.recipebook.data.backup.CookLogRow
import io.github.otato.recipebook.data.backup.ImportedTextRow
import io.github.otato.recipebook.data.backup.IngredientRow
import io.github.otato.recipebook.data.backup.RecipeRow
import io.github.otato.recipebook.data.backup.RecipeTagRow
import io.github.otato.recipebook.data.backup.SourceRow
import io.github.otato.recipebook.data.backup.StepRow
import io.github.otato.recipebook.data.backup.TagRow
import io.github.otato.recipebook.data.backup.VersionRow
import io.github.otato.recipebook.data.db.RecipeBookDatabase
import io.github.otato.recipebook.domain.model.BackupFormatException
import io.github.otato.recipebook.domain.repository.BackupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Clock

/** 全データを 1 つの JSON にする・JSON から全データを戻す（F-DA-02）。削除の印が付いたものは書き出さない。 */
internal class SqlBackupRepository(
    private val db: RecipeBookDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
) : BackupRepository {
    private val q = db.recipeBookQueries

    override suspend fun exportJson(): String = withContext(io) {
        val file = db.transactionWithResult {
            BackupFile(
                exportedAt = clock.millis(),
                recipes = q.allRecipes().executeAsList().map {
                    RecipeRow(it.id, it.title, it.status, it.current_version_id, it.created_at, it.updated_at)
                },
                versions = q.allVersions().executeAsList().map {
                    VersionRow(
                        it.id, it.recipe_id, it.number, it.is_original != 0L, it.servings, it.tips, it.reason,
                        it.from_cook_log_id, it.created_by, it.created_at,
                    )
                },
                ingredients = q.allIngredients().executeAsList().map {
                    IngredientRow(it.version_id, it.position, it.name, it.amount, it.is_heading != 0L)
                },
                steps = q.allSteps().executeAsList().map { StepRow(it.version_id, it.position, it.text) },
                sources = q.allSources().executeAsList().map {
                    SourceRow(it.id, it.recipe_id, it.position, it.kind, it.url, it.title, it.note)
                },
                cookLogs = q.allCookLogs().executeAsList().map {
                    CookLogRow(
                        it.id, it.recipe_id, it.version_id, it.cooked_on, it.rating, it.impression, it.changes,
                        it.next_note, it.created_by, it.created_at,
                    )
                },
                tags = q.selectTags().executeAsList().map { TagRow(it.id, it.name, it.position) },
                recipeTags = q.allRecipeTags().executeAsList().map { RecipeTagRow(it.recipe_id, it.tag_id) },
                importedTexts = q.allImportedTexts().executeAsList().map {
                    ImportedTextRow(it.id, it.recipe_id, it.text, it.created_at)
                },
            )
        }
        BackupJson.encode(file)
    }

    override suspend fun inspectJson(json: String): Int = withContext(io) { BackupJson.decode(json).recipes.size }

    override suspend fun restoreJson(json: String) = withContext(io) {
        val file = BackupJson.decode(json)
        try {
            db.transaction {
                // レシピを消すと、版・材料・手順・出典・作った記録・タグの付け外し・取り込んだ文章も消える（外部キー）
                q.deleteAllRecipes()
                q.deleteAllTags()
                file.tags.forEach { q.insertTag(it.id, it.name, it.position) }
                file.recipes.forEach { q.insertRecipe(it.id, it.title, it.status, it.currentVersionId, it.createdAt, it.updatedAt) }
                file.versions.forEach {
                    q.insertVersion(
                        it.id, it.recipeId, it.number, if (it.isOriginal) 1 else 0, it.servings, it.tips, it.reason,
                        it.fromCookLogId, it.createdBy, it.createdAt,
                    )
                }
                file.ingredients.forEach { q.insertIngredient(it.versionId, it.position, it.name, it.amount, if (it.isHeading) 1 else 0) }
                file.steps.forEach { q.insertStep(it.versionId, it.position, it.text) }
                file.sources.forEach { q.insertSource(it.id, it.recipeId, it.position, it.kind, it.url, it.title, it.note) }
                file.cookLogs.forEach {
                    q.insertCookLog(
                        it.id, it.recipeId, it.versionId, it.cookedOn, it.rating, it.impression, it.changes, it.nextNote,
                        it.createdBy, it.createdAt,
                    )
                }
                file.recipeTags.forEach { q.insertRecipeTag(it.recipeId, it.tagId) }
                file.importedTexts.forEach { q.insertImportedText(it.id, it.recipeId, it.text, it.createdAt) }
            }
        } catch (e: Exception) {
            // 中身の食い違い（存在しないレシピを指す行など）。トランザクションなので、元のデータは残っている
            if (e is BackupFormatException) throw e
            throw BackupFormatException("このファイルは、中身が壊れているため読み込めません")
        }
    }
}
