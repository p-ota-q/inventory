package io.github.otato.recipebook.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.otato.recipebook.data.db.Cook_log
import io.github.otato.recipebook.data.db.RecipeBookDatabase
import io.github.otato.recipebook.data.db.Recipe_version
import io.github.otato.recipebook.domain.IdGenerator
import io.github.otato.recipebook.domain.logic.SearchText
import io.github.otato.recipebook.domain.logic.Urls
import io.github.otato.recipebook.domain.logic.Versioning
import io.github.otato.recipebook.domain.model.Author
import io.github.otato.recipebook.domain.model.CookLog
import io.github.otato.recipebook.domain.model.CookLogInput
import io.github.otato.recipebook.domain.model.ImportedText
import io.github.otato.recipebook.domain.model.Ingredient
import io.github.otato.recipebook.domain.model.Rating
import io.github.otato.recipebook.domain.model.Recipe
import io.github.otato.recipebook.domain.model.RecipeContent
import io.github.otato.recipebook.domain.model.RecipeDetail
import io.github.otato.recipebook.domain.model.RecipeDraft
import io.github.otato.recipebook.domain.model.RecipeSource
import io.github.otato.recipebook.domain.model.RecipeStatus
import io.github.otato.recipebook.domain.model.RecipeSummary
import io.github.otato.recipebook.domain.model.RecipeVersion
import io.github.otato.recipebook.domain.model.SourceInput
import io.github.otato.recipebook.domain.model.SourceKind
import io.github.otato.recipebook.domain.model.Tag
import io.github.otato.recipebook.domain.repository.RecipeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

