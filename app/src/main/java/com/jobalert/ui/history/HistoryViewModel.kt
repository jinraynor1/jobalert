package com.jobalert.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jobalert.data.model.AlertEntity
import com.jobalert.data.repository.AlertRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: AlertRepository) : ViewModel() {
    val alerts: StateFlow<List<AlertEntity>> = repository.allAlerts
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun acknowledge(id: Long) {
        viewModelScope.launch { repository.acknowledge(id) }
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll() }
    }
}
