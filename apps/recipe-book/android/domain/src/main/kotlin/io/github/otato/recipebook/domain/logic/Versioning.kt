package io.github.otato.recipebook.domain.logic

import io.github.otato.recipebook.domain.model.Ingredient
import io.github.otato.recipebook.domain.model.RecipeContent
import java.time.Instant

/**
 * レシピを直したときに、新しい版にするかどうか（F-RC-07）。
 *
 * - 中身が変わっていなければ、版は増やさない
 * - 作った記録から反映するときは、必ず新しい版にする
 * - いまの版を作ったあとに、作った記録がある（作ってから直した）なら、新しい版にする
 * - それ以外（まだ作っていない、作ったあとにもう直した）は、いまの版をそのまま直す。
 *   これで「同じ日の続けての編集は、1 つの版にまとめる」ことになる
 */
object Versioning {
    fun needsNewVersion(
        currentVersionCreatedAt: Instant,
        lastCookLogCreatedAt: Instant?,
        fromCookLog: Boolean,
    ): Boolean = fromCookLog || (lastCookLogCreatedAt != null && lastCookLogCreatedAt > currentVersionCreatedAt)

    /** 比べるための形（前後の空白・空の行の違いは、変更とみなさない）。 */
    fun normalized(content: RecipeContent): RecipeContent = RecipeContent(
        servings = content.servings.trim(),
        ingredients = content.ingredients
            .map { Ingredient(it.name.trim(), if (it.isHeading) "" else it.amount.trim(), it.isHeading) }
            .filter { it.name.isNotEmpty() || it.amount.isNotEmpty() },
        steps = content.steps.map { it.trim() }.filter { it.isNotEmpty() },
        tips = content.tips.trim(),
    )

    /** 材料の 1 行ごとの違い（F-RC-08）。 */
    sealed interface IngredientChange {
        val ingredient: Ingredient

        data class Same(override val ingredient: Ingredient) : IngredientChange
        data class Added(override val ingredient: Ingredient) : IngredientChange
        data class Removed(override val ingredient: Ingredient) : IngredientChange

        /** 名前が同じで、分量が変わった */
        data class Changed(override val ingredient: Ingredient, val before: String) : IngredientChange
    }

    /**
     * 元の版といまの版の材料を比べる。名前で突き合わせる（並びの違いは見ない）。
     * いまの版の並びで返し、元の版にだけあったものは、最後に「消した」として並べる。
     */
    fun diffIngredients(original: List<Ingredient>, current: List<Ingredient>): List<IngredientChange> {
        val before = original.filter { !it.isHeading }.groupBy { it.name.trim() }.mapValues { it.value.toMutableList() }
        val result = mutableListOf<IngredientChange>()
        for (item in current) {
            if (item.isHeading) {
                result += IngredientChange.Same(item)
                continue
            }
            val match = before[item.name.trim()]?.removeFirstOrNull()
            result += when {
                match == null -> IngredientChange.Added(item)
                match.amount.trim() == item.amount.trim() -> IngredientChange.Same(item)
                else -> IngredientChange.Changed(item, match.amount)
            }
        }
        before.values.flatten().forEach { result += IngredientChange.Removed(it) }
        return result
    }
}
