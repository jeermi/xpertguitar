package fr.xpert.guitar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import fr.xpert.guitar.audio.StrumDir
import fr.xpert.guitar.audio.StrumKind
import fr.xpert.guitar.practice.PracticeSession
import fr.xpert.guitar.ui.theme.X

/**
 * Rythmique qui défile de droite à gauche : flèches ↓ (vers le bas) et ↑ (vers le haut),
 * « PM » sous les coups étouffés, accord écrit au début de chaque changement.
 * La barre jaune est l'instant présent : on gratte quand la flèche la touche.
 * Les flèches passées prennent la couleur du verdict.
 */
@Composable
fun StrumLane(s: PracticeSession, nowNanos: Long, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(modifier) { drawLane(tm, s, nowNanos) }
}

private const val FUTURE_NS = 3_000_000_000.0

private fun DrawScope.drawLane(tm: TextMeasurer, s: PracticeSession, now: Long) {
    fun lbl(t: String, x: Float, y: Float, size: Float, color: Color, left: Boolean = false) = label(tm, t, x, y, size, color, left)
    val w = size.width
    val h = size.height
    val nowX = w * 0.25f
    val pxPerNs = (w - nowX) / FUTURE_NS
    fun xOf(t: Long) = nowX + ((t - now) * pxPerNs).toFloat()
    val midY = h * 0.58f

    // Mesures et temps
    for (b in s.beatMarks) {
        val x = xOf(s.frameNanos(b.frame))
        if (x < -10 || x > w + 10) continue
        val bar = b.number == 1
        drawLine(
            if (bar) X.CarteBord.copy(alpha = 0.95f) else X.CarteBord.copy(alpha = 0.45f),
            Offset(x, h * 0.3f), Offset(x, h * 0.92f), if (bar) 4f else 2f,
        )
    }
    // Ligne de jeu
    drawLine(X.CarteBord, Offset(0f, midY), Offset(w, midY), 2f)

    // Accords (au début de chaque changement)
    var lastShape: Any? = null
    for (ev in s.strums) {
        if (ev.shape == lastShape) continue
        lastShape = ev.shape
        val x = xOf(s.frameNanos(ev.frame))
        if (x < -80 || x > w + 10) continue
        lbl(ev.shape.symbol, x + 6f, h * 0.2f, h * 0.15f, X.Jaune, left = true)
    }

    // Flèches
    val arrowH = h * 0.36f
    for (ev in s.strums) {
        val t = s.frameNanos(ev.frame)
        val x = xOf(t)
        if (x < -30 || x > w + 30) continue
        val color = ev.result?.let { X.judgement(it) } ?: if (t < now) X.Texte2 else X.Texte
        val accent = ev.strum.accent
        val len = arrowH * (if (accent) 1.1f else if (ev.strum.dir == StrumDir.UP) 0.75f else 0.9f)
        val down = ev.strum.dir == StrumDir.DOWN
        val y0 = if (down) midY - len / 2 else midY + len / 2
        val y1 = if (down) midY + len / 2 else midY - len / 2
        val sw = if (accent) 9f else 6f
        drawLine(color, Offset(x, y0), Offset(x, y1), sw, StrokeCap.Round)
        val head = len * 0.32f
        val dir = if (down) 1f else -1f
        val p = Path().apply {
            moveTo(x, y1 + dir * sw * 0.6f)
            lineTo(x - head * 0.55f, y1 - dir * head)
            lineTo(x + head * 0.55f, y1 - dir * head)
            close()
        }
        drawPath(p, color)
        if (ev.strum.kind == StrumKind.PALM_MUTE) lbl("PM", x, h * 0.99f, h * 0.1f, X.Texte2)
        if (accent) lbl(">", x, h * 0.34f, h * 0.13f, X.Jaune)
    }

    // Maintenant
    drawLine(X.Jaune, Offset(nowX, h * 0.28f), Offset(nowX, h * 0.94f), 5f)
    // Fondu à gauche
    drawRect(Color(0xCC1F1F53), Offset.Zero, androidx.compose.ui.geometry.Size(w * 0.04f, h))
}

