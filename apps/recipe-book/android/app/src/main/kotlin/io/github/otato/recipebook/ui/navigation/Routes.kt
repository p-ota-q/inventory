package io.github.otato.recipebook.ui.navigation

import android.net.Uri

/**
 * 画面の行き先。引数つきの行き先は、パターン（{...} 入り）と、実際の行き先を作る関数を対にして持つ。
 */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TAGS = "tags"

    const val DETAIL = "recipe/{recipeId}"
    fun detail(recipeId: String) = "recipe/${Uri.encode(recipeId)}"

    /**
     * レシピの編集。新しく作るときは id に「new」。
     * from は、どこから来たか: none（ふつう）・paste（貼り付けた文章を分けて入れる）・log（作った記録から反映。logId を使う）
     */
    const val EDIT = "edit/{recipeId}?from={from}&logId={logId}"
    fun edit(recipeId: String, from: String = "none", logId: String = "") =
        "edit/${Uri.encode(recipeId)}?from=$from&logId=${Uri.encode(logId)}"

    const val NEW_ID = "new"

    /** 作った記録の入力。logId が空なら、新しく付ける */
    const val COOK_LOG = "cooklog/{recipeId}?logId={logId}"
    fun cookLog(recipeId: String, logId: String = "") = "cooklog/${Uri.encode(recipeId)}?logId=${Uri.encode(logId)}"

    const val KITCHEN = "kitchen/{recipeId}"
    fun kitchen(recipeId: String) = "kitchen/${Uri.encode(recipeId)}"

    const val VERSIONS = "versions/{recipeId}"
    fun versions(recipeId: String) = "versions/${Uri.encode(recipeId)}"
}
