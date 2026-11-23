package io.github.otato.recipebook.domain.logic

import io.github.otato.recipebook.domain.model.Ingredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeTextParserTest {

    @Test
    fun `AI の回答（Markdown）を分ける`() {
        val text = """
            はい、鶏むね肉を使った「ねぎ塩炒め」のレシピをご紹介しますね。

            ## 鶏むね肉のねぎ塩炒め

            **材料（2人分）**
            * 鶏むね肉：1枚（約300g）
            * 長ねぎ：1本
            * 片栗粉：大さじ1
            * **A**
                * 塩：小さじ1/2
                * ごま油：大さじ1

            **作り方**
            1. **下ごしらえ：** 鶏むね肉はそぎ切りにし、片栗粉をまぶします。
            2. 長ねぎはみじん切りにして、Aと混ぜます。
            3. フライパンで鶏肉を焼き、ねぎだれを絡めます。

            ### ポイント
            * 片栗粉を多めにすると、しっとりします。

            ぜひお試しください！
        """.trimIndent()

        val parsed = RecipeTextParser.parse(text)

        assertEquals("鶏むね肉のねぎ塩炒め", parsed.title)
        assertEquals("2人分", parsed.content.servings)
        assertEquals(
            listOf(
                Ingredient("鶏むね肉", "1枚（約300g）"),
                Ingredient("長ねぎ", "1本"),
                Ingredient("片栗粉", "大さじ1"),
                Ingredient("A", isHeading = true),
                Ingredient("塩", "小さじ1/2"),
                Ingredient("ごま油", "大さじ1"),
            ),
            parsed.content.ingredients,
        )
        assertEquals(3, parsed.content.steps.size)
        assertEquals("下ごしらえ： 鶏むね肉はそぎ切りにし、片栗粉をまぶします。", parsed.content.steps[0])
        assertEquals("片栗粉を多めにすると、しっとりします。", parsed.content.tips)
        assertTrue(parsed.leftover.contains("ご紹介しますね"))
        assertTrue(parsed.leftover.contains("ぜひお試しください"))
    }

    @Test
    fun `レシピサイトのコピー（名前と分量が別の行・番号だけの行）を分ける`() {
        val text = """
            豚こまとキャベツのみそ炒め
            材料 (2人分)
            豚こま切れ肉
            200g
            キャベツ
            1/4個
            ★みそ
            大さじ1.5
            ★みりん
            大さじ1
            作り方
            1
            キャベツはざく切りにする。
            2
            豚肉を炒め、キャベツと★を加えて炒め合わせる。
        """.trimIndent()

        val parsed = RecipeTextParser.parse(text)

        assertEquals("豚こまとキャベツのみそ炒め", parsed.title)
        assertEquals("2人分", parsed.content.servings)
        assertEquals(
            listOf(
                Ingredient("豚こま切れ肉", "200g"),
                Ingredient("キャベツ", "1/4個"),
                Ingredient("★みそ", "大さじ1.5"),
                Ingredient("★みりん", "大さじ1"),
            ),
            parsed.content.ingredients,
        )
        assertEquals(
            listOf("キャベツはざく切りにする。", "豚肉を炒め、キャベツと★を加えて炒め合わせる。"),
            parsed.content.steps,
        )
    }

    @Test
    fun `【】の見出しと、空白で区切った材料`() {
        val text = """
            【材料】2人前
            ・卵　3個
            ・砂糖 大さじ1
            ・塩少々
            【タレ】
            ・しょうゆ大さじ2
            【作り方】
            ①卵を割りほぐす。
            ②砂糖と塩を混ぜて焼く。
        """.trimIndent()

        val parsed = RecipeTextParser.parse(text)

        assertEquals("2人前", parsed.content.servings)
        assertEquals(
            listOf(
                Ingredient("卵", "3個"),
                Ingredient("砂糖", "大さじ1"),
                Ingredient("塩", "少々"),
                Ingredient("タレ", isHeading = true),
                Ingredient("しょうゆ", "大さじ2"),
            ),
            parsed.content.ingredients,
        )
        assertEquals(listOf("卵を割りほぐす。", "砂糖と塩を混ぜて焼く。"), parsed.content.steps)
    }

    @Test
    fun `番号付きの手順の下の箇条書きは、その手順の続きにする`() {
        val text = """
            作り方
            1. 下ごしらえ
               * 玉ねぎを薄切りにする
               * 肉に塩をふる
            2. 炒める
        """.trimIndent()

        val parsed = RecipeTextParser.parse(text)

        assertEquals(listOf("下ごしらえ\n玉ねぎを薄切りにする\n肉に塩をふる", "炒める"), parsed.content.steps)
    }

    @Test
    fun `レシピの形でない文章は、捨てずに残す`() {
        val text = "昨日食べた親子丼がおいしかった。今度、卵を半熟にして作ってみたい。"

        val parsed = RecipeTextParser.parse(text)

        assertTrue(!parsed.hasRecipe)
        assertNull(parsed.title)
        assertEquals(text, parsed.leftover)
    }

    @Test
    fun `URL は出典の候補として拾う`() {
        val text = """
            ねぎ塩炒め
            https://www.youtube.com/watch?v=abc123
            材料
            鶏むね肉 1枚
        """.trimIndent()

        val parsed = RecipeTextParser.parse(text)

        assertEquals(listOf("https://www.youtube.com/watch?v=abc123"), parsed.urls)
        assertEquals("ねぎ塩炒め", parsed.title)
        assertEquals("", parsed.leftover)
    }

    @Test
    fun `材料の行を名前と分量に分ける`() {
        assertEquals(Ingredient("鶏もも肉", "1枚"), RecipeTextParser.splitIngredient("鶏もも肉：1枚"))
        assertEquals(Ingredient("にんにく", "1かけ"), RecipeTextParser.splitIngredient("にんにく … 1かけ"))
        assertEquals(Ingredient("しょうゆ", "大さじ2"), RecipeTextParser.splitIngredient("しょうゆ大さじ2"))
        assertEquals(Ingredient("卵", "2個"), RecipeTextParser.splitIngredient("卵2個"))
        assertEquals(Ingredient("塩こしょう", "少々"), RecipeTextParser.splitIngredient("塩こしょう少々"))
        assertEquals(Ingredient("水", "200ml"), RecipeTextParser.splitIngredient("- 水 200ml"))
        assertEquals(Ingredient("鶏むね肉（皮なし）", "1枚"), RecipeTextParser.splitIngredient("鶏むね肉（皮なし）1枚"))
        // 分けられないものは、全部を名前にする
        assertEquals(Ingredient("3色そぼろ", ""), RecipeTextParser.splitIngredient("3色そぼろ"))
        assertEquals(Ingredient("1/2カップ", ""), RecipeTextParser.splitIngredient("1/2カップ"))
    }
}
