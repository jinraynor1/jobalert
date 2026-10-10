package com.jobalert.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jobalert.domain.model.Alert
import com.jobalert.domain.repository.AlertRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: AlertRepository) : ViewModel() {
    val alerts: StateFlow<List<Alert>> = repository.allAlerts
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun acknowledge(id: Long) {
        viewModelScope.launch { repository.acknowledge(id) }
    }

    fun addTestAlert() {
        viewModelScope.launch {
            repository.insert(
                Alert(
                    timestamp = System.currentTimeMillis(),
                    sender = "prueba@jobalert.local",
                    subject = "Alerta de prueba",
                    snippet = "Esta alerta se generó manualmente desde el historial.",
                    ruleName = "Prueba"
                )
            )
        }
    }

    fun unacknowledge(id: Long) {
        viewModelScope.launch { repository.unacknowledge(id) }
    }

    fun acknowledgeAll() {
        viewModelScope.launch { repository.acknowledgeAll() }
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll() }
    }
}
