package com.resonance.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.resonance.music.tabata.Phase
import com.resonance.music.tabata.TabataConfig
import kotlin.math.roundToInt

@Composable
fun TabataScreen(viewModel: TabataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val selected by viewModel.selectedAlbum.collectAsState()
    val error by viewModel.error.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val starting by viewModel.starting.collectAsState()
    var warmup by rememberSaveable { mutableIntStateOf(60) }
    var work by rememberSaveable { mutableIntStateOf(20) }
    var rest by rememberSaveable { mutableIntStateOf(10) }
    var rounds by rememberSaveable { mutableIntStateOf(8) }
    var cooldown by rememberSaveable { mutableIntStateOf(60) }
    var intensity by rememberSaveable { mutableIntStateOf(6) }
    var query by rememberSaveable { mutableStateOf("") }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val running = state.phase != Phase.DONE || starting

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Escolher álbum") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Álbum ou artista") }, singleLine = true)
                    TextButton(onClick = { viewModel.searchAlbums(query) }) { Text("Buscar") }
                    if (loading) {
                        CircularProgressIndicator()
                    } else {
                        if (albums.isEmpty()) Text("Nenhum álbum encontrado.")
                        albums.forEach { album ->
                            TextButton(onClick = { viewModel.selectAlbum(album); choosing = false }) {
                                Text("${album.name} — ${album.artist.orEmpty()}")
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Fechar") } }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tabata", style = MaterialTheme.typography.headlineMedium)
        Text(selected?.let { "Álbum: ${it.name} — ${it.artist.orEmpty()}" } ?: "Nenhum álbum selecionado")
        OutlinedButton(onClick = { choosing = true }, enabled = !running) { Text("Escolher álbum") }
        NumberField("Aquecimento (s)", warmup) { warmup = it.coerceAtLeast(0) }
        NumberField("Trabalho (s)", work) { work = it.coerceAtLeast(0) }
        NumberField("Descanso (s)", rest) { rest = it.coerceAtLeast(0) }
        NumberField("Rodadas", rounds) { rounds = it.coerceAtLeast(0) }
        NumberField("Desaquecimento (s)", cooldown) { cooldown = it.coerceAtLeast(0) }
        Text("Intensidade do efeito: $intensity/10")
        Slider(value = intensity.toFloat(), onValueChange = { intensity = it.roundToInt() }, valueRange = 0f..10f, steps = 9, enabled = !running)
        Text("0: sem efeito • 3: efeito anterior • 6: forte • 10: máximo contraste no descanso")
        Button(
            enabled = !running && selected != null && work > 0 && rounds > 0,
            onClick = { viewModel.startTabata(TabataConfig(warmup, work, rest, rounds, cooldown, intensity)) }
        ) { Text(if (starting) "Carregando…" else "Iniciar com este álbum") }
        Button(onClick = { viewModel.stopTabata() }) { Text("Parar treino e música") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
