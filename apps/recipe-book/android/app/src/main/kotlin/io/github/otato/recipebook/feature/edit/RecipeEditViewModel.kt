package io.github.otato.recipebook.feature.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otato.recipebook.domain.logic.RecipeTextParser
import io.github.otato.recipebook.domain.logic.Urls
import io.github.otato.recipebook.domain.model.Ingredient
import io.github.otato.recipebook.domain.model.RecipeContent
import io.github.otato.recipebook.domain.model.RecipeDraft
import io.github.otato.recipebook.domain.model.RecipeStatus
import io.github.otato.recipebook.domain.model.SourceInput
import io.github.otato.recipebook.domain.model.SourceKind
import io.github.otato.recipebook.domain.model.Tag
import io.github.otato.recipebook.domain.repository.RecipeRepository
import io.github.otato.recipebook.ui.common.formatDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IngredientField(val key: Long, val name: String, val amount: String, val isHeading: Boolean)
data class StepField(val key: Long, val text: String)
data class SourceField(val key: Long, val kind: SourceKind, val url: String, val title: String)

/** 編集中のレシピ。 */
data class EditForm(
    val title: String = "",
    val status: RecipeStatus = RecipeStatus.Want,
    val sources: List<SourceField> = emptyList(),
    val servings: String = "",
    val ingredients: List<IngredientField> = emptyList(),
    val steps: List<StepField> = emptyList(),
    val tips: String = "",
    val tagIds: Set<String> = emptySet(),
    /** 新しい版になるときの、変えた理由 */
    val reason: String = "",
)

/** 文章から入れたとき、その結果の知らせ（F-CL-05）。 */
data class ImportInfo(
    val rawText: String,
    val leftover: String,
    val ingredientCount: Int,
    val stepCount: Int,
)

/**
 * レシピの編集（F-RC-01、F-RC-05、F-RC-06）。新しく作る・貼り付けた文章から作る・直す・作った記録から反映する（F-LG-05）を、この 1 つで扱う。
 *
 * 入力中の値は、Compose の状態で持つ（並べ替えのドラッグ中に、すぐ反映されるように）。
 */
