package tachiyomi.domain.anime.model

import tachiyomi.core.common.preference.TriState

inline fun applyFilter(filter: TriState, predicate: () -> Boolean): Boolean = when (filter) {
    TriState.DISABLED -> true
    TriState.ENABLED_IS -> predicate()
    TriState.ENABLED_NOT -> !predicate()
}
