package com.example.libary.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionStore by preferencesDataStore(name = "session")

class TokenStore(context: Context) {
    private val store = context.sessionStore

    suspend fun read(): String? = store.data.map { it[KEY] }.first()

    suspend fun write(token: String) {
        store.edit { it[KEY] = token }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("access_token")
    }
}
