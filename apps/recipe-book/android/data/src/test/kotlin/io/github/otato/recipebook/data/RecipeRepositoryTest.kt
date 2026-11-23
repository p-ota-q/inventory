package io.github.otato.recipebook.data

import io.github.otato.recipebook.data.testing.MutableClock
import io.github.otato.recipebook.data.testing.TestDatabase
import io.github.otato.recipebook.domain.logic.SearchText
import io.github.otato.recipebook.domain.model.BackupFormatException
import io.github.otato.recipebook.domain.model.CookLogInput
import io.github.otato.recipebook.domain.model.Ingredient
import io.github.otato.recipebook.domain.model.Rating
import io.github.otato.recipebook.domain.model.RecipeContent
import io.github.otato.recipebook.domain.model.RecipeDraft
import io.github.otato.recipebook.domain.model.RecipeStatus
import io.github.otato.recipebook.domain.model.SourceInput
import io.github.otato.recipebook.domain.model.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeRepositoryTest {
    private val clock = MutableClock()
    private val data = TestDatabase.create(clock = clock)
    private val recipes = data.recipes

    private val content = RecipeContent(
        servings = "2人分",
        ingredients = listOf(Ingredient("鶏むね肉", "1枚"), Ingredient("A", isHeading = true), Ingredient("塩", "小さじ1/2")),
        steps = listOf("切る", "焼く"),
        tips = "片栗粉は多めに",
    )

    private fun draft(title: String = "ねぎ塩炒め", c: RecipeContent = content) = RecipeDraft(
        title = title,
        content = c,
        sources = listOf(SourceInput(SourceKind.YouTube, "https://youtu.be/abc?si=1", "動画")),
    )

    @Test
    fun `作ったレシピを、そのまま読み出せる`() = runTest {
        val id = recipes.create(draft().copy(importedText = "元の文章"))

        val detail = assertNotNull(recipes.getDetail(id))

        assertEquals("ねぎ塩炒め", detail.recipe.title)
        assertEquals(RecipeStatus.Want, detail.recipe.status)
        assertEquals(content, detail.recipe.content)
        assertEquals(1, detail.versionCount)
        assertNull(detail.originalVersion)
        assertEquals(SourceKind.YouTube, detail.recipe.sources.single().kind)
        assertEquals("元の文章", detail.importedTexts.single().text)
    }

    @Test
    fun `まだ作っていないうちに直すと、版は増えない`() = runTest {
        val id = recipes.create(draft())
        clock.advance()

        recipes.update(id, draft(c = content.copy(tips = "直した")))

        val detail = assertNotNull(recipes.getDetail(id))
        assertEquals(1, detail.versionCount)
        assertEquals("直した", detail.recipe.content.tips)
    }

    @Test
    fun `作ってから直すと新しい版になり、元の版と比べられる。前の版にも戻せる`() = runTest {
        val id = recipes.create(draft())
        clock.advance()
        recipes.addCookLog(id, CookLogInput(LocalDate.of(2026, 11, 20), Rating.Okay, changes = "塩を減らした"))
        clock.advance()

        val less = content.copy(ingredients = content.ingredients.map { if (it.name == "塩") it.copy(amount = "小さじ1/3") else it })
        recipes.update(id, draft(c = less).copy(versionReason = "しょっぱかった"))
        // 同じ日のうちに、もう一度直しても、版は増えない
        clock.advance()
        recipes.update(id, draft(c = less.copy(tips = "片栗粉は多めに。塩は控えめ")))

        val detail = assertNotNull(recipes.getDetail(id))
        assertEquals(2, detail.versionCount)
        assertEquals(2, detail.recipe.currentVersion.number)
        assertEquals("しょっぱかった", detail.recipe.currentVersion.reason)
        assertEquals("小さじ1/2", assertNotNull(detail.originalVersion).content.ingredients.last().amount)

        recipes.restoreVersion(id, detail.originalVersion!!.id)
        val restored = assertNotNull(recipes.getDetail(id))
        assertEquals(3, restored.versionCount)
        assertEquals(content, restored.recipe.content)
    }

    @Test
    fun `作った記録から反映すると、必ず新しい版になる`() = runTest {
        val id = recipes.create(draft())
        clock.advance()

        recipes.update(id, draft(c = content.copy(tips = "反映")).copy(fromCookLogId = "log"))

        assertEquals(2, assertNotNull(recipes.getDetail(id)).versionCount)
    }

    @Test
    fun `作った記録を付けると「作った」になり、一覧に回数と次のメモが出る`() = runTest {
        val id = recipes.create(draft())
        recipes.addCookLog(id, CookLogInput(LocalDate.of(2026, 11, 2), Rating.Okay, "しょっぱい", nextNote = "塩を控えめに"))
        clock.advance()
        recipes.addCookLog(id, CookLogInput(LocalDate.of(2026, 11, 20), Rating.Again, "ちょうど良い"))

        val summary = recipes.observeSummaries().first().single()

        assertEquals(RecipeStatus.Made, summary.status)
        assertEquals(2, summary.cookCount)
        assertEquals(LocalDate.of(2026, 11, 20), summary.lastCookedOn)
        assertEquals(Rating.Again, summary.lastRating)
        assertEquals("塩を控えめに", summary.latestNextNote)
        // 材料・作った記録の文章でも検索できる
        assertTrue(SearchText.matches(summary.searchText, "鶏むね"))
        assertTrue(SearchText.matches(summary.searchText, "しょっぱい"))

        val detail = assertNotNull(recipes.getDetail(id))
        assertEquals("塩を控えめに", detail.latestNextNote?.nextNote)
        assertEquals(LocalDate.of(2026, 11, 20), detail.cookLogs.first().cookedOn)
    }

    @Test
    fun `定番は、作った記録を付けても定番のまま`() = runTest {
        val id = recipes.create(draft().copy(status = RecipeStatus.Staple))
        recipes.addCookLog(id, CookLogInput(LocalDate.of(2026, 11, 20)))
        assertEquals(RecipeStatus.Staple, assertNotNull(recipes.getDetail(id)).recipe.status)
    }

    @Test
    fun `同じ URL のレシピを見つける（共有のしかたが違っても）`() = runTest {
        val id = recipes.create(draft())
        assertEquals(id, recipes.findByUrl("https://www.youtube.com/watch?v=abc")?.id)
        assertNull(recipes.findByUrl("https://www.youtube.com/watch?v=other"))
    }

    @Test
    fun `削除は取り消せる。取り消さなければ、消える`() = runTest {
        val id = recipes.create(draft())
        recipes.delete(id)
        assertNull(recipes.getDetail(id))
        assertTrue(recipes.observeSummaries().first().isEmpty())

        recipes.undoDelete(id)
        assertNotNull(recipes.getDetail(id))

        recipes.delete(id)
        recipes.purgeDeleted()
        recipes.undoDelete(id)
        assertNull(recipes.getDetail(id))
    }

    @Test
    fun `タグを付けて、名前を変え、消す`() = runTest {
        val chicken = recipes.createTag("鶏肉")
        assertEquals(chicken, recipes.createTag(" 鶏肉 "))
        val quick = recipes.createTag("10分")
        val id = recipes.create(draft().copy(tagIds = listOf(chicken.id, quick.id)))

        recipes.renameTag(chicken.id, "とり肉")
        recipes.reorderTags(listOf(quick.id, chicken.id))
        assertEquals(listOf("10分", "とり肉"), assertNotNull(recipes.getDetail(id)).recipe.tags.map { it.name })

        recipes.deleteTag(quick.id)
        assertEquals(listOf("とり肉"), assertNotNull(recipes.getDetail(id)).recipe.tags.map { it.name })
    }

    @Test
    fun `バックアップを書き出して、別のデータベースに戻せる`() = runTest {
        val tag = recipes.createTag("鶏肉")
        val id = recipes.create(draft().copy(tagIds = listOf(tag.id), importedText = "元の文章"))
        recipes.addCookLog(id, CookLogInput(LocalDate.of(2026, 11, 20), Rating.Again, nextNote = "次は倍量"))
        clock.advance()
        recipes.update(id, draft(c = content.copy(tips = "版 2")))
        val json = data.backup.exportJson()

        val other = TestDatabase.create(ids = TestDatabase.sequentialIds("other"))
        other.recipes.create(draft(title = "消えるレシピ"))
        assertEquals(1, other.backup.inspectJson(json))
        other.backup.restoreJson(json)

        val restored = other.recipes.observeSummaries().first().single()
        assertEquals("ねぎ塩炒め", restored.title)
        val detail = assertNotNull(other.recipes.getDetail(restored.id))
        assertEquals(data.recipes.getDetail(id), detail)
    }

    @Test
    fun `ほかのファイルは読み込まない`() = runTest {
        assertFailsWith<BackupFormatException> { data.backup.restoreJson("{\"app\":\"training-log\",\"formatVersion\":1}") }
        assertFailsWith<BackupFormatException> { data.backup.restoreJson("not json") }
    }
}
