package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.parento.admin.audio.AudioAccessRepository
import com.parento.admin.audio.AudioPlaybackController
import com.parento.admin.audio.AudioTransport

class AudioAccessViewModelFactory(
    private val repository: AudioAccessRepository,
    private val transport: AudioTransport,
    private val playback: AudioPlaybackController,
    private val onSessionExpired: () -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AudioAccessViewModel::class.java))
        return AudioAccessViewModel(
            repository = repository,
            transport = transport,
            playback = playback,
            onSessionExpired = onSessionExpired,
        ) as T
    }
}
