package fr.xpert.guitar.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.xpert.guitar.audio.AudioEngine
import fr.xpert.guitar.audio.TunerStream
import fr.xpert.guitar.data.ProgressStore
import fr.xpert.guitar.music.Chords
import fr.xpert.guitar.music.Notes
import fr.xpert.guitar.practice.ChordJudge
import fr.xpert.guitar.practice.StrumPlayer
import fr.xpert.guitar.practice.StrumVerdict
import fr.xpert.guitar.ui.Nav
import fr.xpert.guitar.ui.Screen
import fr.xpert.guitar.ui.components.ChordDiagram
import fr.xpert.guitar.ui.components.Pill
import fr.xpert.guitar.ui.components.PrimaryButton
import fr.xpert.guitar.ui.components.ScreenHeader
import fr.xpert.guitar.ui.components.SecondaryButton
import fr.xpert.guitar.ui.components.SectionLabel
import fr.xpert.guitar.ui.components.XCard
import fr.xpert.guitar.ui.theme.X
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Accordeur

/** Écart en cents entre [hz] et la note MIDI [midi]. */
private fun centsFrom(hz: Double, midi: Int): Double = 1200.0 * ln(hz / Notes.freq(midi.toDouble())) / ln(2.0)

@Composable
fun TunerScreen(engine: AudioEngine, nav: Nav) {
    var startRequested by remember { mutableStateOf(true) }
    val (granted, requestMic) = rememberMicPermission { }
    val mic = rememberMic(engine, granted)
    val tuner = remember { TunerStream(engine.sampleRate) }
    DisposableEffect(mic) {
        mic.consumers.add(tuner)
        onDispose { mic.consumers.remove(tuner) }
    }
    LaunchedEffect(startRequested) {
        if (startRequested && !granted) requestMic()
        startRequested = false
    }

    // -1 = automatique (corde la plus proche), sinon corde choisie 0..5
    var chosen by remember { mutableIntStateOf(-1) }
    var hz by remember { mutableStateOf(0.0) }
    var cents by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableIntStateOf(-1) }
    var lastHeard by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var inTuneSince by remember { mutableLongStateOf(0L) }

    LaunchedEffect(granted) {
        val recent = ArrayDeque<Double>()
        var lastAt = 0L
        while (true) {
            withFrameNanos { }
            now = System.nanoTime()
            val r = tuner.latest ?: continue
            if (r.atNanos == lastAt) continue
            lastAt = r.atNanos
            if (r.clarity < 0.85) continue
            // Médiane des dernières mesures : stable mais réactive
            recent.addLast(r.hz)
            if (recent.size > 5) recent.removeFirst()
            if (recent.size >= 2 && abs(centsFrom(r.hz, 69) - centsFrom(recent.elementAt(recent.size - 2), 69)) > 80) {
                recent.clear(); recent.addLast(r.hz) // nouvelle corde : on repart
            }
            val med = recent.sorted()[recent.size / 2]
            val string = if (chosen >= 0) chosen else Notes.STANDARD_TUNING.indices.minBy { abs(centsFrom(med, Notes.STANDARD_TUNING[it])) }
            val c = centsFrom(med, Notes.STANDARD_TUNING[string])
            // En mode auto, une note trop loin de toute corde (ex. une case jouée) s'affiche en note libre
            hz = med
            target = if (chosen < 0 && abs(c) > 250) -1 else string
            cents = if (target >= 0) c.toFloat().coerceIn(-50f, 50f) else 0f
            lastHeard = now
            if (target >= 0 && abs(c) <= 5) {
                if (inTuneSince == 0L) inTuneSince = now
            } else {
                inTuneSince = 0L
            }
        }
    }
    DisposableEffect(Unit) { onDispose { if (engine.scheduler is StrumPlayer) engine.scheduler = null } }

    val fresh = now - lastHeard < 1_200_000_000L && hz > 0
    val inTune = fresh && inTuneSince > 0 && now - inTuneSince > 300_000_000L

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Accordeur") { nav.back() }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!granted) {
                XCard(onClick = requestMic) {
                    Text("🎤 L'accordeur a besoin du micro", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
                    Text("Touche ici pour l'autoriser.", color = X.Texte2)
                }
            }
            XCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val noteName = when {
                        !fresh -> "–"
                        target >= 0 -> Notes.STRING_NAMES[target]
                        else -> Notes.nameFr((69 + 12 * ln(hz / 440.0) / ln(2.0)).roundToInt())
                    }
                    Text(noteName, fontSize = 40.sp, color = if (inTune) X.Vert else X.Jaune)
                    Text(
                        if (fresh) "%.1f Hz".format(hz) else "Joue une corde à vide et laisse-la sonner",
                        color = X.Texte2,
                    )
                    Spacer(Modifier.height(12.dp))
                    TunerGauge(if (fresh && target >= 0) cents else null, inTune)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            !fresh -> " "
                            target < 0 -> "Cette note n'est pas une corde à vide."
                            inTune -> "✓ Juste !"
                            cents < 0 -> "Trop grave : resserre la clé (monte)  ↑"
                            else -> "Trop aigu : desserre la clé (descends)  ↓"
                        },
                        color = if (inTune) X.Vert else X.Texte,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            SectionLabel("Corde à accorder")
            FilterChip(
                selected = chosen < 0, onClick = { chosen = -1 }, label = { Text("Automatique") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = X.Orange, selectedLabelColor = X.Texte),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in 0 until 6) {
                    FilterChip(
                        selected = chosen == s,
                        onClick = { chosen = s },
                        label = { Text("${6 - s} " + Notes.FR[Notes.pc(Notes.STANDARD_TUNING[s])], maxLines = 1, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = X.Orange, selectedLabelColor = X.Texte),
                    )
                }
            }
            Text(
                "En automatique, l'accordeur choisit la corde la plus proche. Si une corde est très désaccordée, choisis-la à la main.",
                color = X.Texte2,
            )
            SecondaryButton(
                "🔊 Écouter la note ${if (chosen >= 0) Notes.STRING_NAMES[chosen] else "(corde choisie)"}",
                Modifier.fillMaxWidth(),
            ) {
                val s = if (chosen >= 0) chosen else if (target >= 0) target else 0
                engine.scheduler = StrumPlayer.note(engine, Notes.STANDARD_TUNING[s], s)
            }
            XCard {
                Text("💡 Bien accorder", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Accorde toujours en montant vers la note : si tu es trop aigu, descends un peu sous la note puis remonte. " +
                        "La corde tient mieux. Pince la corde fort une fois, puis laisse-la sonner sans la toucher.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Jauge en arc de -50 à +50 cents. */
@Composable
private fun TunerGauge(cents: Float?, inTune: Boolean) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val cx = size.width / 2
        val baseY = size.height - 8f
        val r = minOf(size.width / 2 - 12f, size.height - 16f)
        fun pt(c: Float, rr: Float): Offset {
            val a = Math.toRadians((c / 50f * 60f).toDouble())
            return Offset(cx + (rr * Math.sin(a)).toFloat(), baseY - (rr * Math.cos(a)).toFloat())
        }
        for (c in -50..50 step 10) {
            val long = c == 0
            drawLine(
                if (abs(c) <= 5) X.Vert else X.CarteBord,
                pt(c.toFloat(), r - if (long) 26f else 14f), pt(c.toFloat(), r),
                strokeWidth = if (long) 6f else 3f, cap = StrokeCap.Round,
            )
        }
        drawArc(
            X.Vert.copy(alpha = 0.35f), startAngle = -90f - 6f, sweepAngle = 12f, useCenter = true,
            topLeft = Offset(cx - r, baseY - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
        )
        if (cents != null) {
            drawLine(
                if (inTune) X.Vert else X.Orange, Offset(cx, baseY), pt(cents, r - 6f),
                strokeWidth = 7f, cap = StrokeCap.Round,
            )
        }
        drawCircle(X.Texte, 9f, Offset(cx, baseY))
    }
}

// ---------------------------------------------------------------- Dictionnaire d'accords

@Composable
fun ChordsScreen(store: ProgressStore, nav: Nav) {
    val mastered = remember { store.chordsMastered() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Dictionnaire d'accords") { nav.back() }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "Touche un accord pour voir le doigté, l'écouter et le vérifier au micro. " +
                    "Les accords déjà joués justes sont marqués ✓.",
                color = X.Texte2,
            )
            for ((family, shapes) in Chords.families) {
                SectionLabel(family.title)
                Text(family.subtitle, color = X.Texte2, modifier = Modifier.padding(bottom = 10.dp))
                for (row in shapes.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        for (sh in row) {
                            XCard(Modifier.weight(1f), onClick = { nav.go(Screen.ChordDetail(sh.id)) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(sh.symbol, color = X.Jaune, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                    if (sh.id in mastered) Text("✓", color = X.Vert, style = MaterialTheme.typography.titleLarge)
                                }
                                ChordDiagram(sh, Modifier.fillMaxWidth(), showFingers = false)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ChordDetailScreen(id: String, engine: AudioEngine, store: ProgressStore, nav: Nav) {
    val shape = remember(id) { Chords.get(id) }
    var listening by remember { mutableStateOf(false) }
    var startAfterPermission by remember { mutableStateOf(false) }
    val (granted, requestMic) = rememberMicPermission { ok -> if (ok) startAfterPermission = true }
    val mic = rememberMic(engine, granted && listening)
    val stream = rememberChordStream(mic)
    val judge = remember { ChordJudge(mic, stream) }
    var last by remember { mutableStateOf<StrumVerdict?>(null) }
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var level by remember { mutableFloatStateOf(0f) }
    var mastered by remember { mutableStateOf(shape.id in store.chordsMastered()) }

    fun startListening() {
        if (!granted) { requestMic(); return }
        mic.detector.threshold = store.micThreshold * 0.8f
        judge.reset()
        last = null
        listening = true
    }
    if (startAfterPermission) {
        startAfterPermission = false
        startListening()
    }
    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        while (listening) {
            withFrameNanos { }
            now = System.nanoTime()
            level = maxOf(mic.detector.takePeak(), level * 0.85f)
            judge.tick(now, shape)?.let { v ->
                last = v
                if (v.verdict.ok) {
                    store.markChordMastered(shape.id)
                    mastered = true
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { if (engine.scheduler is StrumPlayer) engine.scheduler = null } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(shape.symbol) { nav.back() }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(shape.chord.nameFr, style = MaterialTheme.typography.titleMedium, color = X.Texte2)
                if (mastered) Pill("✓ déjà joué juste", X.Vert)
            }
            val hl = last?.takeIf { !it.verdict.ok && now - it.atNanos < 4_000_000_000L }?.verdict?.wrongString ?: -1
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ChordDiagram(shape, Modifier.fillMaxWidth(0.7f), highlightString = hl)
            }
            if (shape.tip.isNotEmpty()) {
                XCard {
                    Text("💡 Conseil", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
                    Text(shape.tip, style = MaterialTheme.typography.bodyLarge)
                }
            }
            XCard {
                Text("Corde par corde", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                for (s in 0 until 6) {
                    val f = shape.frets[s]
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${6 - s}. ${Notes.STRING_NAMES[s]}", color = X.Texte2, modifier = Modifier.weight(1f))
                        Text(
                            when {
                                f < 0 -> "ne pas jouer"
                                f == 0 -> "à vide : ${Notes.nameFr(Notes.STANDARD_TUNING[s])}"
                                else -> "case $f, doigt ${shape.fingers[s].takeIf { it > 0 } ?: "–"} : ${Notes.nameFr(Notes.STANDARD_TUNING[s] + f)}"
                            },
                            color = if (f < 0) X.Texte2 else X.Texte,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("🔊 Note par note", Modifier.weight(1f)) { engine.scheduler = StrumPlayer.chord(engine, shape) }
                SecondaryButton("🔊 Gratté", Modifier.weight(1f)) { engine.scheduler = StrumPlayer.chord(engine, shape, arpeggio = false) }
            }
            if (!listening) {
                PrimaryButton("🎤 Vérifier au micro", Modifier.fillMaxWidth()) { startListening() }
            } else {
                VerdictCard(last, now, shape)
                if (!mic.active) {
                    Text("Le micro n'a pas pu démarrer : vérifie l'autorisation micro dans les réglages Android.", color = X.OrangeTexte)
                } else {
                    LevelMeter(level, mic.detector.threshold)
                }
                SecondaryButton("■ Arrêter l'écoute", Modifier.fillMaxWidth()) { listening = false }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- Crédits

@Composable
fun CreditsScreen(nav: Nav) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("À propos et crédits") { nav.back() }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            XCard {
                Text("🎸 Sons de guitare", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "« Black and Green Guitars » de Karoryfer Lecolds : une Gretsch Anniversary et une Höfner Club " +
                        "enregistrées note par note par Brian Wood, en jeu normal et étouffé.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Licence CC0 (domaine public) : « Royalty-free for all commercial and non-commercial use. Copyright 2022 Karoryfer Lecolds ». " +
                        "Source : github.com/sfzinstruments/karoryfer.black-and-green-guitars. Merci à eux !",
                    color = X.Texte2,
                )
            }
            XCard {
                Text("👂 Comment l'appli reconnaît tes accords", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Le micro découpe le son en fréquences, garde les pics (les notes), corrige l'accordage général de ta guitare " +
                        "et range tout sur les 12 notes de la gamme. Ce profil est comparé au modèle de l'accord attendu, " +
                        "harmoniques comprises, puis aux accords voisins et aux erreurs de doigt courantes. " +
                        "Ça marche en son clair comme saturé, en grattant vers le bas comme vers le haut, et une corde un peu étouffée est tolérée.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Le rythme, lui, est jugé sur les attaques du médiator, comme dans XPert Drum.",
                    color = X.Texte2,
                )
            }
            XCard {
                Text("XPert Guitare", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
                Text("Appli personnelle d'entraînement à la guitare électrique rythmique, de la famille XPert.", color = X.Texte2)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
