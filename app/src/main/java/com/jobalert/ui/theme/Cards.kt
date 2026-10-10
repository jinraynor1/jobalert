package com.jobalert.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Fondo suave compartido por las tarjetas de todas las pantallas (el de Material 3 por defecto es muy marcado). */
@Composable
fun softCardColors(): CardColors =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
