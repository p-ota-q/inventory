package io.github.otato.recipebook.domain.logic

import java.text.Normalizer

/**
 * 検索のための文字の正規化（F-SR-01）。
 *
 * - 全角・半角の違いをなくす（ＡＢＣ → abc、ｶﾞ → ガ）
 * - カタカナをひらがなにする（「ネギ」でも「ねぎ」でも見つかる）
 * - 英字は小文字にする
 * - 空白を詰める
 */
object SearchText {
    fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        val sb = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            sb.append(
                when (ch) {
                    // カタカナ（ァ〜ヶ）をひらがなにずらす。「ー」はそのまま
                    in 'ァ'..'ヶ' -> ch - 0x60
                    else -> ch
                },
            )
        }
        return sb.toString().replace(WHITESPACE, " ").trim()
    }

    /** 検索の語（空白で区切ったもの）が、すべて [haystack]（正規化済み）に含まれるか。 */
    fun matches(haystack: String, query: String): Boolean {
        val terms = normalize(query).split(' ').filter { it.isNotEmpty() }
        return terms.all { haystack.contains(it) }
    }

    private val WHITESPACE = Regex("\\s+")
}
