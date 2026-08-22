package com.aurora.podcast.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.podcast.PodcastApplication
import com.aurora.podcast.data.db.EpisodeEntity
import com.aurora.podcast.data.model.SubtitleCue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val player = (app as PodcastApplication).playerManager
    private val repo = (app as PodcastApplication).repository

    val currentEpisode: StateFlow<EpisodeEntity?> = player.currentEpisode
    val isPlaying: StateFlow<Boolean> = player.isPlaying
    val positionMs: StateFlow<Long> = player.positionMs
    val subtitleCues: StateFlow<List<SubtitleCue>> = player.subtitleCues

    /** 当前高亮的字幕行。 */
    val currentCue: StateFlow<SubtitleCue?> =
        combine(player.positionMs, player.subtitleCues) { pos, cues ->
            cues.firstOrNull { pos in it.startMillis..it.endMillis }
                ?: cues.lastOrNull { pos >= it.endMillis }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 从列表进入播放页时调用：加载并播放指定节目。 */
    fun load(guid: String) {
        viewModelScope.launch {
            val ep = repo.episodeByGuid(guid) ?: return@launch
            repo.markLastPlayed(ep.guid)
            player.play(ep)
        }
    }

    fun play(episode: EpisodeEntity) = player.play(episode)

    fun toggle() = player.togglePlayPause()

    fun skipNext() = player.skipToNext()

    fun skipPrevious() = player.skipToPrevious()
}