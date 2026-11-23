package fr.xpert.guitar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.xpert.guitar.practice.Judgement

/** Charte XPert : marine + orange du logo, jaune d'accent et cartes des applis XPert 3e / 2de. */
object X {
    val Marine = Color(0xFF1E2054)
    val Marine2 = Color(0xFF2C2F72)
    val Orange = Color(0xFFE8501E)
    /** Orange éclairci pour du texte sur fond sombre (contraste suffisant). */
    val OrangeTexte = Color(0xFFFF8A5C)
    val OrangeFonce = Color(0xFFC9410F)
    val Jaune = Color(0xFFFBC63C)
    val JauneLogo = Color(0xFFFFAB19)
    val Fond = Color(0xFF12133A)
    val FondHaut = Color(0xFF17183F)
    val Carte = Color(0xFF1F1F53)
    val CarteBord = Color(0xFF34357A)
    val Pastille = Color(0xFF2B2974)
    val Texte = Color(0xFFFFFFFF)
    val Texte2 = Color(0xFFB9BADD)
    val Vert = Color(0xFF2BB673)
    val Bleu = Color(0xFF5B8DEF)
    val Rouge = Color(0xFFE5484D)

    fun judgement(j: Judgement?): Color = when (j) {
        Judgement.PERFECT -> Vert
        Judgement.EARLY -> Bleu
        Judgement.LATE -> OrangeTexte
        Judgement.MISS -> Rouge
        Judgement.EXTRA -> Texte2
        null -> Texte
    }
}

private val colors = darkColorScheme(
    primary = X.Orange,
    onPrimary = Color.White,
    secondary = X.Jaune,
    onSecondary = X.Marine,
    background = X.Fond,
    onBackground = X.Texte,
    surface = X.Carte,
    onSurface = X.Texte,
    surfaceVariant = X.Pastille,
    onSurfaceVariant = X.Texte2,
    outline = X.CarteBord,
    error = X.Rouge,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
)

@Composable
fun XpertTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
