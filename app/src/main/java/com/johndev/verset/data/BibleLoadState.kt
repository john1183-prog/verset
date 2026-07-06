package com.johndev.verset.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * True while the one-time KJV import is running. The Reader screen observes
 * this to show a loading state instead of silently displaying an empty
 * chapter on first launch.
 */
object BibleLoadState {
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun setLoading(value: Boolean) {
        _isLoading.value = value
    }
}
