package fr.xpert.guitar.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fr.xpert.guitar.audio.AudioEngine
import fr.xpert.guitar.audio.MicListener
import fr.xpert.guitar.data.ProgressStore
import fr.xpert.guitar.practice.MetronomeScheduler
import fr.xpert.guitar.ui.Nav
import fr.xpert.guitar.ui.components.PrimaryButton
import fr.xpert.guitar.ui.components.ScreenHeader
import fr.xpert.guitar.ui.components.SecondaryButton
import fr.xpert.guitar.ui.components.XCard
import fr.xpert.guitar.ui.theme.X
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** Permission micro : (accordée ?, demander). */
@Composable
fun rememberMicPermission(onResult: (Boolean) -> Unit = {}): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        onResult(it)
    }
    return granted to { launcher.launch(Manifest.permission.RECORD_AUDIO) }
}

/** Micro partagé par un écran : démarré tant que [enabled], arrêté en quittant l'écran. */
@Composable
fun rememberMic(engine: AudioEngine, enabled: Boolean): MicListener {
    val context = LocalContext.current
    val mic = remember { MicListener(context.applicationContext, engine.sampleRate) }
    DisposableEffect(enabled) {
        if (enabled) mic.start()
        onDispose { mic.stop() }
    }
    return mic
}

/** Vumètre horizontal (échelle logarithmique) avec repère du seuil. */
@Composable
fun LevelMeter(level: Float, threshold: Float?, modifier: Modifier = Modifier, flash: Color? = null) {
    fun pos(v: Float): Float {
        val db = 20 * log10(max(v, 1e-4f))
        return ((db + 60f) / 60f).coerceIn(0f, 1f) // -60 dB .. 0 dB
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(X.Pastille, RoundedCornerShape(11.dp))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(pos(level))
                .background(flash ?: X.Jaune, RoundedCornerShape(11.dp))
        )
        if (threshold != null) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pos(threshold))
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(width = 3.dp, height = 22.dp)
                        .background(X.Orange)
                )
            }
        }
    }
}

private enum class Step { INTRO, SILENCE, CLICKS, HITS, DONE }

