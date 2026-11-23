package io.github.otato.recipebook

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.otato.recipebook.ui.AppRoot

class MainActivity : ComponentActivity() {
    /** 共有の画面の「保存して開く」から来たとき、開くレシピ */
    private val openRecipe = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRecipe.value = intent.getStringExtra(EXTRA_RECIPE_ID)
        setContent { AppRoot(openRecipe = openRecipe) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_RECIPE_ID)?.let { openRecipe.value = it }
    }

    companion object {
        private const val EXTRA_RECIPE_ID = "recipe_id"

        fun openRecipeIntent(context: Context, recipeId: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_RECIPE_ID, recipeId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
