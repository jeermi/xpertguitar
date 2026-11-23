package fr.xpert.guitar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.xpert.guitar.audio.AudioEngine
import fr.xpert.guitar.content.Block
import fr.xpert.guitar.content.Lessons
import fr.xpert.guitar.data.ProgressStore
import fr.xpert.guitar.practice.StrumPlayer
import fr.xpert.guitar.music.Chords
import fr.xpert.guitar.ui.components.ChordDiagram
import androidx.compose.foundation.layout.size
import fr.xpert.guitar.ui.Nav
import fr.xpert.guitar.ui.Screen
import fr.xpert.guitar.ui.components.Illustration
import fr.xpert.guitar.ui.components.Pill
import fr.xpert.guitar.ui.components.PrimaryButton
import fr.xpert.guitar.ui.components.ScreenHeader
import fr.xpert.guitar.ui.components.SecondaryButton
import fr.xpert.guitar.ui.components.XCard
import fr.xpert.guitar.ui.theme.X
import kotlinx.coroutines.delay

@Composable
fun LessonsScreen(store: ProgressStore, nav: Nav) {
    val read = store.lessonsRead()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Les bases") { nav.back() }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "À lire dans l'ordre avant tes premiers exercices. Tu peux y revenir quand tu veux. Les gauchers inversent simplement les mains.",
                color = X.Texte2,
            )
            Lessons.all.forEachIndexed { i, lesson ->
                XCard(onClick = { nav.go(Screen.Lesson(lesson.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(lesson.icon, fontSize = 30.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${i + 1}. ${lesson.title}", style = MaterialTheme.typography.titleMedium, color = X.Texte)
                            Text(lesson.summary, color = X.Texte2, style = MaterialTheme.typography.bodyMedium)
                            if (lesson.id in read) {
                                Spacer(Modifier.height(6.dp))
                                Pill("✔ Lue", X.Vert)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun LessonScreen(id: String, engine: AudioEngine, store: ProgressStore, nav: Nav) {
    val lesson = Lessons.byId(id) ?: return
    LaunchedEffect(id) { store.markLessonRead(id) }
    DisposableEffect(id) { onDispose { if (engine.scheduler is StrumPlayer) engine.scheduler = null } }
    val index = Lessons.all.indexOf(lesson)
    val next = Lessons.all.getOrNull(index + 1)
    // Exemple en cours de lecture : un bloc (accord ou rythmique)
    var playing by remember { mutableStateOf<Any?>(null) }
    val current = playing
    if (current != null) {
        LaunchedEffect(current) {
            val player = when (current) {
                is Block.Advice -> current.example?.let { StrumPlayer.rhythm(engine, it.rhythm, it.grid, it.bpm, it.bars, it.click) }
                    ?: current.chord?.let { StrumPlayer.chord(engine, Chords.get(it), arpeggio = false) }
                is Block.Chord -> StrumPlayer.chord(engine, Chords.get(current.shapeId))
                else -> null
            }
            if (player != null) {
                engine.scheduler = player
                delay(player.durationMs)
                if (engine.scheduler === player) engine.scheduler = null
            }
            playing = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(lesson.title) { nav.back() }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            for (b in lesson.blocks) {
                BlockView(
                    b,
                    isPlaying = b === current,
                    onPlay = { blk ->
                        if (playing === blk) {
                            playing = null
                            if (engine.scheduler is StrumPlayer) engine.scheduler = null
                        } else {
                            playing = blk
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            if (next != null) {
                PrimaryButton("Leçon suivante : ${next.title}", Modifier.fillMaxWidth()) {
                    nav.back(); nav.go(Screen.Lesson(next.id))
                }
            } else {
                PrimaryButton("Passer aux exercices", Modifier.fillMaxWidth()) {
                    nav.back(); nav.go(Screen.Exercises)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BlockView(
    b: Block,
    isPlaying: Boolean,
    onPlay: (Block) -> Unit,
) {
    when (b) {
        is Block.Text -> Text(b.text, style = MaterialTheme.typography.bodyLarge, color = X.Texte)
        is Block.Section -> Text(
            b.title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = X.Texte2,
            modifier = Modifier.padding(top = 8.dp),
        )
        is Block.Advice -> XCard {
            if (b.title != null) {
                Text(b.title, style = MaterialTheme.typography.titleMedium, color = X.Jaune)
                Spacer(Modifier.height(10.dp))
            }
            Illustration(
                b.illus,
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(X.Fond.copy(alpha = 0.55f))
                    .padding(6.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(b.text, style = MaterialTheme.typography.bodyLarge, color = X.Texte)
            if (b.chord != null || b.example != null) {
                Spacer(Modifier.height(12.dp))
                val label = when {
                    isPlaying -> "■ Arrêter"
                    b.example != null -> "▶ Écouter la rythmique"
                    else -> "🔊 Écouter ${Chords.get(b.chord!!).symbol}"
                }
                SecondaryButton(label) { onPlay(b) }
            }
        }
        is Block.Chord -> XCard {
            val shape = Chords.get(b.shapeId)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChordDiagram(shape, Modifier.size(width = 96.dp, height = 117.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(shape.symbol, style = MaterialTheme.typography.headlineSmall, color = X.Jaune)
                    Text(b.text, style = MaterialTheme.typography.bodyLarge, color = X.Texte)
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(if (isPlaying) "■ Arrêter" else "🔊 Écouter") { onPlay(b) }
                }
            }
        }
        is Block.Tip -> Callout("💡 Astuce", b.text, X.Jaune, X.Jaune)
        is Block.Warning -> Callout("⚠️ Attention", b.text, X.Orange, X.OrangeTexte)
    }
}

@Composable
private fun Callout(title: String, text: String, color: Color, titleColor: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(title, color = titleColor, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = X.Texte)
    }
}
