package io.github.otato.recipebook.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import io.github.otato.recipebook.data.db.RecipeBookDatabase
import io.github.otato.recipebook.data.repository.SqlBackupRepository
import io.github.otato.recipebook.data.repository.SqlRecipeRepository
import io.github.otato.recipebook.data.repository.SqlSettingsRepository
import io.github.otato.recipebook.domain.IdGenerator
import io.github.otato.recipebook.domain.UuidGenerator
import io.github.otato.recipebook.domain.repository.BackupRepository
import io.github.otato.recipebook.domain.repository.RecipeRepository
import io.github.otato.recipebook.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock

/**
 * データ層の入口。ドライバー（Android なら Android の SQLite、テストなら JVM の SQLite）を渡すと、
 * ドメイン層の interface を実装したリポジトリを返す。画面側は SQLDelight の型を知らなくてよい。
 */
class DataModule(
    driver: SqlDriver,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    clock: Clock = Clock.systemDefaultZone(),
    ids: IdGenerator = UuidGenerator,
) {
    private val database = RecipeBookDatabase(driver)

    val recipes: RecipeRepository = SqlRecipeRepository(database, ioDispatcher, clock, ids)
    val settings: SettingsRepository = SqlSettingsRepository(database, ioDispatcher)
    val backup: BackupRepository = SqlBackupRepository(database, ioDispatcher, clock)

    companion object {
        /** ドライバーを作るときに渡すスキーマ（テーブルの定義とマイグレーション）。 */
        val schema: SqlSchema<QueryResult.Value<Unit>> get() = RecipeBookDatabase.Schema
    }
}
