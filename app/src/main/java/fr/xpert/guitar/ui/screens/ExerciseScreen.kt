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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.xpert.guitar.audio.AudioEngine
import fr.xpert.guitar.audio.ChordStream
import fr.xpert.guitar.audio.MicListener
import fr.xpert.guitar.data.ProgressStore
import fr.xpert.guitar.data.SessionRecord
import fr.xpert.guitar.practice.Exercise
import fr.xpert.guitar.practice.Exercises
import fr.xpert.guitar.practice.Feedback
import fr.xpert.guitar.practice.Judgement
import fr.xpert.guitar.practice.PracticeSession
import fr.xpert.guitar.practice.Stats
import fr.xpert.guitar.ui.Nav
import fr.xpert.guitar.ui.Screen
import fr.xpert.guitar.ui.components.PrimaryButton
import fr.xpert.guitar.ui.components.ScreenHeader
import fr.xpert.guitar.ui.components.StrumLane
import fr.xpert.guitar.ui.components.ChordDiagram
import fr.xpert.guitar.practice.ChordSegment
import fr.xpert.guitar.practice.ExerciseKind
import fr.xpert.guitar.music.Notes
import fr.xpert.guitar.ui.components.SecondaryButton
import fr.xpert.guitar.ui.components.XCard
import fr.xpert.guitar.ui.theme.X
import kotlin.math.abs

private class Summary(val record: SessionRecord, val stats: Stats)

@Composable
fun ExerciseScreen(id: String, engine: AudioEngine, store: ProgressStore, nav: Nav) {
    val ex = Exercises.byId(id) ?: return
    if (ex.kind != ExerciseKind.RHYTHM) {
        ChordTrainerScreen(ex, engine, store, nav)
        return
    }
    RhythmExerciseScreen(ex, engine, store, nav)
}

@Composable
private fun RhythmExerciseScreen(ex: Exercise, engine: AudioEngine, store: ProgressStore, nav: Nav) {
    var startBpm by remember { mutableIntStateOf(ex.startBpm) }
    var ramp by remember { mutableStateOf(true) }
    var metronome by remember { mutableStateOf(true) }
    var session by remember { mutableStateOf<PracticeSession?>(null) }
    var summary by remember { mutableStateOf<Summary?>(null) }
    var startAfterPermission by remember { mutableStateOf(false) }
    val (micGranted, requestMic) = rememberMicPermission { ok -> if (ok) startAfterPermission = true }
    val listening = session?.let { !it.demo } == true
    val mic = rememberMic(engine, micGranted && listening)
    val chordStream = rememberChordStream(mic)

    fun stop(showSummary: Boolean) {
        val s = session ?: return
        engine.scheduler = null
        session = null
        if (!s.demo && s.stats.judged > 0) {
            val rec = SessionRecord(
                exerciseId = ex.id,
                title = ex.title,
                atMillis = s.startedAtMillis,
                durationSec = ((System.currentTimeMillis() - s.startedAtMillis) / 1000).toInt(),
                accuracy = s.stats.accuracy,
                bpm = s.maxBpmPlayed,
                meanDeltaMs = s.stats.meanDeltaMs,
                chordAccuracy = if (s.stats.chordsJudged > 0) s.stats.chordAccuracy else -1,
            )
            store.record(rec)
            if (showSummary) summary = Summary(rec, s.stats)
        }
    }

    fun start(demo: Boolean) {
        val withMic = !demo
        if (withMic && !micGranted) {
            requestMic()
            return
        }
        summary = null
        mic.detector.threshold = store.micThreshold
        chordStream.clear()
        val s = PracticeSession(
            engine = engine,
            exercise = ex,
            startBpm = startBpm,
            rampEnabled = ramp && !demo,
            demo = demo,
            metronome = metronome,
            calibrationMs = if (withMic) store.micOffsetMs else 0,
            chords = if (withMic) chordStream else null,
        )
        session = s
        engine.scheduler = s
    }

    if (startAfterPermission) {
        startAfterPermission = false
        start(demo = false)
    }

    DisposableEffect(Unit) { onDispose { stop(showSummary = false) } }

    val s = session
    val sum = summary
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(ex.title) { nav.back() }
        when {
            s != null -> RunningView(s, if (!s.demo) mic else null, store.micThreshold) { stop(showSummary = true) }
            sum != null -> SummaryView(
                ex, sum,
                onRetry = { summary = null },
                onNext = {
                    val i = Exercises.all.indexOf(ex)
                    val next = Exercises.all.getOrNull(i + 1)
                    nav.back()
                    if (next != null) nav.go(Screen.Exercise(next.id))
                },
            )
            else -> SetupView(
                ex, store, startBpm, { startBpm = it }, ramp, { ramp = it }, metronome, { metronome = it },
                onListen = { start(demo = true) },
                onPlay = { start(demo = false) },
                onCalibrate = { nav.go(Screen.MicSetup) },
            )
        }
    }
}

