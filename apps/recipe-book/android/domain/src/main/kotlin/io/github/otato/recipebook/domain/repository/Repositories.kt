package io.github.otato.recipebook.domain.repository

import io.github.otato.recipebook.domain.model.AppSettings
import io.github.otato.recipebook.domain.model.Author
import io.github.otato.recipebook.domain.model.CookLog
import io.github.otato.recipebook.domain.model.CookLogInput
import io.github.otato.recipebook.domain.model.RecipeDetail
import io.github.otato.recipebook.domain.model.RecipeDraft
import io.github.otato.recipebook.domain.model.RecipeStatus
import io.github.otato.recipebook.domain.model.RecipeSummary
import io.github.otato.recipebook.domain.model.RecipeVersion
import io.github.otato.recipebook.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * レシピ・作った記録・版・タグ。画面の操作も、将来 Gemini から呼ぶ操作（段階 3）も、ここを通す
 * （要件 9 章「操作の作り方」）。
 */
interface RecipeRepository {
    /** 削除していないレシピの一覧（並びは決めない。並べ替えは画面側） */
    fun observeSummaries(): Flow<List<RecipeSummary>>

    /** 1 つのレシピの詳細。無い（削除した）ときは null */
    fun observeDetail(recipeId: String): Flow<RecipeDetail?>

    suspend fun getDetail(recipeId: String): RecipeDetail?

    /** 新しく作る。作ったレシピの id を返す */
    suspend fun create(draft: RecipeDraft, author: Author = Author.User): String

    /**
     * 直す。作ったあと（いまの版を作ったあとに、作った記録がある）なら、新しい版にする。
     * 作った記録から反映するとき（[RecipeDraft.fromCookLogId]）も、新しい版にする。中身が変わっていなければ、版は増やさない。
     */
    suspend fun update(recipeId: String, draft: RecipeDraft, author: Author = Author.User)

    suspend fun setStatus(recipeId: String, status: RecipeStatus)

    /** 取り消せる削除（F-DA-03）。取り消さなければ、次に起動したときに消える */
    suspend fun delete(recipeId: String)

    suspend fun undoDelete(recipeId: String)

    /** 削除の印が付いたものを、本当に消す */
    suspend fun purgeDeleted()

    /** 同じ URL（正規化して比べる）の出典を持つレシピ（F-CL-03） */
    suspend fun findByUrl(url: String): RecipeSummary?

    /** 作った記録を付ける。「気になる」のレシピは「作った」になる。記録の id を返す */
    suspend fun addCookLog(recipeId: String, input: CookLogInput, author: Author = Author.User): String

    suspend fun updateCookLog(cookLogId: String, input: CookLogInput)

    suspend fun getCookLog(cookLogId: String): CookLog?

    suspend fun deleteCookLog(cookLogId: String)

    suspend fun undoDeleteCookLog(cookLogId: String)

    /** 版の一覧（新しい順。F-RC-07） */
    fun observeVersions(recipeId: String): Flow<List<RecipeVersion>>

    /** 前の版に戻す。戻した内容を、新しい版として保存する（どの版も消さない） */
    suspend fun restoreVersion(recipeId: String, versionId: String)

    /** タグ（並び順） */
    fun observeTags(): Flow<List<Tag>>

    /** タグを作る。同じ名前があれば、それを返す */
    suspend fun createTag(name: String): Tag

    suspend fun renameTag(tagId: String, name: String)

    suspend fun deleteTag(tagId: String)

    suspend fun reorderTags(tagIds: List<String>)
}

interface SettingsRepository {
    fun observe(): Flow<AppSettings>

    suspend fun get(): AppSettings

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** バックアップ（F-DA-02）。全データを 1 つの JSON にする。 */
interface BackupRepository {
    suspend fun exportJson(): String

    /**
     * 全データを置き換える。
     * @throws io.github.otato.recipebook.domain.model.BackupFormatException 読めないとき
     */
    suspend fun restoreJson(json: String)

    /** 読み込む前に、中身を確かめる（レシピの数を返す） */
    suspend fun inspectJson(json: String): Int
}
