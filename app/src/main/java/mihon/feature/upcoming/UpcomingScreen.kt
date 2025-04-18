package mihon.feature.upcoming

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_OUTSIDE_RELEASE_PERIOD

class UpcomingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { UpcomingScreenModel() }
        val state by screenModel.state.collectAsState()

        UpcomingScreenContent(
            state = state,
            setSelectedYearMonth = screenModel::setSelectedYearMonth,
            onClickUpcoming = { navigator.push(AnimeScreen(it.id)) },
            // KMK -->
            showUpdatingMangas = screenModel::showUpdatingMangas,
            hideUpdatingMangas = screenModel::hideUpdatingMangas,
            isPredictReleaseDate = ANIME_OUTSIDE_RELEASE_PERIOD in screenModel.restriction,
            // KMK <--
        )
    }
}
