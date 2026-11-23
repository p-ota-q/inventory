package io.github.otato.recipebook.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otato.recipebook.di.DeletedRecipe
import io.github.otato.recipebook.domain.logic.RecipeList
import io.github.otato.recipebook.domain.logic.StatusFilter
import io.github.otato.recipebook.domain.model.RecipeSort
import io.github.otato.recipebook.domain.model.RecipeSummary
import io.github.otato.recipebook.domain.model.Tag
import io.github.otato.recipebook.domain.repository.RecipeRepository
import io.github.otato.recipebook.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    /** 絞り込む前の数（0 なら、はじめての案内を出す） */
    val totalCount: Int = 0,
    val recipes: List<RecipeSummary> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val query: String = "",
    val status: StatusFilter = StatusFilter.All,
    val selectedTagIds: Set<String> = emptySet(),
    val sort: RecipeSort = RecipeSort.Added,
)

/** ホーム（レシピの一覧）。検索・絞り込み・並べ替え（4.5）。 */
class HomeViewModel(
    private val recipes: RecipeRepository,
    private val settings: SettingsRepository,
    private val deletedRecipe: MutableStateFlow<DeletedRecipe?>,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val status = MutableStateFlow(StatusFilter.All)
    private val selectedTags = MutableStateFlow<Set<String>>(emptySet())

    private val filters = combine(query, status, selectedTags) { q, s, t -> Triple(q, s, t) }

    val state: StateFlow<HomeUiState> = combine(
        recipes.observeSummaries(),
        recipes.observeTags(),
        settings.observe().map { it.sort },
        filters,
    ) { all, tags, sort, (q, s, t) ->
        // 消したタグが選ばれたままにならないように
        val validTags = t.filterTo(mutableSetOf()) { id -> tags.any { it.id == id } }
        HomeUiState(
            loading = false,
            totalCount = all.size,
            recipes = RecipeList.apply(all, q, s, validTags, sort),
            tags = tags,
            query = q,
            status = s,
            selectedTagIds = validTags,
            sort = sort,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 削除したレシピ（「元に戻す」を出す） */
    val deleted: StateFlow<DeletedRecipe?> = deletedRecipe

    fun setQuery(value: String) {
        query.value = value
    }

    fun setStatus(value: StatusFilter) {
        status.value = value
    }

    fun toggleTag(tagId: String) {
        selectedTags.value = selectedTags.value.let { if (tagId in it) it - tagId else it + tagId }
    }

    fun setSort(sort: RecipeSort) {
        viewModelScope.launch { settings.update { it.copy(sort = sort) } }
    }

    fun undoDelete(recipe: DeletedRecipe) {
        deletedRecipe.value = null
        viewModelScope.launch { recipes.undoDelete(recipe.id) }
    }

    fun dismissDeleted() {
        deletedRecipe.value = null
    }
}
