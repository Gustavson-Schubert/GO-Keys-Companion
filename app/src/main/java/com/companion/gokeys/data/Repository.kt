package com.companion.gokeys.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "go_keys_companion")

object Keys {
    val STATE = stringPreferencesKey("app_state")
    val PROFILES = stringPreferencesKey("profiles")
    val MACROS = stringPreferencesKey("macros")
    val AUTOMATIONS = stringPreferencesKey("automations")
}

class Repository(private val ctx: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val stateFlow: Flow<AppState> = ctx.dataStore.data.map { p: Preferences ->
        p[Keys.STATE]?.let { runCatching { json.decodeFromString<AppState>(it) }.getOrNull() }
            ?: AppState()
    }

    val profilesFlow: Flow<List<Profile>> = ctx.dataStore.data.map { p: Preferences ->
        p[Keys.PROFILES]?.let {
            runCatching { json.decodeFromString(ListSerializer(Profile.serializer()), it) }
                .getOrNull()
        } ?: emptyList()
    }

    val macrosFlow: Flow<List<Macro>> = ctx.dataStore.data.map { p: Preferences ->
        p[Keys.MACROS]?.let {
            runCatching { json.decodeFromString(ListSerializer(Macro.serializer()), it) }
                .getOrNull()
        } ?: emptyList()
    }

    val automationsFlow: Flow<List<Automation>> = ctx.dataStore.data.map { p: Preferences ->
        p[Keys.AUTOMATIONS]?.let {
            runCatching { json.decodeFromString(ListSerializer(Automation.serializer()), it) }
                .getOrNull()
        } ?: emptyList()
    }

    suspend fun saveState(state: AppState) {
        ctx.dataStore.edit { it[Keys.STATE] = json.encodeToString(AppState.serializer(), state) }
    }

    suspend fun saveProfiles(list: List<Profile>) {
        ctx.dataStore.edit {
            it[Keys.PROFILES] =
                json.encodeToString(ListSerializer(Profile.serializer()), list)
        }
    }

    suspend fun saveMacros(list: List<Macro>) {
        ctx.dataStore.edit {
            it[Keys.MACROS] = json.encodeToString(ListSerializer(Macro.serializer()), list)
        }
    }

    suspend fun saveAutomations(list: List<Automation>) {
        ctx.dataStore.edit {
            it[Keys.AUTOMATIONS] =
                json.encodeToString(ListSerializer(Automation.serializer()), list)
        }
    }
}
