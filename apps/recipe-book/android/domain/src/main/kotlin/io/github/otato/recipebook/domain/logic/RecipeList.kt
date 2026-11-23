package io.github.otato.recipebook.domain.logic

import io.github.otato.recipebook.domain.model.Rating
import io.github.otato.recipebook.domain.model.RecipeSort
import io.github.otato.recipebook.domain.model.RecipeStatus
import io.github.otato.recipebook.domain.model.RecipeSummary

/** 一覧の状態の絞り込み（F-SR-02）。「作った」には「定番」も入る（定番は、作ったレシピの中のお気に入りなので）。 */
enum class StatusFilter { All, Want, Made, Staple }

/** 一覧の検索・絞り込み・並べ替え（4.5）。 */
object RecipeList {
    fun apply(
        recipes: List<RecipeSummary>,
        query: String = "",
        status: StatusFilter = StatusFilter.All,
        tagIds: Set<String> = emptySet(),
        sort: RecipeSort = RecipeSort.Added,
    ): List<RecipeSummary> = recipes
        .filter { matchesStatus(it.status, status) }
        .filter { r -> tagIds.all { id -> r.tags.any { it.id == id } } }
        .filter { query.isBlank() || SearchText.matches(it.searchText, query) }
        .sortedWith(comparator(sort))

    fun matchesStatus(status: RecipeStatus, filter: StatusFilter): Boolean = when (filter) {
        StatusFilter.All -> true
        StatusFilter.Want -> status == RecipeStatus.Want
        StatusFilter.Made -> status == RecipeStatus.Made || status == RecipeStatus.Staple
        StatusFilter.Staple -> status == RecipeStatus.Staple
    }

    private fun comparator(sort: RecipeSort): Comparator<RecipeSummary> {
        val newest = compareByDescending<RecipeSummary> { it.createdAt }
        return when (sort) {
            RecipeSort.Added -> newest
            // 作ったことのないものは、うしろ
            RecipeSort.RecentlyCooked -> compareBy<RecipeSummary> { it.lastCookedOn == null }
                .thenByDescending { it.lastCookedOn }
                .then(newest)
            RecipeSort.MostCooked -> compareByDescending<RecipeSummary> { it.cookCount }.then(newest)
            // 定番を先に、そのあと最後の評価の良い順
            RecipeSort.BestRated -> compareBy<RecipeSummary> { if (it.status == RecipeStatus.Staple) 0 else 1 }
                .thenBy { ratingRank(it.lastRating) }
                .then(newest)
        }
    }

    private fun ratingRank(rating: Rating?): Int = when (rating) {
        Rating.Again -> 0
        Rating.Okay -> 1
        Rating.Meh -> 2
        null -> 3
    }
}
