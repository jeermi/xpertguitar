package fr.xpert.guitar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.xpert.guitar.audio.AudioEngine
import fr.xpert.guitar.data.ProgressStore
import fr.xpert.guitar.practice.MetronomeScheduler
import fr.xpert.guitar.ui.Nav
import fr.xpert.guitar.ui.components.PrimaryButton
import fr.xpert.guitar.ui.components.ScreenHeader
import fr.xpert.guitar.ui.components.SecondaryButton
import fr.xpert.guitar.ui.components.XCard
import fr.xpert.guitar.ui.theme.X
import kotlin.math.abs

/** Boucle d'animation : renvoie System.nanoTime() à chaque image tant que [active]. */
@Composable
private fun frameClock(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            now = System.nanoTime()
        }
    }
    return now
}

// ---------------------------------------------------------------- Métronome

@Composable
fun MetronomeScreen(engine: AudioEngine, store: ProgressStore, nav: Nav) {
    val metro = remember { MetronomeScheduler(engine, store.metronomeBpm) }
    var running by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(metro.bpm) }
    var beats by remember { mutableIntStateOf(4) }
    var ramp by remember { mutableStateOf(false) }
    var rampTarget by remember { mutableIntStateOf(minOf(bpm + 40, 240)) }
    val taps = remember { mutableStateListOf<Long>() }
    val now = frameClock(running)

    fun setBpm(v: Int) {
        bpm = v.coerceIn(30, 240); metro.bpm = bpm; store.metronomeBpm = bpm
    }
    DisposableEffect(Unit) { onDispose { if (engine.scheduler === metro) engine.scheduler = null } }

    // Le tempo qui monte modifie metro.bpm depuis le thread audio : on le reflète à l'écran.
    if (running && metro.bpm != bpm) bpm = metro.bpm

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Métronome") { nav.back() }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val current = if (running) metro.lastBeat(now) else null
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                for (i in 1..beats) {
                    val on = current?.number == i
                    Box(
                        Modifier
                            .size(if (i == 1) 30.dp else 24.dp)
                            .background(
                                when {
                                    on && i == 1 -> X.Orange
                                    on -> X.Jaune
                                    else -> X.Pastille
                                },
                                CircleShape,
                            )
                    )
                }
            }
            Text("$bpm", fontSize = 84.sp, color = X.Texte, style = MaterialTheme.typography.headlineLarge)
            Text("BPM · ${tempoName(bpm)}", color = X.Texte2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in listOf(-5, -1, +1, +5)) {
                    SecondaryButton(if (d > 0) "+$d" else "$d", Modifier.weight(1f)) { setBpm(bpm + d) }
                }
            }
            Slider(
                value = bpm.toFloat(),
                onValueChange = { setBpm(it.toInt()) },
                valueRange = 30f..240f,
                colors = SliderDefaults.colors(thumbColor = X.Orange, activeTrackColor = X.Orange),
            )
            PrimaryButton(if (running) "■ Stop" else "▶ Démarrer", Modifier.fillMaxWidth()) {
                running = !running
                engine.scheduler = if (running) metro.also { it.bpm = bpm } else null
            }
            SecondaryButton("👆 Tap tempo", Modifier.fillMaxWidth()) {
                val t = System.nanoTime()
                if (taps.isNotEmpty() && t - taps.last() > 2_000_000_000L) taps.clear()
                taps.add(t)
                if (taps.size > 6) taps.removeAt(0)
                if (taps.size >= 3) {
                    val avg = (taps.last() - taps.first()) / (taps.size - 1)
                    setBpm((60_000_000_000L / avg).toInt())
                }
            }
            XCard {
                Text("Mesure", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (b in listOf(2, 3, 4, 6)) {
                        FilterChip(
                            selected = beats == b,
                            onClick = { beats = b; metro.beatsPerBar = b },
                            label = { Text("$b/4") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = X.Orange, selectedLabelColor = X.Texte,
                            ),
                        )
                    }
                }
                ToggleRow("Tempo qui monte", "+4 BPM toutes les 4 mesures jusqu'à $rampTarget BPM", ramp) {
                    ramp = it; metro.rampEnabled = it; metro.rampTarget = rampTarget
                }
                if (ramp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Objectif", Modifier.weight(1f), color = X.Texte2)
                        TextButton(onClick = { rampTarget = (rampTarget - 5).coerceAtLeast(40); metro.rampTarget = rampTarget }) { Text("−5", color = X.Jaune) }
                        Text("$rampTarget", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { rampTarget = (rampTarget + 5).coerceAtMost(240); metro.rampTarget = rampTarget }) { Text("+5", color = X.Jaune) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun tempoName(bpm: Int) = when {
    bpm < 60 -> "Largo"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante / Moderato"
    bpm < 168 -> "Allegro"
    else -> "Presto"
}