@Composable
private fun SetupView(
    ex: Exercise,
    store: ProgressStore,
    startBpm: Int, setStartBpm: (Int) -> Unit,
    ramp: Boolean, setRamp: (Boolean) -> Unit,
    metronome: Boolean, setMetronome: (Boolean) -> Unit,
    onListen: () -> Unit,
    onPlay: () -> Unit,
    onCalibrate: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(ex.description, style = MaterialTheme.typography.bodyLarge)
        Column(
            Modifier
                .fillMaxWidth()
                .background(X.Jaune.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text("💡 Conseil", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
            Text(ex.tip, style = MaterialTheme.typography.bodyLarge)
        }
        if (!store.isMicCalibrated) {
            XCard(onClick = onCalibrate) {
                Text("🎤 Réglage du micro pas encore fait", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
                Text("Sans lui, l'appli peut rater tes coups ou entendre le clic. Touche ici (1 min).", color = X.Texte2)
            }
        }
        XCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tempo de départ", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("$startBpm BPM", color = X.Jaune, style = MaterialTheme.typography.titleLarge)
            }
            Slider(
                value = startBpm.toFloat(),
                onValueChange = { setStartBpm(it.toInt()) },
                valueRange = 30f..ex.targetBpm.toFloat(),
                colors = SliderDefaults.colors(thumbColor = X.Orange, activeTrackColor = X.Orange),
            )
            ToggleRow(
                "Tempo qui monte",
                "+${ex.rampStep} BPM toutes les ${ex.rampEveryBars} mesures, jusqu'à ${ex.targetBpm}",
                ramp, setRamp,
            )
            ToggleRow("Métronome", "Le clic pendant l'exercice (le décompte reste toujours)", metronome, setMetronome)
        }
        XCard {
            Text("La rythmique", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(ex.rhythm?.arrows ?: "", fontSize = 28.sp, color = X.Texte)
            Text(countLine(ex), color = X.Texte2, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Text("La grille", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (sh in ex.chords.map { it.shape }.distinct()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
                        Text(sh.symbol, color = X.Texte, style = MaterialTheme.typography.titleMedium)
                        ChordDiagram(sh, Modifier.fillMaxWidth(), showFingers = false)
                    }
                }
            }
            Text(
                ex.chords.joinToString("  ·  ") { if (it.bars > 1) "${it.shape.symbol} ×${it.bars}" else it.shape.symbol } + "  (une mesure par accord, en boucle)",
                color = X.Texte2, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "Le micro écoute ta guitare : gratte quand les flèches touchent la barre jaune. Il juge le placement de chaque coup et la justesse de chaque accord.",
            color = X.Texte2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("▶ Écouter", Modifier.weight(1f), onListen)
            PrimaryButton("🎸 À moi !", Modifier.weight(1f), onPlay)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = X.Texte2, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = X.Orange, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun RunningView(s: PracticeSession, mic: MicListener?, micThreshold: Float, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var micLevel by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(s) {
        mic?.onsets?.clear()
        while (true) {
            withFrameNanos { }
            now = System.nanoTime()
            if (mic != null) {
                micLevel = maxOf(mic.detector.takePeak(), micLevel * 0.85f)
                while (true) {
                    val o = mic.onsets.poll() ?: break
                    if (s.countInBeat(o.nanos) == null) {
                        val fb = s.onOnset(o.nanos)
                        if (fb.counted) feedback = fb
                    }
                }
            }
            s.tick(now)
        }
    }
    val countIn = s.countInBeat(now)
    val bpm = s.currentBpm(now)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("$bpm", color = X.Jaune, fontSize = 30.sp, style = MaterialTheme.typography.headlineLarge)
                Text("BPM", color = X.Texte2, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(16.dp))
            val fb = feedback
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (s.demo) {
                    Text("Écoute et suis les flèches", color = X.Texte2, textAlign = TextAlign.Center)
                } else if (fb != null && now - fb.atNanos < 700_000_000L) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fb.judgement.label, color = X.judgement(fb.judgement), fontSize = 24.sp, style = MaterialTheme.typography.headlineSmall)
                        if (fb.judgement != Judgement.EXTRA) {
                            Text("${abs(fb.deltaMs)} ms", color = X.Texte2)
                        }
                    }
                }
            }
            if (!s.demo) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${s.stats.accuracy} %", fontSize = 22.sp, style = MaterialTheme.typography.headlineSmall)
                    Text("RYTHME", color = X.Texte2, style = MaterialTheme.typography.labelMedium)
                    if (s.stats.chordsJudged > 0) {
                        Text("${s.stats.chordAccuracy} %", fontSize = 22.sp, style = MaterialTheme.typography.headlineSmall)
                        Text("ACCORDS", color = X.Texte2, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(vertical = 8.dp)
                .background(X.Carte, RoundedCornerShape(20.dp)),
        ) {
            StrumLane(s, now, Modifier.fillMaxSize())
            if (countIn != null) {
                Text(
                    if (countIn == 0) "Prêt ?" else "$countIn",
                    color = X.OrangeTexte,
                    fontSize = 56.sp,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        if (!s.demo) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatChip("Pile", s.stats.perfect, X.Vert)
                StatChip("Avance", s.stats.early, X.Bleu)
                StatChip("Retard", s.stats.late, X.Orange)
                StatChip("Raté", s.stats.miss, X.Rouge)
            }
        }
        Spacer(Modifier.height(10.dp))

        val (cur, next) = s.chordAt(now)
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (cur != null) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(cur.symbol, color = X.Jaune, fontSize = 26.sp, style = MaterialTheme.typography.headlineSmall)
                    ChordDiagram(cur, Modifier.fillMaxWidth(0.8f))
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (next != null && next != cur) {
                    Text("Ensuite : ${next.symbol}", color = X.Texte2, style = MaterialTheme.typography.titleMedium)
                    ChordDiagram(next, Modifier.fillMaxWidth(0.55f), showFingers = false)
                }
                val seg = s.lastChordVerdict
                if (mic != null && seg != null) {
                    Spacer(Modifier.height(6.dp))
                    ChordVerdictLine(seg)
                }
                if (mic != null) {
                    if (!mic.active && System.currentTimeMillis() - s.startedAtMillis > 1500) {
                        Text("Le micro n'a pas pu démarrer : vérifie l'autorisation micro dans les réglages Android.", color = X.OrangeTexte)
                    } else {
                        val fb = feedback
                        val flash = if (fb != null && now - fb.atNanos < 180_000_000L) X.judgement(fb.judgement) else null
                        Spacer(Modifier.height(8.dp))
                        LevelMeter(micLevel, micThreshold, flash = flash)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("■ Arrêter", Modifier.fillMaxWidth(), onStop)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun StatChip(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(10.dp)
                .height(10.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text("$label $value", color = X.Texte2, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SummaryView(ex: Exercise, sum: Summary, onRetry: () -> Unit, onNext: () -> Unit) {
    val r = sum.record
    val st = sum.stats
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "${r.accuracy} %",
            fontSize = 64.sp,
            color = if (r.accuracy >= 80) X.Vert else X.Jaune,
            style = MaterialTheme.typography.headlineLarge,
        )
        Text("de précision rythmique · jusqu'à ${r.bpm} BPM", color = X.Texte2)
        if (r.chordAccuracy >= 0) {
            Text(
                "Accords justes : ${r.chordAccuracy} % (${st.chordsOk} sur ${st.chordsJudged})",
                color = if (r.chordAccuracy >= 80) X.Vert else X.Jaune,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        XCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatChip("Pile", st.perfect, X.Vert)
                StatChip("Avance", st.early, X.Bleu)
                StatChip("Retard", st.late, X.Orange)
                StatChip("Raté", st.miss, X.Rouge)
            }
            if (st.extra > 0) {
                Spacer(Modifier.height(6.dp))
                Text("${st.extra} coup(s) de médiator en trop", color = X.Texte2)
            }
        }
        XCard {
            Text("🎧 Ton placement", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(placementAdvice(r.meanDeltaMs), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Text("➡️ La suite", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(nextStepAdvice(r.accuracy, r.bpm, ex), style = MaterialTheme.typography.bodyLarge)
            if (r.chordAccuracy in 0..79) {
                Spacer(Modifier.height(10.dp))
                Text("🖐️ Les accords", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Plusieurs accords n'ont pas sonné juste. Travaille les changements lentement (exercices « Changement… »), " +
                        "vérifie l'accordage, et prépare l'accord suivant pendant le dernier temps de la mesure.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("↻ Recommencer", Modifier.weight(1f), onRetry)
            val isLast = Exercises.all.lastOrNull() == ex
            PrimaryButton(if (isLast) "Retour à la liste" else "Exercice suivant", Modifier.weight(1f), onNext)
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun placementAdvice(meanDeltaMs: Int): String = when {
    meanDeltaMs <= -15 ->
        "Tu as tendance à jouer en avance (${-meanDeltaMs} ms en moyenne). C'est très courant : on se précipite. " +
            "Écoute le clic et laisse-le arriver avant de gratter."
    meanDeltaMs >= 15 ->
        "Tu as tendance à jouer en retard ($meanDeltaMs ms en moyenne). Anticipe un peu le geste : le médiator doit " +
            "commencer à descendre avant le clic."
    else -> "Ton placement est bien centré sur le clic (écart moyen $meanDeltaMs ms). Bravo !"
}

private fun nextStepAdvice(accuracy: Int, bpm: Int, ex: Exercise): String = when {
    accuracy >= 90 && bpm >= ex.targetBpm -> "Objectif atteint ! Passe à l'exercice suivant, et reviens à celui-ci en échauffement."
    accuracy >= 90 -> "Excellent. Recommence en partant de quelques BPM plus haut."
    accuracy >= 80 -> "Bien joué. Refais-le une ou deux fois au même tempo pour le rendre automatique."
    accuracy >= 50 -> "Baisse le tempo de départ de 5 à 10 BPM et vise 80 %. Lentement, c'est comme ça qu'on va vite."
    else -> "Commence par « Écouter », compte à voix haute, puis rejoue nettement plus lentement."
}

/** « 1 et 2 et 3 et 4 et » aligné sur la rythmique. */
private fun countLine(ex: Exercise): String {
    val r = ex.rhythm ?: return ""
    return when (r.stepsPerBeat) {
        1 -> (1..r.beatsPerBar).joinToString(" ")
        2 -> (1..r.beatsPerBar).joinToString(" ") { "$it et" }
        3 -> (1..r.beatsPerBar).joinToString(" ") { "$it . ." } + "  (ternaire)"
        else -> (1..r.beatsPerBar).joinToString(" ") { "$it i et a" }
    }
}

/** Verdict du dernier accord de la grille. */
@Composable
fun ChordVerdictLine(seg: ChordSegment) {
    val v = seg.verdict
    when {
        seg.unheard -> Text("${seg.shape.symbol} : pas entendu", color = X.Texte2)
        v == null -> {}
        v.ok -> Text("${seg.shape.symbol} : juste ✓", color = X.Vert, style = MaterialTheme.typography.titleMedium)
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${seg.shape.symbol} : pas juste", color = X.Rouge, style = MaterialTheme.typography.titleMedium)
            Text(chordHint(v, seg.shape), color = X.Texte2, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Conseil court tiré du diagnostic de l'analyse. */
fun chordHint(v: fr.xpert.guitar.audio.ChordVerdict, shape: fr.xpert.guitar.music.Shape): String = when {
    v.silent -> "Je n'entends rien : rapproche le téléphone ou monte un peu l'ampli."
    v.wrongString >= 0 ->
        "La corde de ${Notes.STRING_NAMES[v.wrongString]} sonne faux : vérifie la case de ce doigt" +
            (if (shape.frets[v.wrongString] == 0) " (elle doit sonner à vide)." else ".")
    v.heard != null && v.heard != shape.chord && v.score < 0.5f -> "J'entends plutôt ${v.heard.symbol}."
    v.missing.isNotEmpty() -> "On n'entend pas bien le ${Notes.FR[v.missing.first()]} : un doigt étouffe peut-être une corde."
    v.extra.isNotEmpty() -> "Une note en trop (${Notes.FR[v.extra.first()]}) : une corde à ne pas jouer, ou un doigt mal placé ?"
    else -> "Vérifie que chaque corde sonne bien, une par une."
}

/** ChordStream branché sur le micro tant que l'écran est affiché. */
@Composable
fun rememberChordStream(mic: MicListener): ChordStream {
    val cs = androidx.compose.runtime.remember { ChordStream(mic.sampleRate) }
    DisposableEffect(mic) {
        mic.consumers.add(cs)
        onDispose { mic.consumers.remove(cs) }
    }
    return cs
}
