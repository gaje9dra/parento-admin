package com.parento.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.parento.admin.screensharing.ScreenSharingRepository

class ScreenSharingViewModelFactory(
    private val repository: ScreenSharingRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ScreenSharingViewModel::class.java))
        return ScreenSharingViewModel(repository, onSessionExpired) as T
    }
}
