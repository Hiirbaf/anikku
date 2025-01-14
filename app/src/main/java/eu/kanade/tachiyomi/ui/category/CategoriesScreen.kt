package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.CategoryScreen
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.category.components.CategorySortAlphabeticallyDialog
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

object CategoriesScreen : Screen {
    private fun readResolve(): Any = CategoriesScreen

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AnimeCategoryScreenModel() }

        val state by screenModel.state.collectAsState()

        if (state is AnimeCategoryScreenState.Loading) {
            LoadingScreen()
        } else {
            val successState = state as AnimeCategoryScreenState.Success

            CategoryScreen(
                state = successState,
                onClickCreate = { screenModel.showDialog(AnimeCategoryDialog.Create) },
                onClickSortAlphabetically = { screenModel.showDialog(AnimeCategoryDialog.SortAlphabetically) },
                onClickRename = { screenModel.showDialog(AnimeCategoryDialog.Rename(it)) },
                onClickHide = screenModel::hideCategory,
                onClickDelete = { screenModel.showDialog(AnimeCategoryDialog.Delete(it)) },
                onClickMoveUp = screenModel::moveUp,
                onClickMoveDown = screenModel::moveDown,
                navigateUp = navigator::pop,
            )

            when (val dialog = successState.dialog) {
                null -> {}
                AnimeCategoryDialog.Create -> {
                    CategoryCreateDialog(
                        onDismissRequest = screenModel::dismissDialog,
                        onCreate = screenModel::createCategory,
                        categories = successState.categories.fastMap { it.name }.toImmutableList(),
                    )
                }
                is AnimeCategoryDialog.Rename -> {
                    CategoryRenameDialog(
                        onDismissRequest = screenModel::dismissDialog,
                        onRename = { screenModel.renameCategory(dialog.category, it) },
                        categories = successState.categories.fastMap { it.name }.toImmutableList(),
                        category = dialog.category.name,
                    )
                }
                is AnimeCategoryDialog.Delete -> {
                    CategoryDeleteDialog(
                        onDismissRequest = screenModel::dismissDialog,
                        onDelete = { screenModel.deleteCategory(dialog.category.id) },
                        category = dialog.category.name,
                    )
                }
                is AnimeCategoryDialog.SortAlphabetically -> {
                    CategorySortAlphabeticallyDialog(
                        onDismissRequest = screenModel::dismissDialog,
                        onSort = { screenModel.sortAlphabetically() },
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is AnimeCategoryEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
