package io.github.otato.recipebook.domain.logic

import io.github.otato.recipebook.domain.model.SourceKind
import java.net.URI

/** URL の扱い（出典の種類の見分け、同じ URL かどうかの比較。F-RC-02、F-CL-03）。 */
object Urls {
    private val URL_PATTERN = Regex("""https?://[^\s<>"'「」（）()\[\]{}、。]+""")

    /** 文章の中の URL（出てきた順・重複なし）。 */
    fun findAll(text: String): List<String> =
        URL_PATTERN.findAll(text).map { it.value.trimEnd('.', ',', '!', '?', '！', '？') }.distinct().toList()

    fun kindOf(url: String): SourceKind {
        val host = hostOf(url) ?: return SourceKind.Web
        return if (host == "youtu.be" || host.endsWith("youtube.com")) SourceKind.YouTube else SourceKind.Web
    }

    /**
     * 比べるための URL。同じページなら、共有のしかたが違っても同じ文字列になるようにする。
     *
     * - YouTube は、動画の ID だけにする（youtu.be / ショート / 共有の印 si= などの違いをなくす）
     * - ほかのサイトは、追跡用のパラメーター（utm_ など）と、# 以降を外す
     * - http と https、www の有無、末尾の / の違いをなくす
     */
    fun normalize(url: String): String {
        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed.lowercase()
        val host = (uri.host ?: return trimmed.lowercase()).lowercase().removePrefix("www.").removePrefix("m.")
        val path = (uri.rawPath ?: "").trimEnd('/')
        val params = parseQuery(uri.rawQuery)

        youTubeId(host, path, params)?.let { return "youtube:$it" }

        val kept = params.filterNot { (key, _) -> isTrackingParam(key) }
        val query = if (kept.isEmpty()) "" else "?" + kept.joinToString("&") { (k, v) -> if (v == null) k else "$k=$v" }
        return "$host$path$query"
    }

    fun hostOf(url: String): String? =
        runCatching { URI(url.trim()).host }.getOrNull()?.lowercase()?.removePrefix("www.")?.removePrefix("m.")

    private fun youTubeId(host: String, path: String, params: List<Pair<String, String?>>): String? = when {
        host == "youtu.be" -> path.removePrefix("/").takeIf { it.isNotEmpty() }
        host.endsWith("youtube.com") -> when {
            path == "/watch" -> params.firstOrNull { it.first == "v" }?.second
            path.startsWith("/shorts/") -> path.removePrefix("/shorts/")
            path.startsWith("/live/") -> path.removePrefix("/live/")
            else -> null
        }
        else -> null
    }

    private fun parseQuery(raw: String?): List<Pair<String, String?>> =
        raw.orEmpty().split('&').filter { it.isNotEmpty() }.map { part ->
            val i = part.indexOf('=')
            if (i < 0) part to null else part.substring(0, i) to part.substring(i + 1)
        }

    private fun isTrackingParam(key: String): Boolean {
        val k = key.lowercase()
        return k.startsWith("utm_") || k in TRACKING_KEYS
    }

    private val TRACKING_KEYS = setOf("si", "fbclid", "gclid", "igshid", "feature", "ref", "ref_src")
}
