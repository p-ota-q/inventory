package io.github.otato.recipebook.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otato.recipebook.di.DeletedRecipe
import io.github.otato.recipebook.domain.model.RecipeDetail
import io.github.otato.recipebook.domain.model.RecipeStatus
import io.github.otato.recipebook.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    /** 削除した・見つからないときは null */
    val detail: RecipeDetail? = null,
    /** 元のレシピとの違いを出すか（F-RC-08） */
    val showDiff: Boolean = false,
)

/** 取り消せる操作のあとに出す知らせ。 */
data class UndoMessage(val text: String, val undo: () -> Unit)

class RecipeDetailViewModel(
    private val recipeId: String,
    private val recipes: RecipeRepository,
    private val deletedRecipe: MutableStateFlow<DeletedRecipe?>,
) : ViewModel() {

    private val showDiff = MutableStateFlow(false)

    val state: StateFlow<DetailUiState> = combine(recipes.observeDetail(recipeId), showDiff) { detail, diff ->
        DetailUiState(loading = false, detail = detail, showDiff = diff && detail?.originalVersion != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private val _messages = MutableSharedFlow<UndoMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<UndoMessage> = _messages.asSharedFlow()

    fun toggleDiff() {
        showDiff.value = !showDiff.value
    }

    fun setStatus(status: RecipeStatus) {
        viewModelScope.launch { recipes.setStatus(recipeId, status) }
    }

    /** レシピを削除する。ホームで「元に戻す」を出す（F-DA-03） */
    fun deleteRecipe(onDone: () -> Unit) {
        val title = state.value.detail?.recipe?.title.orEmpty()
        viewModelScope.launch {
            recipes.delete(recipeId)
            deletedRecipe.value = DeletedRecipe(recipeId, title)
            onDone()
        }
    }

    fun deleteCookLog(cookLogId: String) {
        viewModelScope.launch {
            recipes.deleteCookLog(cookLogId)
            _messages.emit(UndoMessage("作った記録を削除しました") { viewModelScope.launch { recipes.undoDeleteCookLog(cookLogId) } })
        }
    }
}
