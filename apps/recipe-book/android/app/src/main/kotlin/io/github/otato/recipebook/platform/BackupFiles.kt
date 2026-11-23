package io.github.otato.recipebook.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.otato.recipebook.domain.model.BackupFormatException
import io.github.otato.recipebook.domain.repository.BackupRepository
import io.github.otato.recipebook.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * バックアップのファイルまわり（F-DA-02）。
 *
 * - 書き出し: キャッシュの中にファイルを作り、共有機能に渡す（保存先は、利用者が選ぶ。Google ドライブも選べる）
 * - 読み込み: 利用者が選んだファイルを読む。置き換える前に、いまのデータをアプリの中に残す（戻せるように）
 */
class BackupFiles(
    private val context: Context,
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val exportDir get() = File(context.cacheDir, "exports").apply { mkdirs() }
    private val safetyDir get() = File(context.filesDir, "backups").apply { mkdirs() }
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** 全データを JSON に書き出し、共有機能に渡すための Intent を返す。 */
    suspend fun export(): Intent = withContext(Dispatchers.IO) {
        exportDir.listFiles()?.forEach { it.delete() }
        val file = File(exportDir, "recipe-book-${LocalDateTime.now(clock).format(stamp)}.json")
        file.writeText(backup.exportJson())
        settings.update { it.copy(lastExportAtMillis = clock.millis()) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        Intent.createChooser(send, "保存先を選ぶ")
    }

    /**
     * 利用者が選んだファイルを読み、中身を確かめる。
     * @return 読んだ文字と、入っているレシピの数
     * @throws BackupFormatException 読めないとき（画面に出す文言を持つ）
     */
    suspend fun read(uri: Uri): Pair<String, Int> = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readUpTo(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) throw BackupFormatException("このファイルは、大きすぎて読み込めません")
                bytes.toString(Charsets.UTF_8)
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } ?: throw BackupFormatException("ファイルを開けませんでした")
        text to backup.inspectJson(text)
    }

    /** 全データを置き換える。置き換える前に、いまのデータをアプリの中に残す。 */
    suspend fun restore(json: String) {
        withContext(Dispatchers.IO) {
            try {
                File(safetyDir, "before-restore-${LocalDateTime.now(clock).format(stamp)}.json").writeText(backup.exportJson())
                safetyDir.listFiles().orEmpty().sortedByDescending { it.lastModified() }.drop(KEEP).forEach { it.delete() }
            } catch (e: IOException) {
                // 残せなくても、読み込みは止めない
            }
        }
        backup.restoreJson(json)
    }

    private companion object {
        const val MAX_BYTES = 50 * 1024 * 1024
        const val KEEP = 3
    }
}

/** minSdk 26 では `InputStream.readNBytes` が使えないので、同じことをする。 */
private fun InputStream.readUpTo(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (total < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - total))
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}
