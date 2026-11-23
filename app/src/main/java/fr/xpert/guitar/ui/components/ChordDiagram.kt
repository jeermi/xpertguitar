package fr.xpert.guitar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import fr.xpert.guitar.music.Shape
import fr.xpert.guitar.ui.theme.X

/**
 * Diagramme d'accord classique : le manche vu de face, cordes verticales (6e corde à gauche),
 * frettes horizontales, sillet en haut. Ronds numérotés = doigts (1 index … 4 auriculaire),
 * « o » = corde à vide, « × » = corde à ne pas jouer, barre = barré.
 * [highlightString] : corde à signaler (fausse note entendue), en rouge.
 */
@Composable
fun ChordDiagram(shape: Shape, modifier: Modifier = Modifier, highlightString: Int = -1, showFingers: Boolean = true) {
    val tm = rememberTextMeasurer()
    Canvas(modifier.aspectRatio(0.82f)) { drawChord(tm, shape, highlightString, showFingers) }
}

fun DrawScope.drawChord(tm: TextMeasurer, shape: Shape, highlightString: Int = -1, showFingers: Boolean = true) {
    fun txt(t: String, x: Float, y: Float, size: Float, color: Color) = label(tm, t, x, y, size, color)
    val w = size.width
    val h = size.height
    val frets = 5
    val maxFret = shape.maxFret
    val base = if (maxFret <= frets) 1 else shape.minFret
    val left = w * 0.17f
    val right = w * 0.9f
    val top = h * 0.17f
    val bottom = h * 0.96f
    val sx = (right - left) / 5f
    val sy = (bottom - top) / frets
    val line = X.Texte2
    val stroke = w * 0.012f
    val dotR = minOf(sx, sy) * 0.36f

    // Frettes et sillet
    for (f in 0..frets) {
        val y = top + f * sy
        val nut = f == 0 && base == 1
        drawLine(if (nut) X.Texte else line, Offset(left - stroke / 2, y), Offset(right + stroke / 2, y), if (nut) stroke * 4 else stroke)
    }
    // Cordes (plus épaisses côté grave)
    for (s in 0 until 6) {
        val x = left + s * sx
        val c = if (s == highlightString) X.Rouge else line
        drawLine(c, Offset(x, top), Offset(x, bottom), stroke * (1.9f - s * 0.18f) * (if (s == highlightString) 1.6f else 1f))
    }
    // Repère de case si on n'est pas en haut du manche
    if (base > 1) txt("${base}e", left - sx * 0.55f, top + sy * 0.62f, sy * 0.42f, X.Texte2)

    // Barré
    if (shape.barre > 0) {
        val strings = (0 until 6).filter { shape.frets[it] == shape.barre }
        if (strings.size >= 2) {
            val y = top + (shape.barre - base + 0.5f) * sy
            val x0 = left + strings.first() * sx
            val x1 = left + strings.last() * sx
            drawRoundRect(X.Jaune, Offset(x0 - dotR, y - dotR * 0.8f), Size(x1 - x0 + 2 * dotR, dotR * 1.6f), CornerRadius(dotR, dotR))
            if (showFingers) txt("1", (x0 + x1) / 2, y + dotR * 0.45f, dotR * 1.3f, X.Marine)
        }
    }
    // Doigts, cordes à vide et muettes
    for (s in 0 until 6) {
        val x = left + s * sx
        val f = shape.frets[s]
        val markY = top - sy * 0.42f
        when {
            f < 0 -> {
                val r = dotR * 0.55f
                drawLine(X.Texte2, Offset(x - r, markY - r), Offset(x + r, markY + r), stroke * 1.6f, StrokeCap.Round)
                drawLine(X.Texte2, Offset(x - r, markY + r), Offset(x + r, markY - r), stroke * 1.6f, StrokeCap.Round)
            }
            f == 0 -> drawCircle(if (s == highlightString) X.Rouge else X.Texte, dotR * 0.55f, Offset(x, markY), style = Stroke(stroke * 1.4f))
            else -> {
                if (shape.barre > 0 && f == shape.barre && shape.fingers[s] == 1) continue
                val y = top + (f - base + 0.5f) * sy
                drawCircle(if (s == highlightString) X.Rouge else X.Orange, dotR, Offset(x, y))
                if (showFingers && shape.fingers[s] > 0) txt("${shape.fingers[s]}", x, y + dotR * 0.45f, dotR * 1.3f, Color.White)
            }
        }
    }
}

/** Texte centré horizontalement sur [x], ligne de base approximative en [y]. */
fun DrawScope.label(tm: TextMeasurer, t: String, x: Float, y: Float, size: Float, color: Color, alignLeft: Boolean = false) {
    val layout = tm.measure(t, TextStyle(color = color, fontSize = size.toSp(), fontWeight = FontWeight.Bold))
    val left = if (alignLeft) x else x - layout.size.width / 2f
    drawText(layout, topLeft = Offset(left, y - layout.firstBaseline))
}