class RecipeEditViewModel(
    private val recipeId: String?,
    private val from: String,
    private val logId: String?,
    private val recipes: RecipeRepository,
    private val pendingImport: MutableStateFlow<String?>,
) : ViewModel() {

    var form by mutableStateOf(EditForm())
        private set
    var loading by mutableStateOf(true)
        private set
    var importInfo by mutableStateOf<ImportInfo?>(null)
        private set

    /** 作った記録から反映するとき、その記録の「変えたところ」 */
    var reflectNote by mutableStateOf<String?>(null)
        private set

    /** 保存すると新しい版になるか（作ったあとに直すとき。変えた理由の欄を出す） */
    var willMakeVersion by mutableStateOf(false)
        private set

    /** 料理名が空のまま保存しようとした */
    var titleError by mutableStateOf(false)
        private set

    val isNew: Boolean get() = recipeId == null

    val tags: StateFlow<List<Tag>> = recipes.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var initialForm = EditForm()
    private var nextKey = 0L
    private fun key() = nextKey++

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (recipeId == null) {
            val text = if (from == FROM_PASTE) pendingImport.value.also { pendingImport.value = null } else null
            form = if (text != null) formFromText(text) else EditForm(
                ingredients = listOf(IngredientField(key(), "", "", false)),
                steps = listOf(StepField(key(), "")),
            )
            initialForm = if (text != null) EditForm() else form
        } else {
            val detail = recipes.getDetail(recipeId) ?: run {
                loading = false
                return
            }
            val recipe = detail.recipe
            val content = recipe.content
            form = EditForm(
                title = recipe.title,
                status = recipe.status,
                sources = recipe.sources.map { SourceField(key(), it.kind, it.url.orEmpty(), it.title) },
                servings = content.servings,
                ingredients = content.ingredients.map { IngredientField(key(), it.name, it.amount, it.isHeading) },
                steps = content.steps.map { StepField(key(), it) },
                tips = content.tips,
                tagIds = recipe.tags.map { it.id }.toSet(),
            )
            val lastCook = detail.cookLogs.maxOfOrNull { it.createdAt }
            willMakeVersion = from == FROM_LOG || (lastCook != null && lastCook > recipe.currentVersion.createdAt)
            if (from == FROM_LOG && logId != null) {
                recipes.getCookLog(logId)?.let { log ->
                    reflectNote = log.changes.ifBlank { null }
                    form = form.copy(reason = "${formatDate(log.cookedOn)}に作ったとき: ${log.changes}".trim().trimEnd(':', ' '))
                }
            }
            // 変えた理由を前もって入れただけでは、「保存していない変更」とみなさない
            initialForm = form
        }
        loading = false
    }

    private fun formFromText(text: String): EditForm {
        val parsed = RecipeTextParser.parse(text)
        importInfo = ImportInfo(
            rawText = text,
            leftover = parsed.leftover,
            ingredientCount = parsed.content.ingredients.count { !it.isHeading },
            stepCount = parsed.content.steps.size,
        )
        val sources = parsed.urls.map { SourceField(key(), Urls.kindOf(it), it, "") }
            // URL のない文章は、AI の回答のことが多い（違っていたら変えられる）
            .ifEmpty { listOf(SourceField(key(), SourceKind.Ai, "", "AI の回答")) }
        return EditForm(
            title = parsed.title.orEmpty(),
            sources = sources,
            servings = parsed.content.servings,
            ingredients = parsed.content.ingredients.map { IngredientField(key(), it.name, it.amount, it.isHeading) }
                .ifEmpty { listOf(IngredientField(key(), "", "", false)) },
            steps = parsed.content.steps.map { StepField(key(), it) }.ifEmpty { listOf(StepField(key(), "")) },
            tips = parsed.content.tips,
        )
    }

    /** 保存していない変更があるか（戻るときに確かめる） */
    val isDirty: Boolean get() = !loading && form != initialForm

    // ── 入力 ──

    fun setTitle(value: String) {
        form = form.copy(title = value)
        if (value.isNotBlank()) titleError = false
    }

    fun setStatus(value: RecipeStatus) {
        form = form.copy(status = value)
    }

    fun setServings(value: String) {
        form = form.copy(servings = value)
    }

    fun setTips(value: String) {
        form = form.copy(tips = value)
    }

    fun setReason(value: String) {
        form = form.copy(reason = value)
    }

    fun updateIngredient(key: Long, name: String? = null, amount: String? = null) {
        form = form.copy(ingredients = form.ingredients.map {
            if (it.key == key) it.copy(name = name ?: it.name, amount = amount ?: it.amount) else it
        })
    }

    fun toggleHeading(key: Long) {
        form = form.copy(ingredients = form.ingredients.map { if (it.key == key) it.copy(isHeading = !it.isHeading) else it })
    }

    fun addIngredient(heading: Boolean = false) {
        form = form.copy(ingredients = form.ingredients + IngredientField(key(), "", "", heading))
    }

    fun removeIngredient(key: Long) {
        form = form.copy(ingredients = form.ingredients.filterNot { it.key == key })
    }

    fun updateStep(key: Long, text: String) {
        form = form.copy(steps = form.steps.map { if (it.key == key) it.copy(text = text) else it })
    }

    fun addStep() {
        form = form.copy(steps = form.steps + StepField(key(), ""))
    }

    fun removeStep(key: Long) {
        form = form.copy(steps = form.steps.filterNot { it.key == key })
    }

    /** 並べ替え（ドラッグ中に、行が入れ替わるたびに呼ぶ）。材料は材料の中、手順は手順の中だけで動かす。 */
    fun move(fromKey: Any, toKey: Any) {
        val a = fromKey as? String ?: return
        val b = toKey as? String ?: return
        when {
            a.startsWith(ING) && b.startsWith(ING) -> form = form.copy(ingredients = moved(form.ingredients, a.removePrefix(ING).toLong(), b.removePrefix(ING).toLong()) { it.key })
            a.startsWith(STEP) && b.startsWith(STEP) -> form = form.copy(steps = moved(form.steps, a.removePrefix(STEP).toLong(), b.removePrefix(STEP).toLong()) { it.key })
        }
    }

    private fun <T> moved(list: List<T>, fromKey: Long, toKey: Long, keyOf: (T) -> Long): List<T> {
        val from = list.indexOfFirst { keyOf(it) == fromKey }
        val to = list.indexOfFirst { keyOf(it) == toKey }
        if (from < 0 || to < 0) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun addSource() {
        form = form.copy(sources = form.sources + SourceField(key(), SourceKind.Web, "", ""))
    }

    fun updateSource(key: Long, kind: SourceKind? = null, url: String? = null, title: String? = null) {
        form = form.copy(sources = form.sources.map {
            if (it.key != key) {
                it
            } else {
                val newUrl = url ?: it.url
                // URL を入れたら、種類を合わせる（YouTube かどうか）
                val newKind = kind ?: if (url != null && url.startsWith("http")) Urls.kindOf(url) else it.kind
                it.copy(kind = newKind, url = newUrl, title = title ?: it.title)
            }
        })
    }

    fun removeSource(key: Long) {
        form = form.copy(sources = form.sources.filterNot { it.key == key })
    }

    fun toggleTag(tagId: String) {
        form = form.copy(tagIds = if (tagId in form.tagIds) form.tagIds - tagId else form.tagIds + tagId)
    }

    fun createTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val tag = recipes.createTag(name)
            form = form.copy(tagIds = form.tagIds + tag.id)
        }
    }

    // ── 保存 ──

    fun save(onSaved: (String) -> Unit) {
        if (form.title.isBlank()) {
            titleError = true
            return
        }
        val f = form
        val draft = RecipeDraft(
            title = f.title,
            status = f.status,
            content = RecipeContent(
                servings = f.servings,
                ingredients = f.ingredients.map { Ingredient(it.name, it.amount, it.isHeading) },
                steps = f.steps.map { it.text },
                tips = f.tips,
            ),
            sources = f.sources.map { SourceInput(it.kind, it.url.trim().ifEmpty { null }, it.title) },
            tagIds = f.tagIds.toList(),
            versionReason = if (willMakeVersion) f.reason else "",
            fromCookLogId = if (from == FROM_LOG) logId else null,
            importedText = importInfo?.rawText,
        )
        viewModelScope.launch {
            val id = if (recipeId == null) recipes.create(draft) else recipes.update(recipeId, draft).let { recipeId }
            initialForm = form
            onSaved(id)
        }
    }

    companion object {
        const val FROM_NONE = "none"
        const val FROM_PASTE = "paste"
        const val FROM_LOG = "log"
        const val ING = "ing-"
        const val STEP = "step-"
    }
}
