package com.resonance.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.resonance.music.tabata.TabataConfig

@Composable
fun TabataScreen(viewModel: TabataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var warmup by remember { mutableStateOf(60) }
    var work by remember { mutableStateOf(20) }
    var rest by remember { mutableStateOf(10) }
    var rounds by remember { mutableStateOf(8) }
    var cooldown by remember { mutableStateOf(60) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tabata", style = MaterialTheme.typography.headlineMedium)
        NumberField("Aquecimento (s)", warmup) { warmup = it }
        NumberField("Trabalho (s)", work) { work = it }
        NumberField("Descanso (s)", rest) { rest = it }
        NumberField("Rodadas", rounds) { rounds = it }
        NumberField("Desaquecimento (s)", cooldown) { cooldown = it }
        Button(onClick = {
            viewModel.startRandomAlbumAndTabata(TabataConfig(warmup, work, rest, rounds, cooldown))
        }) { Text("Álbum aleatório + iniciar") }
        Button(onClick = { viewModel.stopTabata() }) { Text("Parar") }
        Text("Fase: ${state.phase} | Rodada: ${state.round} | ${state.secLeft}s")
    }
}

@Composable
fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { onChange(it.toIntOrNull() ?: 0) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
