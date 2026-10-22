package com.emmah.colordock.remote.ui

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.appearancePreferences by preferencesDataStore(name = "appearance")

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.appearancePreferences
    private val themeKey = stringPreferencesKey("theme")
    private val mutableError = MutableStateFlow<String?>(null)
    private val mutableTheme = MutableStateFlow<AppTheme?>(null)
    val error = mutableError.asStateFlow()
    val theme = mutableTheme.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                mutableTheme.value = AppTheme.fromStored(preferences.data.first()[themeKey])
            } catch (_: IOException) {
                mutableError.value = "Could not load your theme. Please try choosing it again."
                mutableTheme.value = AppTheme.SoftDark
            }
        }
    }

    fun select(theme: AppTheme) {
        viewModelScope.launch {
            try {
                preferences.edit { it[themeKey] = theme.name }
                mutableTheme.value = theme
                mutableError.value = null
            } catch (_: IOException) {
                mutableError.value = "Could not save your theme. Please try again."
            }
        }
    }
}
