package io.github.otato.recipebook.data.testing

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.otato.recipebook.data.DataModule
import io.github.otato.recipebook.domain.IdGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.util.Properties

/** テスト用に、メモリ上の SQLite でデータ層を作る（外部キーも本物と同じく有効）。 */
object TestDatabase {
    fun newDriver(): SqlDriver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })

    fun create(
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        clock: Clock = Clock.systemDefaultZone(),
        ids: IdGenerator = sequentialIds(),
    ): DataModule {
        val driver = newDriver()
        DataModule.schema.create(driver)
        return DataModule(driver, dispatcher, clock, ids)
    }

    /** "id-0", "id-1", ... と順に返す ID の作り方。 */
    fun sequentialIds(prefix: String = "id"): IdGenerator {
        var counter = 0
        return IdGenerator { "$prefix-${counter++}" }
    }
}

/** テストで時刻を進められる時計。 */
class MutableClock(var millis: Long = 1_700_000_000_000L) : Clock() {
    override fun getZone(): java.time.ZoneId = java.time.ZoneId.of("Asia/Tokyo")
    override fun withZone(zone: java.time.ZoneId?): Clock = this
    override fun instant(): java.time.Instant = java.time.Instant.ofEpochMilli(millis)
    override fun millis(): Long = millis

    fun advance(ms: Long = 1_000) {
        millis += ms
    }
}
