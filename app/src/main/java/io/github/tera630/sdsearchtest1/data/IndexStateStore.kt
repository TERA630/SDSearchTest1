package io.github.tera630.sdsearchtest1.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("sdsearch_prefs")

class IndexStateStore(private val context: Context) {
    private object Keys {
        val LAST_INDEXED_AT = longPreferencesKey("last_indexed_at")
    }

    val lastIndexedAtFlow: Flow<Long?> =
        context.dataStore.data.map { pref -> pref[Keys.LAST_INDEXED_AT] }

    suspend fun setLastIndexedAt(epochMillis: Long) {
        context.dataStore.edit { prefs ->   // 型を明示しない or MutablePreferences
            prefs[Keys.LAST_INDEXED_AT] = epochMillis
        }
    }
}
