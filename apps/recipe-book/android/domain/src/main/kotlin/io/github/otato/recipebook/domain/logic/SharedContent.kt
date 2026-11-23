package io.github.otato.recipebook.domain.logic

import io.github.otato.recipebook.domain.model.SourceKind

/** ほかのアプリから「共有」で受け取ったもの（F-CL-01）。 */
data class SharedContent(
    /** 共有された URL（最初の 1 つ） */
    val url: String?,
    /** 料理名の候補（共有の題名、または文章から取ったもの）。空のこともある */
    val title: String,
    val kind: SourceKind,
    /** 文章を分けた結果。URL と題名だけのときは null */
    val parsed: ParsedRecipe?,
    /** 受け取った文章そのもの（取り込んだ文章として残す） */
    val rawText: String,
)

/**
 * 共有で受け取った文字（本文と題名）を読む。
 *
 * - YouTube・ブラウザ: 本文が URL、題名がページの題名（アプリによっては本文に題名と URL が入る）
 * - Gemini・ChatGPT など: 本文が回答の文章
 */
object SharedContentReader {
    fun read(text: String?, subject: String?): SharedContent {
        val body = text.orEmpty().trim()
        val url = Urls.findAll(body).firstOrNull()
        val withoutUrl = if (url != null) body.replace(url, "").trim() else body
        val isLongText = withoutUrl.lines().count { it.isNotBlank() } >= 3 || withoutUrl.length > 120
        val parsed = if (isLongText) RecipeTextParser.parse(body) else null

        val titleFromSubject = subject?.let { cleanSharedTitle(it, url) }?.takeIf { it.isNotEmpty() }
        val titleFromBody = when {
            parsed != null -> parsed.title
            withoutUrl.isNotEmpty() -> cleanSharedTitle(withoutUrl, url)
            else -> null
        }
        val title = (parsed?.title ?: titleFromSubject ?: titleFromBody).orEmpty()

        val kind = when {
            url != null -> Urls.kindOf(url)
            // URL のない長い文章は、AI の回答のことが多い（保存の前に変えられる）
            parsed != null -> SourceKind.Ai
            else -> SourceKind.Own
        }
        return SharedContent(url = url, title = title, kind = kind, parsed = parsed, rawText = body)
    }

    /** 共有の題名についてくる、サイト名などの飾りを外す。 */
    fun cleanSharedTitle(raw: String, url: String?): String {
        var t = raw.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        // 「"題名" を YouTube で見る」「Watch "題名" on YouTube」
        YOUTUBE_WRAPPED.find(t)?.let { t = it.groupValues[1] }
        for (suffix in SITE_SUFFIXES) {
            val i = t.lastIndexOf(suffix)
            if (i > 0) t = t.substring(0, i)
        }
        // クックパッドの「料理名 by 作者」
        if (url != null && Urls.hostOf(url)?.contains("cookpad") == true) {
            val by = t.indexOf(" by ")
            if (by > 0) t = t.substring(0, by)
        }
        return t.trim().trim('"', '“', '”', '「', '」')
    }

    private val YOUTUBE_WRAPPED = Regex("""^(?:Watch\s+)?["“「](.+)["”」]\s*(?:を\s*YouTube\s*で見る|on YouTube)""")
    private val SITE_SUFFIXES = listOf(" - YouTube", " | クックパッド", "【クックパッド】", " [クックパッド]", " | レシピ・作り方")
}
