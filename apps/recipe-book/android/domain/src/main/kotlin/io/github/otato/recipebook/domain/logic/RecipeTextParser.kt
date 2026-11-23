package io.github.otato.recipebook.domain.logic

import io.github.otato.recipebook.domain.model.Ingredient
import io.github.otato.recipebook.domain.model.RecipeContent

/** 文章を分けた結果（F-CL-05）。 */
data class ParsedRecipe(
    /** 料理名らしい行。見つからなければ null */
    val title: String?,
    val content: RecipeContent,
    /** 文章の中の URL（出典の候補） */
    val urls: List<String>,
    /** 材料・手順・ポイントのどれにも入らなかった行（あいさつ・説明など） */
    val leftover: String,
) {
    /** 材料か手順が 1 つでも取れたか */
    val hasRecipe: Boolean get() = content.ingredients.isNotEmpty() || content.steps.isNotEmpty()
}

/**
 * 貼り付けた・共有された文章を、料理名・何人分・材料・手順・ポイントに分ける（F-CL-05、要件 6.3）。
 *
 * AI（API）は使わず、決まった手がかりで分ける。主な対象は次の 2 つ。
 * - AI の回答（Markdown の見出し・太字・箇条書き・番号付きの手順）
 * - レシピサイトの文章のコピー（「材料（2人分）」「作り方」の見出し。名前と分量が別の行になっているものも）
 *
 * うまく分けられなかった行は [ParsedRecipe.leftover] に残す（捨てない）。取り込んだ文章そのものは、別に保存する。
 */
object RecipeTextParser {

    private enum class Section { None, Ingredients, Steps, Tips }

    fun parse(text: String): ParsedRecipe {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var section = Section.None
        var title: String? = null
        var servings = ""
        val ingredients = mutableListOf<Ingredient>()
        val steps = mutableListOf<String>()
        val tips = mutableListOf<String>()
        val leftover = mutableListOf<String>()
        var stepsNumbered = false
        var pendingStepNumber = false
        var previousBlank = true
        var seenSection = false

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            i++
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                previousBlank = true
                continue
            }
            if (SEPARATOR.matches(trimmed)) {
                previousBlank = true
                continue
            }
            val wasBlank = previousBlank
            previousBlank = false

            // URL だけの行は、出典の候補として別に拾うので、ここでは読み飛ばす
            if (URL_ONLY.matches(trimmed)) continue

            val isMarkdownHeading = trimmed.startsWith("#")
            val isBoldOnly = BOLD_ONLY.matches(trimmed)
            val clean = stripDecorations(trimmed)
            if (clean.isEmpty()) continue

            // 見出し（材料・作り方・ポイント）
            val heading = sectionHeading(clean, isMarkdownHeading || isBoldOnly || isBracketed(trimmed))
            if (heading != null) {
                section = heading.first
                seenSection = true
                if (section == Section.Ingredients && servings.isEmpty()) servings = findServings(heading.second) ?: ""
                if (section == Section.Steps) {
                    stepsNumbered = false
                    pendingStepNumber = false
                }
                continue
            }

            // 単独の「2人分」の行
            if (section != Section.Steps && section != Section.Tips) {
                val alone = SERVINGS_ALONE.matchEntire(clean)
                if (alone != null) {
                    if (servings.isEmpty()) servings = alone.groupValues[1].trim()
                    continue
                }
            }

