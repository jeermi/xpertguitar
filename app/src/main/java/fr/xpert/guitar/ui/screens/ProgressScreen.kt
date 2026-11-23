package fr.xpert.guitar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.xpert.guitar.data.ProgressStore
import fr.xpert.guitar.practice.ExerciseKind
import fr.xpert.guitar.practice.Exercises
import fr.xpert.guitar.ui.Nav
import fr.xpert.guitar.ui.components.Pill
import fr.xpert.guitar.ui.components.ScreenHeader
import fr.xpert.guitar.ui.components.SectionLabel
import fr.xpert.guitar.ui.components.XCard
import fr.xpert.guitar.ui.theme.X
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProgressScreen(store: ProgressStore, nav: Nav) {
    val history = store.history()
    val bests = store.bests()
    val minutes = store.totalPracticeSeconds() / 60
    val days7 = practiceDaysLast7(history.map { it.atMillis })
    val fmt = SimpleDateFormat("EEE d MMM · HH:mm", Locale.FRANCE)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Ma progression") { nav.back() }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("$minutes", "min de pratique", Modifier.weight(1f))
                BigStat("${history.size}", "séances", Modifier.weight(1f))
                BigStat("$days7/7", "jours cette semaine", Modifier.weight(1f))
            }

            SectionLabel("Records par exercice")
            if (bests.isEmpty()) {
                Text("Pas encore de séance. Lance ton premier exercice !", color = X.Texte2)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (ex in Exercises.all) {
                    val b = bests[ex.id] ?: continue
                    XCard {
                        Text(ex.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (ex.kind) {
                                ExerciseKind.CHANGES -> Pill("${b.changes} changements / min", if (b.changes >= 30) X.Vert else X.Jaune)
                                ExerciseKind.CHORD_CHECK -> Pill("${b.accuracy} % d'accords justes", if (b.accuracy >= 80) X.Vert else X.Jaune)
                                ExerciseKind.RHYTHM -> {
                                    Pill("Rythme ${b.accuracy} %", if (b.accuracy >= 80) X.Vert else X.Jaune)
                                    if (b.chordAccuracy >= 0) {
                                        Spacer(Modifier.width(6.dp))
                                        Pill("Accords ${b.chordAccuracy} %", if (b.chordAccuracy >= 80) X.Vert else X.Jaune)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Pill(if (b.bpm > 0) "${b.bpm} BPM" else "< 80 %")
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${b.sessions} séance(s)", color = X.Texte2)
                    }
                }
            }

            SectionLabel("Dernières séances")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in history.takeLast(25).reversed()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.bodyLarge)
                            Text(fmt.format(Date(r.atMillis)), color = X.Texte2, style = MaterialTheme.typography.bodyMedium)
                        }
                        val label = when {
                            r.changes > 0 -> "${r.changes} changements"
                            r.bpm > 0 && r.chordAccuracy >= 0 -> "${r.accuracy} % · accords ${r.chordAccuracy} % · ${r.bpm} BPM"
                            r.bpm > 0 -> "${r.accuracy} % · ${r.bpm} BPM"
                            else -> "${r.accuracy} %"
                        }
                        Text(label, color = if (r.accuracy >= 80) X.Vert else X.Jaune, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier) {
    XCard(modifier) {
        Text(value, color = X.Jaune, style = MaterialTheme.typography.headlineSmall)
        Text(label, color = X.Texte2, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun practiceDaysLast7(times: List<Long>): Int {
    val tz = java.util.TimeZone.getDefault()
    fun day(t: Long) = (t + tz.getOffset(t)) / 86_400_000L
    val today = day(System.currentTimeMillis())
    return times.map { day(it) }.filter { today - it in 0..6 }.distinct().size
}
