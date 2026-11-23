package io.github.otato.recipebook.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.otato.recipebook.data.DataModule
import io.github.otato.recipebook.platform.BackupFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * 依存の組み立て（DI のフレームワークは使わない。training-log と同じ）。
 * アプリ全体で 1 つだけ作り、ViewModel に必要なものを渡す。
 */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val driver = AndroidSqliteDriver(
        schema = DataModule.schema,
        context = context,
        name = DATABASE_NAME,
        callback = object : AndroidSqliteDriver.Callback(DataModule.schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                // 外部キー（レシピを消すと、版・作った記録なども消える）を有効にする
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )

    val clock: Clock = Clock.systemDefaultZone()
    val data = DataModule(driver, clock = clock)

    val backupFiles = BackupFiles(context.applicationContext, data.backup, data.settings, clock)

    /**
     * 「貼り付けて追加」で読んだ文章を、編集の画面に渡す入れ物（長い文章を、画面の行き先の文字に入れないため）。
     * 編集の画面が受け取ったら、空にする。
     */
    val pendingImport = MutableStateFlow<String?>(null)

    /** 削除したレシピ（ホームで「元に戻す」を出すため） */
    val deletedRecipe = MutableStateFlow<DeletedRecipe?>(null)

    init {
        // 前に起動していたときに削除して、取り消さなかったものを、本当に消す（F-DA-03）
        appScope.launch { data.recipes.purgeDeleted() }
    }

    private companion object {
        const val DATABASE_NAME = "recipe-book.db"
    }
}

data class DeletedRecipe(val id: String, val title: String)
