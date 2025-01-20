package eu.kanade.tachiyomi.animesource

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

interface ConfigurableAnimeSource : Source {

    /**
     * Gets instance of [SharedPreferences] scoped to the specific source.
     *
     * @since extensions-lib 1.5
     */
    fun getSourcePreferences(): SharedPreferences =
        Injekt.get<Application>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

    fun setupPreferenceScreen(screen: PreferenceScreen)
}

fun ConfigurableSource.preferenceKey(): String = "source_$id"

// TODO: use getSourcePreferences once all extensions are on ext-lib 1.5
fun ConfigurableSource.sourcePreferences(): SharedPreferences =
    Injekt.get<Application>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

fun sourcePreferences(key: String): SharedPreferences =
    Injekt.get<Application>().getSharedPreferences(key, Context.MODE_PRIVATE)
