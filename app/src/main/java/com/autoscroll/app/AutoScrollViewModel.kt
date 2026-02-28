package com.autoscroll.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel that exposes scroll-engine parameters and running state to the UI.
 */
class AutoScrollViewModel : ViewModel() {

    // ---- Observable parameters ----

    private val _baseDelay = MutableLiveData(ScrollConfig.baseDelayMs)
    val baseDelay: LiveData<Float> = _baseDelay

    private val _jitterMultiplier = MutableLiveData(ScrollConfig.jitterMultiplier)
    val jitterMultiplier: LiveData<Float> = _jitterMultiplier

    private val _swipeLengthVariance = MutableLiveData(ScrollConfig.swipeLengthVariance)
    val swipeLengthVariance: LiveData<Float> = _swipeLengthVariance

    private val _swipeDuration = MutableLiveData(ScrollConfig.swipeDurationMs)
    val swipeDuration: LiveData<Float> = _swipeDuration

    private val _isScrolling = MutableLiveData(false)
    val isScrolling: LiveData<Boolean> = _isScrolling

    // State listener kept as a field so we can remove it later
    private val stateListener: (Boolean) -> Unit = { running ->
        _isScrolling.postValue(running)
    }

    init {
        AutoScrollService.addStateListener(stateListener)
        _isScrolling.value = AutoScrollService.isScrolling
    }

    override fun onCleared() {
        super.onCleared()
        AutoScrollService.removeStateListener(stateListener)
    }

    // ---- Setters (called from UI slider changes) ----

    fun setBaseDelay(value: Float) {
        ScrollConfig.baseDelayMs = value
        _baseDelay.value = value
    }

    fun setJitterMultiplier(value: Float) {
        ScrollConfig.jitterMultiplier = value
        _jitterMultiplier.value = value
    }

    fun setSwipeLengthVariance(value: Float) {
        ScrollConfig.swipeLengthVariance = value
        _swipeLengthVariance.value = value
    }

    fun setSwipeDuration(value: Float) {
        ScrollConfig.swipeDurationMs = value
        _swipeDuration.value = value
    }

    // ---- Scroll control ----

    fun toggleScrolling() {
        AutoScrollService.instance?.toggleScrolling()
    }
}
