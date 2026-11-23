package io.github.otato.recipebook.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.otato.recipebook.data.db.RecipeBookDatabase
import io.github.otato.recipebook.domain.model.AppSettings
import io.github.otato.recipebook.domain.model.RecipeSort
import io.github.otato.recipebook.domain.model.ThemeMode
import io.github.otato.recipebook.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 設定は、キーと値の組として保存する。値が無い・読めないものは、初期値にする。 */
internal class SqlSettingsRepository(
    private val db: RecipeBookDatabase,
    private val io: CoroutineDispatcher,
) : SettingsRepository {
    private val queries = db.settingQueries

    override fun observe(): Flow<AppSettings> =
        queries.selectAll().asFlow().mapToList(io).map { rows -> parse(rows.associate { it.key to it.content }) }

    override suspend fun get(): AppSettings = withContext(io) {
        parse(queries.selectAll().executeAsList().associate { it.key to it.content })
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        withContext(io) {
            db.transaction {
                val current = parse(queries.selectAll().executeAsList().associate { it.key to it.content })
                serialize(transform(current)).forEach { (key, value) -> queries.upsert(key, value) }
            }
        }
    }

    private fun parse(map: Map<String, String>): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = map["theme_mode"]?.let { enumOrNull<ThemeMode>(it) } ?: d.themeMode,
            sort = map["sort"]?.let { enumOrNull<RecipeSort>(it) } ?: d.sort,
            lastExportAtMillis = map["last_export_at"]?.toLongOrNull(),
        )
    }

    private fun serialize(s: AppSettings): Map<String, String> = listOfNotNull(
        "theme_mode" to s.themeMode.name,
        "sort" to s.sort.name,
        s.lastExportAtMillis?.let { "last_export_at" to it.toString() },
    ).toMap()
}
