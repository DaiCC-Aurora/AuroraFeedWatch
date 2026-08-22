package com.aurora.podcast.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.podcast.PodcastApplication
import com.aurora.podcast.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = (app as PodcastApplication).settingsRepository
    private val repo = (app as PodcastApplication).repository

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _cleaning = MutableStateFlow(false)
    val cleaning: StateFlow<Boolean> = _cleaning.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setKeepEpisodes(value: Int) {
        viewModelScope.launch { settingsRepo.setKeepEpisodes(value) }
    }

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch { settingsRepo.setWifiOnly(value) }
    }

    fun cleanupNow() {
        viewModelScope.launch {
            _cleaning.value = true
            try {
                val keep = settingsRepo.current().keepEpisodes
                val removed = repo.cleanup(keep)
                _message.value = if (removed > 0) "已清理 $removed 期节目" else "无需清理"
            } catch (e: Exception) {
                _message.value = "清理失败：${e.message}"
            } finally {
                _cleaning.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}