            when (section) {
                Section.None -> {
                    val candidate = titleCandidate(clean, isMarkdownHeading || isBoldOnly)
                    if (title == null && !seenSection && candidate != null) {
                        title = candidate
                    } else {
                        leftover += trimmed
                    }
                }

                Section.Ingredients -> {
                    val body = stripListMarker(clean)
                    when {
                        isGroupHeading(trimmed, body, isMarkdownHeading || isBoldOnly || isBracketed(trimmed)) ->
                            ingredients += Ingredient(name = groupHeadingName(body), isHeading = true)

                        // 材料のあとの、あいさつや説明の文
                        looksLikeSentence(body) -> leftover += trimmed

                        else -> {
                            var ingredient = splitIngredient(body)
                            // 名前と分量が別の行（レシピサイトのコピー）。次の行が分量だけなら、まとめる
                            if (ingredient.amount.isEmpty() && i < lines.size) {
                                val next = stripDecorations(lines[i].trim())
                                if (next.isNotEmpty() && AMOUNT_ONLY.matches(next)) {
                                    ingredient = ingredient.copy(amount = next)
                                    i++
                                }
                            }
                            if (ingredient.name.isNotEmpty()) ingredients += ingredient
                        }
                    }
                }

                Section.Steps -> {
                    if (isMarkdownHeading || isBoldOnly) {
                        // 手順の途中の、別の見出し（「ポイント」以外）。手順はここで終わり
                        section = Section.None
                        leftover += trimmed
                        continue
                    }
                    val number = STEP_NUMBER_ONLY.matchEntire(clean)
                    val numbered = STEP_NUMBERED.matchEntire(clean)
                    val bullet = LIST_MARKER.find(clean)
                    when {
                        // 「1」だけの行（レシピサイトのコピー）。次の行が、その手順
                        number != null -> {
                            pendingStepNumber = true
                            stepsNumbered = true
                        }

                        numbered != null -> {
                            stepsNumbered = true
                            pendingStepNumber = false
                            val body = numbered.groupValues[1].trim()
                            if (body.isNotEmpty()) steps += body else pendingStepNumber = true
                        }

                        pendingStepNumber -> {
                            steps += clean
                            pendingStepNumber = false
                        }

                        // 番号付きの手順の下に、字下げした箇条書き（AI の回答に多い）。その手順の続きにする
                        bullet != null && stepsNumbered && steps.isNotEmpty() && raw.first().isWhitespace() ->
                            steps[steps.lastIndex] = steps.last() + "\n" + stripListMarker(clean)

                        bullet != null -> steps += stripListMarker(clean)

                        // 番号付きの手順の、続きの行
                        steps.isNotEmpty() && stepsNumbered && !wasBlank -> steps[steps.lastIndex] = steps.last() + "\n" + clean

                        // 番号付きの手順のあと、空行をはさんだ文（「ぜひお試しください」など）
                        steps.isNotEmpty() && stepsNumbered -> leftover += trimmed

                        // 番号のない手順（1 行が 1 つの手順）
                        else -> steps += clean
                    }
                }

                Section.Tips -> {
                    // ポイントのあと、空行をはさんだ締めのあいさつ（「ぜひ作ってみてください」）
                    if (wasBlank && tips.isNotEmpty() && looksLikeChatter(clean)) leftover += trimmed
                    else tips += stripListMarker(clean)
                }
            }
        }

        if (title == null) title = quotedTitle(text)

        return ParsedRecipe(
            title = title?.let(::cleanTitle)?.takeIf { it.isNotEmpty() },
            content = RecipeContent(
                servings = servings,
                ingredients = ingredients,
                steps = steps,
                tips = tips.joinToString("\n"),
            ),
            urls = Urls.findAll(text),
            leftover = leftover.joinToString("\n"),
        )
    }

    /**
     * 材料の 1 行を、名前と分量に分ける。分けられなければ、全部を名前にする。
     * 例: 「鶏むね肉：1枚」「鶏むね肉　1枚」「鶏むね肉 … 1枚」「しょうゆ大さじ2」「塩 少々」
     */
    fun splitIngredient(line: String): Ingredient {
        val text = stripListMarker(line.replace("**", "").trim())
        // ① 「：」「:」
        COLON.find(text)?.let { m ->
            val name = text.substring(0, m.range.first).trim()
            val amount = text.substring(m.range.last + 1).trim()
            if (name.isNotEmpty() && amount.isNotEmpty()) return Ingredient(name, amount)
        }
        // ② 「…」「・・・」、タブ、全角の空白、2 つ以上の空白
        GAP.find(text)?.let { m ->
            val name = text.substring(0, m.range.first).trim()
            val amount = text.substring(m.range.last + 1).trim()
            if (name.isNotEmpty() && amount.isNotEmpty()) return Ingredient(name, amount)
        }
        // ③ 1 つの空白で、うしろが分量らしいとき
        val space = text.lastIndexOf(' ')
        if (space > 0) {
            val name = text.substring(0, space).trim()
            val amount = text.substring(space + 1).trim()
            if (name.isNotEmpty() && AMOUNT_ONLY.matches(amount)) return Ingredient(name, amount)
        }
        // ④ 空白なしで、うしろが分量（「しょうゆ大さじ2」「卵2個」「塩少々」）
        AMOUNT_TAIL.matchEntire(text)?.let { m ->
            val name = m.groupValues[1].trim()
            val amount = m.groupValues[2].trim()
            if (name.isNotEmpty() && name.last() !in NUMBER_CHARS) return Ingredient(name, amount)
        }
        return Ingredient(text, "")
    }

    // ── 見出し ──

    private val INGREDIENT_WORDS = listOf("材料", "用意するもの", "食材", "ingredients")
    private val STEP_WORDS = listOf("作り方", "つくり方", "手順", "調理手順", "調理方法", "steps", "instructions", "directions", "method")
    private val TIP_WORDS = listOf("ポイント", "コツ", "メモ", "注意", "アレンジ", "tips", "point")

    /** 見出しなら、その種類と、キーワードのうしろの文字（「（2人分）」など）を返す。 */
    private fun sectionHeading(clean: String, decorated: Boolean): Pair<Section, String>? {
        val lower = clean.lowercase().trimEnd(':', '：')
        fun match(words: List<String>): String? {
            for (word in words) {
                if (!lower.startsWith(word)) continue
                val rest = clean.trimEnd(':', '：').substring(word.length)
                if (rest.isEmpty()) return rest
                val first = rest.first()
                if ((first in HEADING_REST_STARTS || first.isWhitespace()) && rest.length <= 24) return rest
            }
            return null
        }
        match(INGREDIENT_WORDS)?.let { return Section.Ingredients to it }
        match(STEP_WORDS)?.let { return Section.Steps to it }
        match(TIP_WORDS)?.let { return Section.Tips to it }
        // 「作り方のコツ」「おいしく作るポイント」など、終わりがポイントの見出し（飾りのある行だけ）
        if (decorated && clean.length <= 20 && TIP_WORDS.any { lower.endsWith(it) }) return Section.Tips to ""
        return null
    }

    private const val HEADING_REST_STARTS = "(（[【〈<＜《:：・/／"

    // ── 材料の中の見出し（「A」「【タレ】」「＜合わせ調味料＞」） ──

    private fun isGroupHeading(trimmed: String, body: String, decorated: Boolean): Boolean {
        if (body.isEmpty()) return false
        if (BRACKETED.matches(body)) return true
        if (GROUP_LETTER.matches(body)) return true
        if ((body.endsWith("：") || body.endsWith(":")) && body.length <= 16) return true
        // Markdown の見出し・太字だけの行で、分量がない
        if (decorated && splitIngredient(body).amount.isEmpty() && body.length <= 16) return true
        // 「* **A**」のような、箇条書きの太字だけの行
        if (LIST_BOLD_ONLY.matches(trimmed)) return true
        return false
    }

    private fun groupHeadingName(body: String): String {
        var name = body.trimEnd(':', '：').trim()
        if (BRACKETED.matches(name)) name = name.drop(1).dropLast(1).trim()
        return name
    }

    // ── 料理名 ──

    private fun titleCandidate(clean: String, decorated: Boolean): String? {
        if (looksLikeChatter(clean)) return null
        if (decorated) return clean
        // 飾りのない行は、短くて、文になっていないものだけ
        if (clean.length <= 30 && !looksLikeSentence(clean)) return clean
        return null
    }

    /** 「はい、『鶏むね肉のねぎ塩炒め』のレシピをご紹介します」のような、かぎかっこの中 */
    private fun quotedTitle(text: String): String? {
        val firstLines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        for (line in firstLines) {
            QUOTED.find(line)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun cleanTitle(title: String): String {
        var t = title.trim().trim('「', '」', '『', '』', '"', '“', '”')
        for (suffix in TITLE_SUFFIXES) {
            if (t.endsWith(suffix) && t.length > suffix.length + 1) {
                t = t.dropLast(suffix.length)
                break
            }
        }
        return t.trim()
    }

    private val TITLE_SUFFIXES = listOf("のレシピ", "の作り方", "レシピ")

    private fun looksLikeChatter(line: String): Boolean =
        CHATTER_WORDS.any { line.contains(it) } || line.startsWith("はい") || line.startsWith("もちろん")

    private val CHATTER_WORDS = listOf("ご紹介", "しますね", "ですね", "いかが", "お試し", "ましょう", "ください")

    private fun looksLikeSentence(line: String): Boolean =
        line.length > 40 || SENTENCE_END.containsMatchIn(line) || looksLikeChatter(line)

    // ── 何人分 ──

    private fun findServings(text: String): String? = SERVINGS.find(text)?.value?.trim()

    // ── 飾りを外す ──

    /** Markdown の見出し・太字・引用、行頭と行末の飾り（■●【】など）を外す。 */
    private fun stripDecorations(line: String): String {
        var s = line.trim()
        s = s.trimStart('#', '>').trim()
        s = s.replace("**", "").replace("__", "")
        // 見出しを囲む【】だけの行は、中身にする（【材料】→ 材料）
        if (s.startsWith("【") && s.endsWith("】") && s.count { it == '【' } == 1) {
            s = s.drop(1).dropLast(1)
        } else if (s.startsWith("【") && s.indexOf('】') in 2..12) {
            // 「【材料】2人前」→「材料 2人前」
            val end = s.indexOf('】')
            s = s.substring(1, end) + " " + s.substring(end + 1).trim()
        }
        s = s.trimStart('■', '□', '◆', '◇', '▼', '▽', '▶', '▷', '♦').trim()
        return s
    }

    private fun stripListMarker(line: String): String = LIST_MARKER.replaceFirst(line, "").trim()

    private fun isBracketed(line: String): Boolean = line.startsWith("【") || line.startsWith("■") || line.startsWith("◆")

    // ── 正規表現 ──

    private val SEPARATOR = Regex("""^[-*_=ー─━]{3,}$""")
    private val URL_ONLY = Regex("""^(?:[-*・•]\s*)?<?https?://\S+>?$""")
    private val BOLD_ONLY = Regex("""^\*\*[^*]+\*\*[:：]?$""")
    private val LIST_BOLD_ONLY = Regex("""^[-*・•]\s*\*\*[^*]+\*\*[:：]?$""")
    private val LIST_MARKER = Regex("""^(?:[-*+・•●○▪︎]|•)\s*""")
    private val BRACKETED = Regex("""^[【＜<〈《\[［(（].+[】＞>〉》\]］)）]$""")
    private val GROUP_LETTER = Regex("""^[◎●○★☆◇◆]?[A-Za-zＡ-Ｚａ-ｚ][:：]?$""")
    private val COLON = Regex("""\s*[：:]\s*""")
    private val GAP = Regex("""\s*(?:…+|‥+|・{2,}|\.{3,}|\t+| {2,}|　+)\s*""")
    private val SENTENCE_END = Regex("""[。！？!?]$|[。！？!?]\s""")
    private val QUOTED = Regex("""[「『]([^」』]{2,30})[」』]""")

    private const val AMOUNT_WORDS = "少々|適量|適宜|ひとつまみ|ひとつかみ|少量|お好みで|好みで|お好みの量|たっぷり|各少々|各適量"
    private const val MEASURE_PREFIX = "大さじ|小さじ|大匙|小匙|カップ|約|各"
    private const val NUMBER_START = "[0-9０-９½¼¾⅓⅔⅛]"
    private const val NUMBER_CHARS = "0123456789０１２３４５６７８９/／.．"

    /** 分量だけの文字列（「1枚」「大さじ2」「少々」「200g」「1/2個」） */
    private val AMOUNT_ONLY = Regex(
        """^(?:(?:$MEASURE_PREFIX)\s*$NUMBER_START?.*|$NUMBER_START.*|$AMOUNT_WORDS)$""",
    )

    /** うしろが分量になっている行（「しょうゆ大さじ2」「卵2個」「塩少々」） */
    private val AMOUNT_TAIL = Regex(
        """^(.+?)((?:$MEASURE_PREFIX)\s*$NUMBER_START.*|$NUMBER_START[0-9０-９/／.・〜~\-]*\s*[^\s0-9０-９].*|$NUMBER_START[0-9０-９/／.]*|$AMOUNT_WORDS)$""",
    )

    private val SERVINGS = Regex("""[0-9０-９一二三四五六七八九十]+\s*(?:[〜~～\-－]\s*[0-9０-９一二三四五六七八九十]+)?\s*(?:人分|人前|個分|枚分|本分|皿分|食分|杯分|切れ分)""")
    private val SERVINGS_ALONE = Regex("""^[(（\[【]?(?:分量\s*[:：]?\s*)?($SERVINGS_SOURCE)[)）\]】]?$""")
    private const val SERVINGS_SOURCE = """[0-9０-９一二三四五六七八九十]+\s*(?:[〜~～\-－]\s*[0-9０-９一二三四五六七八九十]+)?\s*(?:人分|人前|個分|枚分|本分|皿分|食分|杯分|切れ分)"""

    private val STEP_NUMBER_ONLY = Regex("""^(?:STEP|Step|step|ステップ|手順)?\s*(?:[0-9０-９]{1,2}|[①-⑳]|[⑴-⒇])\s*[.．、)）]?$""")
    private val STEP_NUMBERED = Regex(
        """^(?:(?:STEP|Step|step|ステップ|手順)\s*[0-9０-９]{1,2}\s*[.．、)）:：]?\s*|[0-9０-９]{1,2}\s*[.．、)）:：]\s*|[0-9０-９]{1,2}\s+|[(（][0-9０-９]{1,2}[)）]\s*|[①-⑳⑴-⒇]\s*)(.*)$""",
    )
}
