package io.github.otato.recipebook.domain.logic

import io.github.otato.recipebook.domain.model.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedAndUrlsTest {

    @Test
    fun `YouTube の URL は、共有のしかたが違っても同じになる`() {
        val a = Urls.normalize("https://youtu.be/abc123?si=XYZ")
        val b = Urls.normalize("https://www.youtube.com/watch?v=abc123&feature=share")
        val c = Urls.normalize("https://m.youtube.com/shorts/abc123")
        assertEquals("youtube:abc123", a)
        assertEquals(a, b)
        assertEquals(a, c)
    }

    @Test
    fun `ほかのサイトは、追跡用のパラメーターと末尾の違いをなくす`() {
        assertEquals(
            Urls.normalize("https://cookpad.com/recipe/12345"),
            Urls.normalize("http://www.cookpad.com/recipe/12345/?utm_source=line#steps"),
        )
        // ページを区別するパラメーターは残す
        assertEquals("example.com/r?id=3", Urls.normalize("https://example.com/r?id=3&utm_medium=x"))
    }

    @Test
    fun `YouTube から共有された URL と題名`() {
        val shared = SharedContentReader.read("https://youtu.be/abc123?si=XYZ", "【10分】鶏むね肉のねぎ塩炒め - YouTube")
        assertEquals("https://youtu.be/abc123?si=XYZ", shared.url)
        assertEquals("【10分】鶏むね肉のねぎ塩炒め", shared.title)
        assertEquals(SourceKind.YouTube, shared.kind)
        assertNull(shared.parsed)
    }

    @Test
    fun `本文に題名と URL が入っている共有`() {
        val shared = SharedContentReader.read("簡単！親子丼 by たろう https://cookpad.com/recipe/12345", null)
        assertEquals("簡単！親子丼", shared.title)
        assertEquals(SourceKind.Web, shared.kind)
    }

    @Test
    fun `AI の回答の共有は、分けて取り込む`() {
        val text = """
            ## 親子丼
            材料（1人分）
            - 鶏もも肉：100g
            - 卵：2個
            作り方
            1. 鶏肉を煮る。
            2. 卵でとじる。
        """.trimIndent()
        val shared = SharedContentReader.read(text, null)
        assertEquals("親子丼", shared.title)
        assertEquals(SourceKind.Ai, shared.kind)
        val parsed = assertNotNull(shared.parsed)
        assertEquals(2, parsed.content.ingredients.size)
        assertEquals(2, parsed.content.steps.size)
    }

    @Test
    fun `検索は、カタカナとひらがな、全角と半角の違いを見ない`() {
        val haystack = SearchText.normalize("長ネギ ＡＢＣソース 鶏むね肉")
        assertEquals(true, SearchText.matches(haystack, "ねぎ"))
        assertEquals(true, SearchText.matches(haystack, "abc"))
        assertEquals(true, SearchText.matches(haystack, "ねぎ　鶏むね"))
        assertEquals(false, SearchText.matches(haystack, "豚"))
    }
}