@Composable
fun MicSetupScreen(engine: AudioEngine, store: ProgressStore, nav: Nav) {
    var step by remember { mutableStateOf(Step.INTRO) }
    var startRequested by remember { mutableStateOf(false) }
    val (granted, requestPermission) = rememberMicPermission { ok -> if (ok) startRequested = true }
    val mic = rememberMic(engine, granted)
    val metro = remember { MetronomeScheduler(engine, 70, accent = false) }

    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var level by remember { mutableFloatStateOf(0f) }
    var stepStart by remember { mutableLongStateOf(0L) }
    var noise by remember { mutableFloatStateOf(0f) }
    var bleed by remember { mutableFloatStateOf(0f) }
    val clickDeltas = remember { mutableStateListOf<Int>() }
    val hitLevels = remember { mutableStateListOf<Float>() }
    var offsetMs by remember { mutableIntStateOf(0) }
    var threshold by remember { mutableFloatStateOf(store.micThreshold) }
    var clickTooLoud by remember { mutableStateOf(false) }
    var lastOnset by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) { onDispose { if (engine.scheduler === metro) engine.scheduler = null } }

    fun go(s: Step) {
        step = s
        stepStart = System.nanoTime()
        mic.onsets.clear()
        when (s) {
            Step.SILENCE -> { noise = 0f; mic.detector.threshold = 10f }
            Step.CLICKS -> {
                bleed = 0f; clickDeltas.clear()
                mic.detector.threshold = max(noise * 2.5f, 0.003f)
                metro.beatMarks.clear()
                engine.scheduler = metro
            }
            Step.HITS -> {
                engine.scheduler = null
                hitLevels.clear()
                mic.detector.threshold = max(max(noise * 3f, bleed * 1.2f), 0.004f)
            }
            Step.DONE -> {
                // Seuil : au-dessus du bruit et du clic du téléphone, mais nettement sous le coup le plus doux.
                val quietest = hitLevels.sorted().let { if (it.size >= 3) it[1] else it.firstOrNull() ?: 0.05f }
                val floor = max(noise * 3f, bleed * 1.4f).coerceAtLeast(0.004f)
                val ceiling = quietest * 0.5f
                clickTooLoud = bleed > 0f && floor > ceiling
                threshold = if (clickTooLoud) ceiling else min(floor * 1.5f, ceiling).coerceAtLeast(floor)
                offsetMs = if (clickDeltas.size >= 4) clickDeltas.sorted()[clickDeltas.size / 2] else 0
                mic.detector.threshold = threshold
            }
            Step.INTRO -> {}
        }
    }

    LaunchedEffect(granted) {
        while (true) {
            withFrameNanos { }
            now = System.nanoTime()
            val p = mic.detector.takePeak()
            level = max(p, level * 0.85f)
            if (startRequested && step == Step.INTRO && mic.active) {
                startRequested = false
                go(Step.SILENCE)
            }
            val elapsed = (now - stepStart) / 1_000_000L
            when (step) {
                Step.SILENCE -> {
                    if (elapsed > 300) noise = max(noise, p)
                    if (elapsed > 2500) go(Step.CLICKS)
                }
                Step.CLICKS -> {
                    bleed = max(bleed, p)
                    while (true) {
                        val o = mic.onsets.poll() ?: break
                        val beat = metro.beatMarks.minByOrNull { abs(o.nanos - engine.frameToNanos(it.frame)) } ?: continue
                        val d = ((o.nanos - engine.frameToNanos(beat.frame)) / 1_000_000L).toInt()
                        if (abs(d) < 250) clickDeltas.add(d)
                    }
                    if (elapsed > 7500) go(Step.HITS)
                }
                Step.HITS -> {
                    while (true) {
                        val o = mic.onsets.poll() ?: break
                        hitLevels.add(o.level)
                        lastOnset = now
                    }
                    if (hitLevels.size >= 8 || (elapsed > 25_000 && hitLevels.size >= 3)) go(Step.DONE)
                }
                Step.DONE -> {
                    while (true) {
                        mic.onsets.poll() ?: break
                        lastOnset = now
                    }
                }
                Step.INTRO -> mic.onsets.clear()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Réglage du micro") { nav.back() }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (step != Step.INTRO) {
                LevelMeter(
                    level,
                    if (step == Step.HITS || step == Step.DONE) mic.detector.threshold else null,
                    flash = if (now - lastOnset < 150_000_000L) X.Vert else null,
                )
            }
            when (step) {
                Step.INTRO -> {
                    Text(
                        "L'appli écoute ta guitare avec le micro du téléphone : elle repère chaque coup de médiator et reconnaît les accords. " +
                            "Ce réglage (1 minute) mesure le bruit de la pièce, le délai du téléphone et le volume de ta guitare.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    XCard {
                        Text("Avant de commencer", color = X.Jaune, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "• Pose le téléphone à 50 cm ou 1 m du haut-parleur de l'ampli, sur un meuble ou un pupitre, pas sur l'ampli lui-même (les vibrations fausseraient tout).\n" +
                                "• Micro du téléphone tourné vers l'ampli, rien dessus. Règle l'ampli à un volume normal de travail.\n" +
                                "• Monte le volume du téléphone pour entendre le clic. Des écouteurs filaires, c'est encore mieux. Évite le Bluetooth : il ajoute trop de délai.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    PrimaryButton("🎤 Commencer", Modifier.fillMaxWidth()) {
                        if (granted) go(Step.SILENCE) else requestPermission()
                    }
                    if (!granted) {
                        Text("Android va te demander l'autorisation d'utiliser le micro. Le son n'est ni enregistré ni envoyé : il est analysé en direct.", color = X.Texte2)
                    }
                }
                Step.SILENCE -> StepCard("1 / 3 · Silence", "Ne joue pas, on écoute le bruit de la pièce…", (now - stepStart) / 2500_000_000f)
                Step.CLICKS -> StepCard(
                    "2 / 3 · Le clic",
                    "Ne joue pas : le téléphone joue quelques clics et vérifie ce que le micro en entend.",
                    (now - stepStart) / 7500_000_000f,
                )
                Step.HITS -> {
                    StepCard(
                        "3 / 3 · Ta guitare",
                        "Gratte 8 accords séparés (Mi mineur par exemple), à ton volume NORMAL ou un peu doux, en laissant un temps entre chaque.",
                        hitLevels.size / 8f,
                    )
                    Text("${hitLevels.size} / 8 accords entendus", color = X.Texte2)
                }
                Step.DONE -> {
                    XCard {
                        Text("✔ Réglage terminé", color = X.Vert, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (clickDeltas.size >= 4) "Délai du téléphone mesuré : $offsetMs ms (corrigé automatiquement)."
                            else "Le micro n'a presque pas entendu le clic : parfait, il ne sera pas confondu avec ta guitare. Délai non mesuré (0 ms).",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Essaie maintenant : chaque coup de médiator doit allumer la jauge en vert, et rien ne doit s'allumer quand tu ne joues pas.", color = X.Texte2)
                    }
                    if (clickTooLoud) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(X.Orange.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text("⚠️ Le clic du téléphone est presque aussi fort que ta guitare", color = X.OrangeTexte, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Le micro risque de prendre le clic pour un accord. Baisse un peu le volume, éloigne le téléphone du haut-parleur, ou utilise des écouteurs filaires, puis refais le réglage.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryButton("Refaire", Modifier.weight(1f)) { go(Step.SILENCE) }
                        PrimaryButton("Enregistrer", Modifier.weight(1f)) {
                            store.micThreshold = threshold
                            store.micOffsetMs = offsetMs
                            nav.back()
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StepCard(title: String, text: String, progress: Float) {
    XCard {
        Text(title, color = X.Jaune, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(X.Pastille, RoundedCornerShape(4.dp))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(X.Orange, RoundedCornerShape(4.dp))
            )
        }
    }
}

/** Gros indicateur pour l'exercice au micro : pulse à chaque coup entendu, couleur du verdict. */
@Composable
fun MicHitIndicator(level: Float, threshold: Float, flashColor: Color?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier
                .size(120.dp)
                .background((flashColor ?: X.Pastille), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("🎸", fontSize = 48.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Joue sur ta guitare : le téléphone écoute",
            color = X.Texte2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        LevelMeter(level, threshold, Modifier.padding(horizontal = 24.dp))
    }
}
