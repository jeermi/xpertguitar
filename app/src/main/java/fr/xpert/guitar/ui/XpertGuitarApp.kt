package fr.xpert.guitar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.xpert.guitar.audio.AudioEngine
import fr.xpert.guitar.data.ProgressStore
import fr.xpert.guitar.ui.components.XpertBackground
import fr.xpert.guitar.ui.theme.X
import fr.xpert.guitar.ui.screens.ExerciseScreen
import fr.xpert.guitar.ui.screens.ExercisesScreen
import fr.xpert.guitar.ui.screens.WelcomeScreen
import fr.xpert.guitar.ui.screens.HomeScreen
import fr.xpert.guitar.ui.screens.LessonScreen
import fr.xpert.guitar.ui.screens.LessonsScreen
import fr.xpert.guitar.ui.screens.MetronomeScreen
import fr.xpert.guitar.ui.screens.MicSetupScreen
import fr.xpert.guitar.ui.screens.ProgressScreen
import fr.xpert.guitar.ui.screens.TunerScreen
import fr.xpert.guitar.ui.screens.ChordsScreen
import fr.xpert.guitar.ui.screens.ChordDetailScreen
import fr.xpert.guitar.ui.screens.CreditsScreen

sealed interface Screen {
    data object Home : Screen
    data object Lessons : Screen
    data class Lesson(val id: String) : Screen
    data object Exercises : Screen
    data class Exercise(val id: String) : Screen
    data object Metronome : Screen
    data object Tuner : Screen
    data object Chords : Screen
    data class ChordDetail(val id: String) : Screen
    data object Credits : Screen
    data object Name : Screen
    data object Progress : Screen
    data object MicSetup : Screen
}

class Nav(private val get: () -> List<Screen>, private val set: (List<Screen>) -> Unit) {
    fun go(s: Screen) = set(get() + s)
    fun back() {
        val st = get()
        if (st.size > 1) set(st.dropLast(1))
    }
}

@Composable
fun XpertGuitarApp(engine: AudioEngine, store: ProgressStore) {
    // Premier lancement : on commence par demander le prénom.
    var stack by remember {
        mutableStateOf(if (store.firstName.isEmpty()) listOf<Screen>(Screen.Home, Screen.Name) else listOf<Screen>(Screen.Home))
    }
    val nav = remember { Nav({ stack }, { stack = it }) }
    BackHandler(enabled = stack.size > 1 && !(stack.last() == Screen.Name && store.firstName.isEmpty())) { nav.back() }

    XpertBackground {
        // Sans Surface à la racine, le texte serait noir par défaut : on fixe la couleur pour tout l'écran.
        CompositionLocalProvider(LocalContentColor provides X.Texte) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            // key() : chaque écran (et chaque leçon / exercice) a son propre état, défilement compris.
            // Sans ça, passer à la leçon ou à l'exercice suivant réutilisait l'état du précédent
            // (page restée en bas, bilan ou tempo de l'exercice d'avant).
            val current = stack.last()
            key(stack.size, current) {
            when (val s = current) {
                Screen.Home -> HomeScreen(store, nav)
                Screen.Lessons -> LessonsScreen(store, nav)
                is Screen.Lesson -> LessonScreen(s.id, engine, store, nav)
                Screen.Exercises -> ExercisesScreen(store, nav)
                is Screen.Exercise -> ExerciseScreen(s.id, engine, store, nav)
                Screen.Metronome -> MetronomeScreen(engine, store, nav)
                Screen.Tuner -> TunerScreen(engine, nav)
                Screen.Chords -> ChordsScreen(store, nav)
                is Screen.ChordDetail -> ChordDetailScreen(s.id, engine, store, nav)
                Screen.Credits -> CreditsScreen(nav)
                Screen.Name -> WelcomeScreen(store, nav)
                Screen.Progress -> ProgressScreen(store, nav)
                Screen.MicSetup -> MicSetupScreen(engine, store, nav)
            }
            }
        }
        }
    }
}
