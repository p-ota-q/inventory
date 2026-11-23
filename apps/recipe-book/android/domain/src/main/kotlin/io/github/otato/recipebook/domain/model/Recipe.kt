package io.github.otato.recipebook.domain.model

import java.time.Instant
import java.time.LocalDate

/** レシピの状態（F-RC-04）。作った記録を付けると、「気になる」は自動で「作った」になる。「定番」は自分で付け外しする。 */
enum class RecipeStatus { Want, Made, Staple }

/** 作った記録の評価（F-LG-02。3 段階） */
enum class Rating { Again, Okay, Meh }

/** 出典の種類（F-RC-02） */
enum class SourceKind { YouTube, Web, Ai, Book, Person, Own }

/** 誰が書いたか。段階 3 で Gemini が書いたものを見分けるために、最初から持っておく（6.1） */
enum class Author { User, Gemini }

/** 材料の 1 行。[isHeading] が true の行は、「A」「タレ」のような見出し（分量は持たない。F-RC-05） */
data class Ingredient(
    val name: String,
    val amount: String = "",
    val isHeading: Boolean = false,
)

/** 版ごとに持つ、レシピの中身（何人分・材料・手順・ポイント）。 */
data class RecipeContent(
    val servings: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val tips: String = "",
) {
    val isEmpty: Boolean get() = servings.isBlank() && ingredients.isEmpty() && steps.isEmpty() && tips.isBlank()
}

data class RecipeSource(
    val id: String,
    val kind: SourceKind,
    val url: String?,
    val title: String,
    val note: String = "",
)

/** 出典を保存するときの入力（id は保存のときに振る）。 */
data class SourceInput(
    val kind: SourceKind,
    val url: String? = null,
    val title: String = "",
    val note: String = "",
)

data class Tag(
    val id: String,
    val name: String,
    val position: Int,
)

/** レシピの版（F-RC-07）。中身を丸ごと持つ（差分ではない）。 */
data class RecipeVersion(
    val id: String,
    val recipeId: String,
    val number: Int,
    val isOriginal: Boolean,
    val content: RecipeContent,
    /** 変えた理由（空でもよい） */
    val reason: String,
    /** きっかけになった作った記録 */
    val fromCookLogId: String?,
    val author: Author,
    val createdAt: Instant,
)

/** 作った記録（F-LG-01）。日付以外は空でもよい。 */
data class CookLog(
    val id: String,
    val recipeId: String,
    /** どの版で作ったか */
    val versionId: String?,
    val cookedOn: LocalDate,
    val rating: Rating?,
    val impression: String,
    val changes: String,
    val nextNote: String,
    val author: Author,
    val createdAt: Instant,
)

data class CookLogInput(
    val cookedOn: LocalDate,
    val rating: Rating? = null,
    val impression: String = "",
    val changes: String = "",
    val nextNote: String = "",
)

/** 取り込んだ文章（そのまま残す。F-CL-05、6.3） */
data class ImportedText(
    val id: String,
    val text: String,
    val createdAt: Instant,
)

data class Recipe(
    val id: String,
    val title: String,
    val status: RecipeStatus,
    val currentVersion: RecipeVersion,
    val sources: List<RecipeSource>,
    val tags: List<Tag>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val content: RecipeContent get() = currentVersion.content
}

/** レシピの詳細の画面で使う、まとめた情報。 */
data class RecipeDetail(
    val recipe: Recipe,
    /** 新しい順（作った日、同じ日なら記録した時刻） */
    val cookLogs: List<CookLog>,
    val versionCount: Int,
    /** 最初に保存した版（いまの版と違うときの比較に使う。F-RC-08） */
    val originalVersion: RecipeVersion?,
    val importedTexts: List<ImportedText>,
) {
    /** 最後に書いた「次に作るときのメモ」（F-LG-04） */
    val latestNextNote: CookLog? get() = cookLogs.firstOrNull { it.nextNote.isNotBlank() }
}

/** 一覧の 1 行（F-LG-06、4.5）。 */
data class RecipeSummary(
    val id: String,
    val title: String,
    val status: RecipeStatus,
    val tags: List<Tag>,
    val cookCount: Int,
    val lastCookedOn: LocalDate?,
    val lastRating: Rating?,
    val latestNextNote: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** 検索に使う文章（料理名・タグ・材料・ポイント・作った記録。正規化してある。F-SR-01） */
    val searchText: String,
)

/** レシピを保存するときの入力。新しく作るときも、直すときも使う。 */
data class RecipeDraft(
    val title: String,
    val status: RecipeStatus = RecipeStatus.Want,
    val content: RecipeContent = RecipeContent(),
    val sources: List<SourceInput> = emptyList(),
    val tagIds: List<String> = emptyList(),
    /** 新しい版になるときの、変えた理由 */
    val versionReason: String = "",
    /** 作った記録から反映するとき、その記録（必ず新しい版になる。F-LG-05） */
    val fromCookLogId: String? = null,
    /** 取り込んだ文章（新しく作るときだけ使う） */
    val importedText: String? = null,
)
