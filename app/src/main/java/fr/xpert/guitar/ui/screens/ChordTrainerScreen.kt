package fr.xpert.guitar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.xpert.guitar.audio.AudioEngine
import fr.xpert.guitar.data.ProgressStore
import fr.xpert.guitar.data.SessionRecord
import fr.xpert.guitar.music.Shape
import fr.xpert.guitar.practice.ChordJudge
import fr.xpert.guitar.practice.Exercise
import fr.xpert.guitar.practice.ExerciseKind
import fr.xpert.guitar.practice.Exercises
import fr.xpert.guitar.practice.StrumPlayer
import fr.xpert.guitar.practice.StrumVerdict
import fr.xpert.guitar.ui.Nav
import fr.xpert.guitar.ui.Screen
import fr.xpert.guitar.ui.components.ChordDiagram
import fr.xpert.guitar.ui.components.PrimaryButton
import fr.xpert.guitar.ui.components.ScreenHeader
import fr.xpert.guitar.ui.components.SecondaryButton
import fr.xpert.guitar.ui.components.XCard
import fr.xpert.guitar.ui.theme.X
import kotlinx.coroutines.delay

private const val OK_TO_ADVANCE = 2
private const val CHANGES_SECONDS = 60

/** Exercices sans tempo : accords un par un (CHORD_CHECK) ou changements en une minute (CHANGES). */
@Composable
fun ChordTrainerScreen(ex: Exercise, engine: AudioEngine, store: ProgressStore, nav: Nav) {
    val shapes = ex.chords.map { it.shape }
    var running by remember { mutableStateOf(false) }
    var startAfterPermission by remember { mutableStateOf(false) }
    val (micGranted, requestMic) = rememberMicPermission { ok -> if (ok) startAfterPermission = true }
    val mic = rememberMic(engine, micGranted && running)
    val stream = rememberChordStream(mic)
    val judge = remember { ChordJudge(mic, stream) }

    var index by remember { mutableIntStateOf(0) }
    var oks by remember { mutableIntStateOf(0) }
    var okThisChord by remember { mutableIntStateOf(0) }
    var attempts by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf<StrumVerdict?>(null) }
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }

    fun start() {
        if (!micGranted) { requestMic(); return }
        index = 0; oks = 0; okThisChord = 0; attempts = 0; last = null; finished = false
        mic.detector.threshold = store.micThreshold * 0.8f
        judge.reset()
        startedAt = System.nanoTime()
        running = true
    }

    fun finish() {
        running = false
        finished = true
        if (attempts > 0) {
            store.record(
                SessionRecord(
                    exerciseId = ex.id, title = ex.title, atMillis = System.currentTimeMillis(),
                    durationSec = ((System.nanoTime() - startedAt) / 1_000_000_000L).toInt(),
                    accuracy = oks * 100 / attempts, bpm = 0, meanDeltaMs = 0,
                    changes = if (ex.kind == ExerciseKind.CHANGES) oks else 0,
                )
            )
        }
    }

    if (startAfterPermission) {
        startAfterPermission = false
        start()
    }

    val target = shapes[index % shapes.size]
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (running) {
            withFrameNanos { }
            now = System.nanoTime()
            level = maxOf(mic.detector.takePeak(), level * 0.85f)
            val v = judge.tick(now, shapes[index % shapes.size])
            if (v != null) {
                last = v
                attempts++
                if (v.verdict.ok) {
                    oks++
                    store.markChordMastered(v.shape.id)
                    if (ex.kind == ExerciseKind.CHANGES) {
                        index = (index + 1) % shapes.size
                    } else {
                        okThisChord++
                    }
                }
            }
            if (ex.kind == ExerciseKind.CHANGES && now - startedAt > CHANGES_SECONDS * 1_000_000_000L) finish()
        }
    }
    // Accord validé assez de fois : on passe au suivant après un court instant
    if (ex.kind == ExerciseKind.CHORD_CHECK && running && okThisChord >= OK_TO_ADVANCE) {
        LaunchedEffect(index, okThisChord) {
            delay(1200)
            okThisChord = 0
            last = null
            if (index + 1 >= shapes.size) finish() else index++
        }
    }
    DisposableEffect(Unit) { onDispose { if (engine.scheduler is StrumPlayer) engine.scheduler = null } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(ex.title) { nav.back() }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                finished -> TrainerSummary(ex, oks, attempts, onRetry = { start() }, onNext = {
                    val next = Exercises.all.getOrNull(Exercises.all.indexOf(ex) + 1)
                    nav.back()
                    if (next != null) nav.go(Screen.Exercise(next.id))
                })
                !running -> {
                    Text(ex.description, style = MaterialTheme.typography.bodyLarge)
                    Tip(ex.tip)
                    if (!store.isMicCalibrated) {
                        XCard(onClick = { nav.go(Screen.MicSetup) }) {
                            Text("🎤 Réglage du micro pas encore fait", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
                            Text("1 minute pour que l'appli entende bien ta guitare. Touche ici.", color = X.Texte2)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (sh in shapes.distinct()) {
                            XCard(Modifier.weight(1f), onClick = { engine.scheduler = StrumPlayer.chord(engine, sh) }) {
                                Text(sh.symbol, color = X.Jaune, style = MaterialTheme.typography.titleLarge)
                                ChordDiagram(sh, Modifier.fillMaxWidth())
                                Text("🔊 écouter", color = X.Texte2, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Text(
                        if (ex.kind == ExerciseKind.CHANGES)
                            "Tu as une minute : joue ${shapes[0].symbol}, puis ${shapes[1].symbol}, puis ${shapes[0].symbol}… Chaque accord juste compte un changement. Vise 30, puis 60."
                        else
                            "Pose l'accord, gratte une fois et laisse sonner. Il faut deux accords justes pour passer au suivant.",
                        color = X.Texte2,
                    )
                    PrimaryButton("🎸 C'est parti", Modifier.fillMaxWidth()) { start() }
                }
                else -> {
                    if (ex.kind == ExerciseKind.CHANGES) {
                        val left = CHANGES_SECONDS - ((now - startedAt) / 1_000_000_000L).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${left.coerceAtLeast(0)} s", fontSize = 34.sp, color = X.Jaune, modifier = Modifier.weight(1f))
                            Text("$oks changement${if (oks > 1) "s" else ""}", fontSize = 24.sp, color = X.Texte)
                        }
                    } else {
                        Text("Accord ${index + 1} / ${shapes.size}", color = X.Texte2)
                    }
                    val hl = last?.takeIf { !it.verdict.ok && it.shape == target && now - it.atNanos < 4_000_000_000L }?.verdict?.wrongString ?: -1
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(target.symbol, fontSize = 40.sp, color = X.Jaune, style = MaterialTheme.typography.headlineLarge)
                            Text(target.chord.nameFr, color = X.Texte2)
                            ChordDiagram(target, Modifier.fillMaxWidth(0.62f), highlightString = hl)
                        }
                    }
                    VerdictCard(last, now, target)
                    if (ex.kind == ExerciseKind.CHORD_CHECK) {
                        Text("Justes pour cet accord : $okThisChord / $OK_TO_ADVANCE", color = X.Texte2)
                    }
                    if (!mic.active && (now - startedAt) > 1_500_000_000L) {
                        Text("Le micro n'a pas pu démarrer : vérifie l'autorisation micro dans les réglages Android.", color = X.OrangeTexte)
                    } else {
                        LevelMeter(level, mic.detector.threshold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryButton("🔊 Écouter", Modifier.weight(1f)) { engine.scheduler = StrumPlayer.chord(engine, target, arpeggio = false) }
                        PrimaryButton("■ Terminer", Modifier.weight(1f)) { finish() }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Tip(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(X.Jaune.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text("💡 Conseil", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Résultat du dernier accord gratté. */
@Composable
fun VerdictCard(last: StrumVerdict?, now: Long, target: Shape) {
    XCard {
        val l = last
        when {
            l == null || now - l.atNanos > 6_000_000_000L ->
                Text("Gratte l'accord et laisse-le sonner…", color = X.Texte2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            l.verdict.ok -> Text(
                "✓ ${l.shape.symbol} juste !", color = X.Vert, fontSize = 24.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✗ Pas encore ${l.shape.symbol}", color = X.Rouge, fontSize = 22.sp)
                Spacer(Modifier.height(4.dp))
                Text(chordHint(l.verdict, l.shape), color = X.Texte, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun TrainerSummary(ex: Exercise, oks: Int, attempts: Int, onRetry: () -> Unit, onNext: () -> Unit) {
    val pct = if (attempts > 0) oks * 100 / attempts else 0
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp))
        if (ex.kind == ExerciseKind.CHANGES) {
            Text("$oks", fontSize = 64.sp, color = if (oks >= 30) X.Vert else X.Jaune)
            Text("changements justes en une minute", color = X.Texte2)
        } else {
            Text("$pct %", fontSize = 64.sp, color = if (pct >= 80) X.Vert else X.Jaune)
            Text("d'accords justes ($oks sur $attempts essais)", color = X.Texte2)
        }
        XCard {
            Text("➡️ La suite", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    ex.kind == ExerciseKind.CHANGES && oks >= 60 -> "Excellent : ce changement est acquis. Passe à la suite !"
                    ex.kind == ExerciseKind.CHANGES && oks >= 30 -> "Très bien. Refais-le demain pour viser 60 changements."
                    ex.kind == ExerciseKind.CHANGES -> "Continue chaque jour : le nombre de changements monte très vite d'une séance à l'autre."
                    attempts == 0 -> "Je n'ai rien entendu : vérifie le réglage du micro et le volume de l'ampli."
                    pct >= 80 -> "Bravo, ces accords sonnent juste. Passe à la suite."
                    else -> "Prends le temps de poser chaque doigt, puis vérifie corde par corde que chacune sonne."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("↻ Recommencer", Modifier.weight(1f), onRetry)
            PrimaryButton("Exercice suivant", Modifier.weight(1f), onNext)
        }
    }
}
