package eu.kanade.tachiyomi.ui.anime

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.RelatedAnimesContent
import eu.kanade.presentation.browse.components.BrowseSourceSimpleToolbar
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import kotlinx.coroutines.CoroutineScope
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.material.Scaffold
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun RelatedAnimesScreen(
    screenModel: AnimeScreenModel,
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    navigateUp: () -> Unit,
    navigator: Navigator,
    scope: CoroutineScope,
    successState: AnimeScreenModel.State.Success,
) {
    val sourcePreferences: SourcePreferences = Injekt.get()
    var displayMode by sourcePreferences.sourceDisplayMode().asState(scope)

    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

    val haptic = LocalHapticFeedback.current

    val snackbarHostState = remember { SnackbarHostState() }

    var topBarHeight by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { scrollBehavior ->
            if (bulkFavoriteState.selectionMode) {
                BulkSelectionToolbar(
                    selectedCount = bulkFavoriteState.selection.size,
                    isRunning = bulkFavoriteState.isRunning,
                    onClickClearSelection = bulkFavoriteScreenModel::toggleSelectionMode,
                    onChangeCategoryClick = bulkFavoriteScreenModel::addFavorite,
                    onSelectAll = {
                        successState.relatedAnimesSorted?.forEach {
                            val relatedAnime = it as RelatedAnime.Success
                            relatedAnime.mangaList.forEach { manga ->
                                bulkFavoriteScreenModel.select(manga)
                            }
                        }
                    },
                    onReverseSelection = {
                        successState.relatedAnimesSorted
                            ?.map { it as RelatedAnime.Success }
                            ?.flatMap { it.mangaList }
                            ?.let { bulkFavoriteScreenModel.reverseSelection(it) }
                    },
                )
            } else {
                BrowseSourceSimpleToolbar(
                    navigateUp = navigateUp,
                    title = successState.anime.title,
                    displayMode = displayMode,
                    onDisplayModeChange = { displayMode = it },
                    scrollBehavior = scrollBehavior,
                    toggleSelectionMode = bulkFavoriteScreenModel::toggleSelectionMode,
                    isRunning = bulkFavoriteState.isRunning,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        RelatedAnimesContent(
            relatedAnimes = successState.relatedAnimesSorted,
            getMangaState = { manga -> screenModel.getManga(initialManga = manga) },
            columns = getColumnsPreference(LocalConfiguration.current.orientation),
            entries = getColumnsPreferenceForCurrentOrientation(LocalConfiguration.current.orientation),
            topBarHeight = topBarHeight,
            displayMode = displayMode,
            contentPadding = paddingValues,
            onMangaClick = {
                scope.launchIO {
                    val manga = screenModel.networkToLocalAnime.getLocal(it)
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else {
                        navigator.push(AnimeScreen(manga.id, true))
                    }
                }
            },
            onMangaLongClick = {
                scope.launchIO {
                    val manga = screenModel.networkToLocalAnime.getLocal(it)
                    if (!bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.addRemoveManga(manga, haptic)
                    } else {
                        navigator.push(AnimeScreen(manga.id, true))
                    }
                }
            },
            onKeywordClick = { query ->
                navigator.push(BrowseSourceScreen(successState.source.id, query))
            },
            onKeywordLongClick = { query ->
                navigator.push(GlobalSearchScreen(query))
            },
            selection = bulkFavoriteState.selection,
        )
    }
}

private fun getColumnsPreference(orientation: Int): GridCells {
    val libraryPreferences: LibraryPreferences = Injekt.get()

    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) {
        libraryPreferences.landscapeColumns()
    } else {
        libraryPreferences.portraitColumns()
    }.get()
    return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
}

// returns the number from the size slider
private fun getColumnsPreferenceForCurrentOrientation(orientation: Int): Int {
    val libraryPreferences: LibraryPreferences = Injekt.get()

    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    return if (isLandscape) {
        libraryPreferences.landscapeColumns()
    } else {
        libraryPreferences.portraitColumns()
    }.get()
}