internal class SqlRecipeRepository(
    private val db: RecipeBookDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val ids: IdGenerator,
) : RecipeRepository {
    private val q = db.recipeBookQueries

    /** どのテーブルが変わっても流れる（一覧と詳細は、変わるたびに読み直す。量が少ないので、これで足りる）。 */
    private fun changes(): Flow<Unit> = q.watchAll().asFlow().map { }

    // ── 一覧・詳細 ──

    override fun observeSummaries(): Flow<List<RecipeSummary>> = changes().map { withContext(io) { loadSummaries() } }

    override fun observeDetail(recipeId: String): Flow<RecipeDetail?> = changes().map { getDetail(recipeId) }

    override suspend fun getDetail(recipeId: String): RecipeDetail? = withContext(io) {
        db.transactionWithResult { loadDetail(recipeId) }
    }

    private fun loadSummaries(): List<RecipeSummary> = db.transactionWithResult {
        val tagsByRecipe = q.selectAllRecipeTags().executeAsList()
            .groupBy({ it.recipe_id }, { Tag(it.id, it.name, it.position.toInt()) })
        q.selectSummaries().executeAsList().map { row ->
            val tags = tagsByRecipe[row.id].orEmpty()
            RecipeSummary(
                id = row.id,
                title = row.title,
                status = enumOr(row.status, RecipeStatus.Want),
                tags = tags,
                cookCount = row.cook_count.toInt(),
                lastCookedOn = row.last_cooked_on.takeIf { it.isNotEmpty() }?.let(::parseDate),
                lastRating = row.last_rating?.let { enumOrNull<Rating>(it) },
                latestNextNote = row.next_note.ifEmpty { null },
                createdAt = Instant.ofEpochMilli(row.created_at),
                updatedAt = Instant.ofEpochMilli(row.updated_at),
                searchText = SearchText.normalize(
                    listOf(row.title, tags.joinToString(" ") { it.name }, row.ingredient_text, row.tips, row.log_text)
                        .joinToString(" "),
                ),
            )
        }
    }

    private fun loadDetail(recipeId: String): RecipeDetail? {
        val row = q.selectRecipe(recipeId).executeAsOneOrNull() ?: return null
        val current = q.selectVersion(row.current_version_id).executeAsOneOrNull()?.let(::toVersion) ?: return null
        val recipe = Recipe(
            id = row.id,
            title = row.title,
            status = enumOr(row.status, RecipeStatus.Want),
            currentVersion = current,
            sources = q.selectSources(recipeId).executeAsList().map {
                RecipeSource(it.id, enumOr(it.kind, SourceKind.Web), it.url, it.title, it.note)
            },
            tags = q.selectTagsOfRecipe(recipeId).executeAsList().map { Tag(it.id, it.name, it.position.toInt()) },
            createdAt = Instant.ofEpochMilli(row.created_at),
            updatedAt = Instant.ofEpochMilli(row.updated_at),
        )
        val original = q.selectOriginalVersion(recipeId).executeAsOneOrNull()?.takeIf { it.id != current.id }?.let(::toVersion)
        return RecipeDetail(
            recipe = recipe,
            cookLogs = q.selectCookLogs(recipeId).executeAsList().map(::toCookLog),
            versionCount = q.countVersions(recipeId).executeAsOne().toInt(),
            originalVersion = original,
            importedTexts = q.selectImportedTexts(recipeId).executeAsList().map {
                ImportedText(it.id, it.text, Instant.ofEpochMilli(it.created_at))
            },
        )
    }

    private fun toVersion(row: Recipe_version): RecipeVersion = RecipeVersion(
        id = row.id,
        recipeId = row.recipe_id,
        number = row.number.toInt(),
        isOriginal = row.is_original != 0L,
        content = RecipeContent(
            servings = row.servings,
            ingredients = q.selectIngredients(row.id).executeAsList().map { Ingredient(it.name, it.amount, it.is_heading != 0L) },
            steps = q.selectSteps(row.id).executeAsList().map { it.text },
            tips = row.tips,
        ),
        reason = row.reason,
        fromCookLogId = row.from_cook_log_id,
        author = enumOr(row.created_by, Author.User),
        createdAt = Instant.ofEpochMilli(row.created_at),
    )

    private fun toCookLog(row: Cook_log): CookLog = CookLog(
        id = row.id,
        recipeId = row.recipe_id,
        versionId = row.version_id,
        cookedOn = parseDate(row.cooked_on) ?: LocalDate.now(clock),
        rating = row.rating?.let { enumOrNull<Rating>(it) },
        impression = row.impression,
        changes = row.changes,
        nextNote = row.next_note,
        author = enumOr(row.created_by, Author.User),
        createdAt = Instant.ofEpochMilli(row.created_at),
    )

    // ── 作る・直す ──

    override suspend fun create(draft: RecipeDraft, author: Author): String = withContext(io) {
        val now = clock.millis()
        val recipeId = ids.next()
        val versionId = ids.next()
        val content = Versioning.normalized(draft.content)
        db.transaction {
            q.insertRecipe(recipeId, titleOf(draft), draft.status.name, versionId, now, now)
            q.insertVersion(versionId, recipeId, 1, 1, content.servings, content.tips, draft.versionReason.trim(), null, author.name, now)
            writeContent(versionId, content)
            writeSources(recipeId, draft.sources)
            writeTags(recipeId, draft.tagIds)
            draft.importedText?.takeIf { it.isNotBlank() }?.let { q.insertImportedText(ids.next(), recipeId, it, now) }
        }
        recipeId
    }

    override suspend fun update(recipeId: String, draft: RecipeDraft, author: Author) = withContext(io) {
        val now = clock.millis()
        db.transaction {
            val row = q.selectRecipe(recipeId).executeAsOneOrNull() ?: return@transaction
            val current = q.selectVersion(row.current_version_id).executeAsOne()
            val before = Versioning.normalized(toVersion(current).content)
            val after = Versioning.normalized(draft.content)
            if (before != after) {
                val lastCook = q.selectLastCookLogCreatedAt(recipeId).executeAsOneOrNull()?.let(Instant::ofEpochMilli)
                val newVersion = Versioning.needsNewVersion(
                    currentVersionCreatedAt = Instant.ofEpochMilli(current.created_at),
                    lastCookLogCreatedAt = lastCook,
                    fromCookLog = draft.fromCookLogId != null,
                )
                if (newVersion) {
                    val versionId = ids.next()
                    val number = q.countVersions(recipeId).executeAsOne() + 1
                    q.insertVersion(
                        versionId, recipeId, number, 0, after.servings, after.tips, draft.versionReason.trim(),
                        draft.fromCookLogId, author.name, now,
                    )
                    writeContent(versionId, after)
                    q.setCurrentVersion(versionId, now, recipeId)
                } else {
                    q.updateVersionContent(after.servings, after.tips, current.id)
                    q.deleteIngredients(current.id)
                    q.deleteSteps(current.id)
                    writeContent(current.id, after)
                }
            }
            q.updateRecipeMeta(titleOf(draft), draft.status.name, now, recipeId)
            q.deleteSources(recipeId)
            writeSources(recipeId, draft.sources)
            q.deleteRecipeTags(recipeId)
            writeTags(recipeId, draft.tagIds)
        }
    }

    private fun writeContent(versionId: String, content: RecipeContent) {
        content.ingredients.forEachIndexed { i, item ->
            q.insertIngredient(versionId, i.toLong(), item.name, item.amount, if (item.isHeading) 1 else 0)
        }
        content.steps.forEachIndexed { i, text -> q.insertStep(versionId, i.toLong(), text) }
    }

    private fun writeSources(recipeId: String, sources: List<SourceInput>) {
        sources
            .filter { !it.url.isNullOrBlank() || it.title.isNotBlank() || it.note.isNotBlank() }
            .forEachIndexed { i, s ->
                q.insertSource(ids.next(), recipeId, i.toLong(), s.kind.name, s.url?.trim()?.ifEmpty { null }, s.title.trim(), s.note.trim())
            }
    }

    private fun writeTags(recipeId: String, tagIds: List<String>) {
        tagIds.distinct().forEach { q.insertRecipeTag(recipeId, it) }
    }

    private fun titleOf(draft: RecipeDraft): String = draft.title.trim().ifEmpty { UNTITLED }

    override suspend fun setStatus(recipeId: String, status: RecipeStatus) {
        withContext(io) { q.updateRecipeStatus(status.name, clock.millis(), recipeId) }
    }

    override suspend fun delete(recipeId: String) {
        withContext(io) { q.markRecipeDeleted(clock.millis(), recipeId) }
    }

    override suspend fun undoDelete(recipeId: String) {
        withContext(io) { q.unmarkRecipeDeleted(recipeId) }
    }

    override suspend fun purgeDeleted() = withContext(io) {
        db.transaction {
            q.purgeDeletedRecipes()
            q.purgeDeletedCookLogs()
        }
    }

    override suspend fun findByUrl(url: String): RecipeSummary? = withContext(io) {
        val target = Urls.normalize(url)
        val recipeId = q.selectSourceUrls().executeAsList()
            .firstOrNull { row -> row.url?.let(Urls::normalize) == target }
            ?.recipe_id
            ?: return@withContext null
        loadSummaries().firstOrNull { it.id == recipeId }
    }

    // ── 作った記録 ──

    override suspend fun addCookLog(recipeId: String, input: CookLogInput, author: Author): String = withContext(io) {
        val now = clock.millis()
        val id = ids.next()
        db.transaction {
            val row = q.selectRecipe(recipeId).executeAsOne()
            q.insertCookLog(
                id, recipeId, row.current_version_id, input.cookedOn.toString(), input.rating?.name,
                input.impression.trim(), input.changes.trim(), input.nextNote.trim(), author.name, now,
            )
            // 「気になる」のレシピは、作った記録を付けたら「作った」になる（F-RC-04）
            val status = if (row.status == RecipeStatus.Want.name) RecipeStatus.Made.name else row.status
            q.updateRecipeStatus(status, now, recipeId)
        }
        id
    }

    override suspend fun updateCookLog(cookLogId: String, input: CookLogInput) {
        withContext(io) {
            q.updateCookLog(
                input.cookedOn.toString(), input.rating?.name, input.impression.trim(), input.changes.trim(),
                input.nextNote.trim(), cookLogId,
            )
        }
    }

    override suspend fun getCookLog(cookLogId: String): CookLog? = withContext(io) {
        q.selectCookLog(cookLogId).executeAsOneOrNull()?.let(::toCookLog)
    }

    override suspend fun deleteCookLog(cookLogId: String) {
        withContext(io) { q.markCookLogDeleted(clock.millis(), cookLogId) }
    }

    override suspend fun undoDeleteCookLog(cookLogId: String) {
        withContext(io) { q.unmarkCookLogDeleted(cookLogId) }
    }

    // ── 版 ──

    override fun observeVersions(recipeId: String): Flow<List<RecipeVersion>> = changes().map {
        withContext(io) {
            db.transactionWithResult { q.selectVersions(recipeId).executeAsList().map(::toVersion) }
        }
    }

    override suspend fun restoreVersion(recipeId: String, versionId: String) = withContext(io) {
        val now = clock.millis()
        db.transaction {
            val target = q.selectVersion(versionId).executeAsOneOrNull() ?: return@transaction
            val content = toVersion(target).content
            val newId = ids.next()
            val number = q.countVersions(recipeId).executeAsOne() + 1
            q.insertVersion(
                newId, recipeId, number, 0, content.servings, content.tips, "版 ${target.number} に戻した",
                null, Author.User.name, now,
            )
            writeContent(newId, content)
            q.setCurrentVersion(newId, now, recipeId)
        }
    }

    // ── タグ ──

    override fun observeTags(): Flow<List<Tag>> =
        q.selectTags().asFlow().mapToList(io).map { rows -> rows.map { Tag(it.id, it.name, it.position.toInt()) } }

    override suspend fun createTag(name: String): Tag = withContext(io) {
        val trimmed = name.trim()
        db.transactionWithResult {
            val existing = q.selectTags().executeAsList().firstOrNull { it.name == trimmed }
            if (existing != null) {
                Tag(existing.id, existing.name, existing.position.toInt())
            } else {
                val position = q.countTags().executeAsOne()
                val id = ids.next()
                q.insertTag(id, trimmed, position)
                Tag(id, trimmed, position.toInt())
            }
        }
    }

    override suspend fun renameTag(tagId: String, name: String) {
        withContext(io) { q.renameTag(name.trim(), tagId) }
    }

    override suspend fun deleteTag(tagId: String) {
        withContext(io) { q.deleteTag(tagId) }
    }

    override suspend fun reorderTags(tagIds: List<String>) = withContext(io) {
        db.transaction { tagIds.forEachIndexed { i, id -> q.updateTagPosition(i.toLong(), id) } }
    }

    private companion object {
        const val UNTITLED = "名前のないレシピ"
    }
}

internal fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

internal inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }

internal inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E = enumOrNull<E>(name) ?: default
