package io.github.otato.recipebook.feature.cooklog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otato.recipebook.domain.model.CookLog
import io.github.otato.recipebook.domain.model.CookLogInput
import io.github.otato.recipebook.domain.model.Rating
import io.github.otato.recipebook.domain.repository.RecipeRepository
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** 作った記録の入力（F-LG-01〜05）。logId がなければ、新しく付ける。 */
class CookLogViewModel(
    private val recipeId: String,
    private val logId: String?,
    private val recipes: RecipeRepository,
    clock: Clock,
) : ViewModel() {

    var loading by mutableStateOf(true)
        private set
    var recipeTitle by mutableStateOf("")
        private set

    /** 前に書いた「次に作るときのメモ」（書くときの手がかりに出す。F-LG-04） */
    var previousNote by mutableStateOf<CookLog?>(null)
        private set

    var input by mutableStateOf(CookLogInput(cookedOn = LocalDate.now(clock)))
        private set

    val isNew: Boolean get() = logId == null

    init {
        viewModelScope.launch {
            val detail = recipes.getDetail(recipeId)
            recipeTitle = detail?.recipe?.title.orEmpty()
            previousNote = detail?.cookLogs?.firstOrNull { it.id != logId && it.nextNote.isNotBlank() }
            if (logId != null) {
                recipes.getCookLog(logId)?.let {
                    input = CookLogInput(it.cookedOn, it.rating, it.impression, it.changes, it.nextNote)
                }
            }
            loading = false
        }
    }

    fun setDate(date: LocalDate) {
        input = input.copy(cookedOn = date)
    }

    /** 同じ評価をもう一度押したら、外す */
    fun toggleRating(rating: Rating) {
        input = input.copy(rating = if (input.rating == rating) null else rating)
    }

    fun setImpression(value: String) {
        input = input.copy(impression = value)
    }

    fun setChanges(value: String) {
        input = input.copy(changes = value)
    }

    fun setNextNote(value: String) {
        input = input.copy(nextNote = value)
    }

    /** 保存する。[onSaved] には、記録の id と、レシピへの反映を聞くかどうか（新しい記録で、変えたところがあるとき）を渡す。 */
    fun save(onSaved: (logId: String, askReflect: Boolean) -> Unit) {
        viewModelScope.launch {
            if (logId == null) {
                val id = recipes.addCookLog(recipeId, input)
                onSaved(id, input.changes.isNotBlank())
            } else {
                recipes.updateCookLog(logId, input)
                onSaved(logId, false)
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = logId ?: return
        viewModelScope.launch {
            recipes.deleteCookLog(id)
            onDone()
        }
    }
}
