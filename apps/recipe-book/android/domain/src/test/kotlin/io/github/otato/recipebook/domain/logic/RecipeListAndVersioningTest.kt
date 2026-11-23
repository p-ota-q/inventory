package io.github.otato.recipebook.domain.logic

import io.github.otato.recipebook.domain.model.Ingredient
import io.github.otato.recipebook.domain.model.Rating
import io.github.otato.recipebook.domain.model.RecipeSort
import io.github.otato.recipebook.domain.model.RecipeStatus
import io.github.otato.recipebook.domain.model.RecipeSummary
import io.github.otato.recipebook.domain.model.Tag
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeListAndVersioningTest {

    private val chicken = Tag("t1", "鶏肉", 0)
    private val quick = Tag("t2", "10分", 1)

    private fun summary(
        id: String,
        status: RecipeStatus = RecipeStatus.Want,
        tags: List<Tag> = emptyList(),
        cookCount: Int = 0,
        lastCookedOn: LocalDate? = null,
        lastRating: Rating? = null,
        createdAt: Long = 0,
        text: String = id,
    ) = RecipeSummary(
        id = id, title = id, status = status, tags = tags, cookCount = cookCount, lastCookedOn = lastCookedOn,
        lastRating = lastRating, latestNextNote = null, createdAt = Instant.ofEpochSecond(createdAt),
        updatedAt = Instant.ofEpochSecond(createdAt), searchText = SearchText.normalize(text),
    )

    @Test
    fun `「作った」の絞り込みには定番も入る`() {
        val list = listOf(summary("a", RecipeStatus.Want), summary("b", RecipeStatus.Made), summary("c", RecipeStatus.Staple))
        assertEquals(setOf("b", "c"), RecipeList.apply(list, status = StatusFilter.Made).map { it.id }.toSet())
        assertEquals(listOf("c"), RecipeList.apply(list, status = StatusFilter.Staple).map { it.id })
    }

    @Test
    fun `タグはすべて付いているものだけ、検索は材料などの文章でも見つける`() {
        val list = listOf(
            summary("a", tags = listOf(chicken, quick), text = "ねぎ塩炒め 鶏むね肉 長ネギ"),
            summary("b", tags = listOf(chicken), text = "親子丼 鶏もも肉 卵"),
        )
        assertEquals(listOf("a"), RecipeList.apply(list, tagIds = setOf("t1", "t2")).map { it.id })
        assertEquals(listOf("a"), RecipeList.apply(list, query = "ねぎ").map { it.id })
    }

    @Test
    fun `最近作った順では、作ったことのないものをうしろにする`() {
        val list = listOf(
            summary("never", createdAt = 30),
            summary("old", lastCookedOn = LocalDate.of(2026, 11, 1), createdAt = 10),
            summary("new", lastCookedOn = LocalDate.of(2026, 11, 20), createdAt = 20),
        )
        assertEquals(listOf("new", "old", "never"), RecipeList.apply(list, sort = RecipeSort.RecentlyCooked).map { it.id })
    }

    @Test
    fun `評価の良い順は、定番が先`() {
        val list = listOf(
            summary("meh", RecipeStatus.Made, lastRating = Rating.Meh),
            summary("again", RecipeStatus.Made, lastRating = Rating.Again),
            summary("staple", RecipeStatus.Staple, lastRating = Rating.Okay),
        )
        assertEquals(listOf("staple", "again", "meh"), RecipeList.apply(list, sort = RecipeSort.BestRated).map { it.id })
    }

    @Test
    fun `作ってから直したときだけ、新しい版にする`() {
        val versionAt = Instant.ofEpochSecond(100)
        assertFalse(Versioning.needsNewVersion(versionAt, lastCookLogCreatedAt = null, fromCookLog = false))
        assertFalse(Versioning.needsNewVersion(versionAt, Instant.ofEpochSecond(50), fromCookLog = false))
        assertTrue(Versioning.needsNewVersion(versionAt, Instant.ofEpochSecond(150), fromCookLog = false))
        assertTrue(Versioning.needsNewVersion(versionAt, null, fromCookLog = true))
    }

    @Test
    fun `元の版との材料の違い`() {
        val original = listOf(Ingredient("鶏むね肉", "1枚"), Ingredient("塩", "小さじ1/2"), Ingredient("ごま油", "大さじ1"))
        val current = listOf(Ingredient("鶏むね肉", "1枚"), Ingredient("塩", "小さじ1/3"), Ingredient("長ねぎ", "1本"))

        val diff = Versioning.diffIngredients(original, current)

        assertEquals(
            listOf(
                Versioning.IngredientChange.Same(Ingredient("鶏むね肉", "1枚")),
                Versioning.IngredientChange.Changed(Ingredient("塩", "小さじ1/3"), before = "小さじ1/2"),
                Versioning.IngredientChange.Added(Ingredient("長ねぎ", "1本")),
                Versioning.IngredientChange.Removed(Ingredient("ごま油", "大さじ1")),
            ),
            diff,
        )
    }
}
