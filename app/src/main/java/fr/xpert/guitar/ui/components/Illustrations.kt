package fr.xpert.guitar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import fr.xpert.guitar.content.Illus
import fr.xpert.guitar.ui.theme.X
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Une illustration de leçon : schéma vectoriel 16:9, aux couleurs XPert. */
@Composable
fun Illustration(illus: Illus, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        Sketch(this, tm).draw(illus)
    }
}

// Palette des schémas
private val W = X.Texte
private val M = X.Texte2
private val Y = X.Jaune
private val O = X.Orange
private val OT = X.OrangeTexte
private val G = X.Vert
private val R = X.Rouge
private val WOOD = Color(0xFFE6C08A)
private val WOOD_D = Color(0xFFC99A5E)
private val ROSE = Color(0xFF6B4228)
private val BODY = Color(0xFFD23B2E)
private val BODY_D = Color(0xFF9E2A21)
private val GUARD = Color(0xFFF2EFE6)
private val METAL = Color(0xFFC9CCD8)
private val STRING = Color(0xFFE4E6EE)
private val AMP = Color(0xFF2A2B33)
private val AMP_GRILL = Color(0xFF55576A)
private val PICK = Color(0xFFFBC63C)
private val SKIN = Color(0xFFF1C7A5)
private val SKIN_L = Color(0xFFF8DCC4)
private val SKIN_D = Color(0xFFD9A07E)
private val SKIN_DD = Color(0xFFB97E5E)
private val NAIL = Color(0xFFF9E3D6)
private val HAIR = Color(0xFF5B3A26)
private val SHIRT = Color(0xFFE8501E)
private val SHIRT_D = Color(0xFFC63F14)
private val PANTS = Color(0xFF7A80C4)
private val PANTS_D = Color(0xFF555A9E)
private val SHOE = Color(0xFFE9E9F2)
private val DIM = Color(0xFF34357A)

/**
 * Petit outil de dessin en coordonnées virtuelles 160 × 90 (sol à y = 84).
 */
private class Sketch(val d: DrawScope, val tm: TextMeasurer) {
    val u = d.size.width / 160f

    fun o(x: Float, y: Float) = Offset(x * u, y * u)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, c: Color = W, w: Float = 1.6f, dash: Boolean = false) {
        d.drawLine(
            c, o(x1, y1), o(x2, y2), w * u, StrokeCap.Round,
            pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(2.5f * u, 2f * u)) else null,
        )
    }

    fun circle(x: Float, y: Float, r: Float, c: Color = W, fill: Boolean = false, w: Float = 1.4f) {
        if (fill) d.drawCircle(c, r * u, o(x, y)) else d.drawCircle(c, r * u, o(x, y), style = Stroke(w * u))
    }

    fun oval(x: Float, y: Float, rx: Float, ry: Float, c: Color = W, fill: Boolean = false, w: Float = 1.4f) {
        val tl = o(x - rx, y - ry)
        val sz = Size(2 * rx * u, 2 * ry * u)
        if (fill) d.drawOval(c, tl, sz) else d.drawOval(c, tl, sz, style = Stroke(w * u))
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, c: Color = W, fill: Boolean = false, r: Float = 1f, sw: Float = 1.4f) {
        val tl = o(x, y)
        val sz = Size(w * u, h * u)
        val cr = CornerRadius(r * u, r * u)
        if (fill) d.drawRoundRect(c, tl, sz, cr) else d.drawRoundRect(c, tl, sz, cr, style = Stroke(sw * u))
    }

    fun poly(vararg pts: Float, c: Color = W, w: Float = 1.6f, fill: Boolean = false, close: Boolean = false) {
        val p = Path()
        p.moveTo(pts[0] * u, pts[1] * u)
        var i = 2
        while (i < pts.size) {
            p.lineTo(pts[i] * u, pts[i + 1] * u); i += 2
        }
        if (close || fill) p.close()
        if (fill) d.drawPath(p, c) else d.drawPath(p, c, style = Stroke(w * u, cap = StrokeCap.Round))
    }

    /** Arc de cercle (degrés, 0 = droite, sens horaire). */
    fun arc(x: Float, y: Float, r: Float, start: Float, sweep: Float, c: Color = Y, w: Float = 1.4f, arrowEnd: Boolean = false) {
        d.drawArc(c, start, sweep, false, o(x - r, y - r), Size(2 * r * u, 2 * r * u), style = Stroke(w * u, cap = StrokeCap.Round))
        if (arrowEnd) {
            val a = (start + sweep) * PI.toFloat() / 180f
            val ex = x + r * cos(a)
            val ey = y + r * sin(a)
            val dir = a + (if (sweep > 0) PI.toFloat() / 2 else -PI.toFloat() / 2)
            head(ex, ey, dir, c, w)
        }
    }

    private fun head(x: Float, y: Float, dir: Float, c: Color, w: Float) {
        val l = 3.2f
        line(x, y, x - l * cos(dir - 0.5f), y - l * sin(dir - 0.5f), c, w)
        line(x, y, x - l * cos(dir + 0.5f), y - l * sin(dir + 0.5f), c, w)
    }

    fun arrow(x1: Float, y1: Float, x2: Float, y2: Float, c: Color = Y, w: Float = 1.4f, both: Boolean = false) {
        line(x1, y1, x2, y2, c, w)
        val a = atan2(y2 - y1, x2 - x1)
        head(x2, y2, a, c, w)
        if (both) head(x1, y1, a + PI.toFloat(), c, w)
    }

    fun text(s: String, x: Float, y: Float, c: Color = W, size: Float = 6f, bold: Boolean = false, align: Int = 0) {
        val style = TextStyle(
            color = c,
            fontSize = with(d) { (size * u).toSp() },
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
        val layout = tm.measure(s, style)
        val w = layout.size.width
        val h = layout.size.height
        val left = when (align) {
            0 -> x * u - w / 2f
            1 -> x * u
            else -> x * u - w
        }
        d.drawText(layout, topLeft = Offset(left, y * u - h / 2f))
    }

    fun check(x: Float, y: Float) {
        circle(x, y, 5f, G, fill = true)
        poly(x - 2.4f, y + 0.2f, x - 0.6f, y + 2f, x + 2.6f, y - 1.8f, c = X.Fond, w = 1.5f)
    }

    fun cross(x: Float, y: Float) {
        circle(x, y, 5f, R, fill = true)
        line(x - 2f, y - 2f, x + 2f, y + 2f, X.Fond, 1.5f)
        line(x - 2f, y + 2f, x + 2f, y - 2f, X.Fond, 1.5f)
    }

    fun floor() = line(4f, 84f, 156f, 84f, DIM, 1.2f)

    /** Baguette de (x1,y1) [talon] à (x2,y2) [olive]. */
    fun stick(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 2.4f, c: Color = WOOD) {
        line(x1, y1, x2, y2, c, w)
        val a = atan2(y2 - y1, x2 - x1)
        oval(x2 + cos(a) * 0.6f, y2 + sin(a) * 0.6f, w * 0.65f, w * 0.65f, c, fill = true)
    }

    // ------------------------------------------------------------------ Formes « réalistes »

    /** Segment de membre effilé de a (largeur wa) à b (largeur wb), extrémités arrondies. */
    fun limb(ax: Float, ay: Float, bx: Float, by: Float, wa: Float, wb: Float, c: Color) {
        val dx = bx - ax
        val dy = by - ay
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val nx = -dy / len
        val ny = dx / len
        val p = Path()
        p.moveTo((ax + nx * wa / 2) * u, (ay + ny * wa / 2) * u)
        p.lineTo((bx + nx * wb / 2) * u, (by + ny * wb / 2) * u)
        p.lineTo((bx - nx * wb / 2) * u, (by - ny * wb / 2) * u)
        p.lineTo((ax - nx * wa / 2) * u, (ay - ny * wa / 2) * u)
        p.close()
        d.drawPath(p, c)
        d.drawCircle(c, wa / 2 * u, o(ax, ay))
        d.drawCircle(c, wb / 2 * u, o(bx, by))
    }

    /** Membre avec un fin contour plus foncé (doigts, pouce) pour bien les distinguer. */
    fun flimb(ax: Float, ay: Float, bx: Float, by: Float, wa: Float, wb: Float, c: Color, edge: Color = SKIN_DD) {
        limb(ax, ay, bx, by, wa + 0.9f, wb + 0.9f, edge)
        limb(ax, ay, bx, by, wa, wb, c)
    }

    /** Membre avec contour de mise en évidence (jaune) quand [hl]. */
    fun limbHl(ax: Float, ay: Float, bx: Float, by: Float, wa: Float, wb: Float, c: Color, hl: Boolean) {
        if (hl) limb(ax, ay, bx, by, wa + 2.4f, wb + 2.4f, Y)
        limb(ax, ay, bx, by, wa, wb, c)
    }

    /** Forme pleine passant par des points (lissée par des courbes quadratiques). */
    fun blob(c: Color, vararg pts: Float) = blobE(c, null, *pts)

    fun blobE(c: Color, edge: Color?, vararg pts: Float) {
        val n = pts.size / 2
        val p = Path()
        fun mx(i: Int) = (pts[(2 * i) % pts.size] + pts[(2 * i + 2) % pts.size]) / 2f
        fun my(i: Int) = (pts[(2 * i + 1) % pts.size] + pts[(2 * i + 3) % pts.size]) / 2f
        p.moveTo(mx(0) * u, my(0) * u)
        for (k in 1..n) {
            val i = k % n
            p.quadraticBezierTo(pts[2 * i] * u, pts[2 * i + 1] * u, mx(i) * u, my(i) * u)
        }
        p.close()
        d.drawPath(p, c)
        if (edge != null) d.drawPath(p, edge, style = Stroke(0.7f * u))
    }

    /** Tête de profil tournée vers la droite (cheveux, oreille, nez, œil). */
    fun headSide(hx: Float, hy: Float, r: Float = 6f) {
        circle(hx - r * 0.12f, hy - r * 0.1f, r * 1.02f, HAIR, fill = true)
        blob(
            SKIN,
            hx - r * 0.35f, hy - r * 0.45f,
            hx + r * 0.75f, hy - r * 0.75f,
            hx + r * 1.02f, hy - r * 0.05f,
            hx + r * 0.95f, hy + r * 0.7f,
            hx + r * 0.2f, hy + r * 1.08f,
            hx - r * 0.55f, hy + r * 0.6f,
        )
        // nez et menton
        poly(hx + r * 0.95f, hy - r * 0.2f, hx + r * 1.25f, hy + r * 0.25f, hx + r * 0.95f, hy + r * 0.32f, c = SKIN, fill = true)
        // mèche de cheveux sur le front
        blob(HAIR, hx - r * 0.9f, hy - r * 0.6f, hx + r * 0.2f, hy - r * 1.15f, hx + r * 0.9f, hy - r * 0.7f, hx + r * 0.1f, hy - r * 0.45f)
        oval(hx - r * 0.15f, hy + r * 0.15f, r * 0.2f, r * 0.3f, SKIN_D, fill = true) // oreille
        circle(hx + r * 0.6f, hy - r * 0.12f, r * 0.11f, X.Fond, fill = true) // œil
    }

    /** Tête de face. */
    fun headFront(hx: Float, hy: Float, r: Float = 6.5f) {
        circle(hx, hy - r * 0.08f, r * 1.04f, HAIR, fill = true)
        oval(hx - r * 0.98f, hy + r * 0.15f, r * 0.2f, r * 0.32f, SKIN_D, fill = true)
        oval(hx + r * 0.98f, hy + r * 0.15f, r * 0.2f, r * 0.32f, SKIN_D, fill = true)
        oval(hx, hy + r * 0.15f, r * 0.88f, r * 0.98f, SKIN, fill = true)
        blob(HAIR, hx - r * 0.95f, hy - r * 0.2f, hx - r * 0.6f, hy - r * 1.1f, hx + r * 0.7f, hy - r * 1.1f, hx + r * 0.95f, hy - r * 0.2f, hx, hy - r * 0.55f)
        circle(hx - r * 0.33f, hy + r * 0.1f, r * 0.1f, X.Fond, fill = true)
        circle(hx + r * 0.33f, hy + r * 0.1f, r * 0.1f, X.Fond, fill = true)
        line(hx - r * 0.25f, hy + r * 0.62f, hx + r * 0.25f, hy + r * 0.62f, SKIN_D, 0.6f)
    }

    fun rotated(deg: Float, px: Float, py: Float, block: () -> Unit) {
        d.rotate(deg, o(px, py)) { block() }
    }

    // ------------------------------------------------------------------ Guitare

    /** Position (x) de la frette n (0 = sillet) sur un manche du sillet [nutX] vers le corps, longueur de corde [scale]. */
    fun fretX(nutX: Float, scale: Float, n: Int, dir: Float = -1f) =
        nutX + dir * scale * (1f - Math.pow(2.0, -n / 12.0).toFloat())

    /**
     * Guitare électrique vue de face, manche vers la droite (avant rotation), centrée sur le corps.
     * Repère local : corps de -23 à +17 en x, sillet à x = 56, tête jusqu'à 69.
     * [hl] met en valeur une partie (contour jaune).
     */
    fun guitar(cx: Float, cy: Float, angle: Float = 0f, s: Float = 1f, hl: String = "") {
        rotated(angle, cx, cy) {
            fun gx(x: Float) = cx + x * s
            fun gy(y: Float) = cy + y * s
            // Tête
            blobE(WOOD, WOOD_D, gx(55f), gy(-2.4f), gx(60f), gy(-4f), gx(68f), gy(-5.5f), gx(70f), gy(-3f), gx(68f), gy(1.5f), gx(60f), gy(3.2f), gx(55f), gy(2.4f))
            if (hl == "head") blobE(Color.Transparent, Y, gx(55f), gy(-2.4f), gx(60f), gy(-4f), gx(68f), gy(-5.5f), gx(70f), gy(-3f), gx(68f), gy(1.5f), gx(60f), gy(3.2f), gx(55f), gy(2.4f))
            for (i in 0 until 6) {
                val tx = gx(58.5f + i * 1.7f)
                val ty = gy(-3.9f - i * 0.25f)
                circle(tx, ty, 0.55f * s, METAL, fill = true)
                line(tx, ty, tx, ty - 1.3f * s, METAL, 0.5f * s)
                circle(tx, ty - 1.6f * s, 0.45f * s, METAL, fill = true)
            }
            // Manche et touche
            rect(gx(8f), gy(-2.3f), 48f * s, 4.6f * s, WOOD, fill = true, r = 0.6f * s)
            rect(gx(9f), gy(-2.1f), 47f * s, 4.2f * s, ROSE, fill = true, r = 0.4f * s)
            if (hl == "neck") rect(gx(8f), gy(-2.6f), 48f * s, 5.2f * s, Y, r = 0.8f * s, sw = 0.9f)
            for (n in 1..21) {
                val fx = fretX(gx(56f), 67f * s, n)
                if (fx < gx(9.5f)) break
                line(fx, gy(-2.1f), fx, gy(2.1f), if (hl == "frets") Y else METAL, (if (hl == "frets") 0.55f else 0.35f) * s)
            }
            for (n in listOf(3, 5, 7, 9)) {
                val m = (fretX(gx(56f), 67f * s, n) + fretX(gx(56f), 67f * s, n - 1)) / 2
                circle(m, gy(0f), 0.45f * s, GUARD, fill = true)
            }
            rect(gx(55.6f), gy(-2.3f), 0.9f * s, 4.6f * s, GUARD, fill = true, r = 0.2f)
            // Corps
            val body = floatArrayOf(
                gx(-21f), gy(-9f), gx(-24f), gy(0f), gx(-21f), gy(10f), gx(-12f), gy(13.5f), gx(-2f), gy(11f), gx(5f), gy(12f),
                gx(13f), gy(11.5f), gx(17f), gy(7.5f), gx(11f), gy(4.2f), gx(9f), gy(0f), gx(11f), gy(-4.5f), gx(17f), gy(-10f),
                gx(13f), gy(-12.5f), gx(3f), gy(-10f), gx(-8f), gy(-13.5f), gx(-17f), gy(-12.5f),
            )
            blobE(BODY, BODY_D, *body)
            if (hl == "body") blobE(Color.Transparent, Y, *body)
            // Plaque
            blobE(GUARD, Color(0xFFD8D3C4), gx(-14f), gy(-5f), gx(-8f), gy(-9f), gx(4f), gy(-7f), gx(10f), gy(-3f), gx(10f), gy(3f), gx(3f), gy(8f), gx(-6f), gy(10f), gx(-12f), gy(6f))
            // Micros
            for (px in listOf(-7f, -1.5f, 4f)) {
                rect(gx(px - 1f), gy(-3.3f), 2f * s, 6.6f * s, if (hl == "pickups") Y else Color(0xFF2B2B30), fill = true, r = 0.8f * s)
                for (k in 0 until 6) circle(gx(px), gy(-2.5f + k), 0.22f * s, METAL, fill = true)
            }
            // Chevalet
            rect(gx(-15.5f), gy(-3.8f), 3f * s, 7.6f * s, if (hl == "bridge") Y else METAL, fill = true, r = 0.4f * s)
            // Boutons et sélecteur
            for ((kx, ky) in listOf(-11f to 7.2f, -6f to 8.6f, -1f to 9.2f)) circle(gx(kx), gy(ky), 1.1f * s, if (hl == "knobs") Y else GUARD, fill = true)
            for ((kx, ky) in listOf(-11f to 7.2f, -6f to 8.6f, -1f to 9.2f)) circle(gx(kx), gy(ky), 1.1f * s, Color(0xFFB8B3A6), w = 0.3f * s)
            line(gx(4.5f), gy(6.8f), gx(6.5f), gy(5.2f), if (hl == "selector") Y else Color(0xFF2B2B30), 0.9f * s)
            // Prise jack
            circle(gx(-17f), gy(10.5f), 1.1f * s, if (hl == "jack") Y else METAL, fill = true)
            circle(gx(-17f), gy(10.5f), 0.5f * s, Color(0xFF2B2B30), fill = true)
            // Cordes
            for (i in 0 until 6) {
                val yy = gy(-1.6f + i * 0.64f)
                line(gx(-14f), yy, gx(56f), yy, STRING, (0.32f - i * 0.03f) * s)
                line(gx(56f), yy, gx(58.5f + (5 - i) * 1.7f), gy(-3.9f - (5 - i) * 0.25f), STRING, 0.22f * s)
            }
        }
    }

    // ------------------------------------------------------------------ Personnage de face

    /** Guitariste de face (le corps de la guitare à gauche de l'image, côté main droite du joueur). */
    fun player(seated: Boolean, strap: Boolean = false, guitarAngle: Float = -18f, shouldersUp: Boolean = false, hl: String = "") {
        val cx = 80f
        val sh = if (shouldersUp) 24.5f else 28.5f
        // Jambes
        if (seated) {
            rect(60f, 62f, 40f, 4f, DIM, fill = true, r = 1f) // assise
            line(64f, 66f, 64f, 84f, DIM, 2f); line(96f, 66f, 96f, 84f, DIM, 2f)
            limb(73f, 58f, 70f, 66f, 9f, 8.5f, PANTS)
            limb(87f, 58f, 90f, 66f, 9f, 8.5f, PANTS_D)
            limb(70f, 66f, 69f, 82f, 7f, 5.5f, PANTS)
            limb(90f, 66f, 91f, 82f, 7f, 5.5f, PANTS_D)
            oval(68f, 83f, 4.5f, 1.8f, SHOE, fill = true)
            oval(92f, 83f, 4.5f, 1.8f, SHOE, fill = true)
            if (hl == "thigh") oval(71f, 63f, 7f, 5f, Y, w = 1f)
        } else {
            limb(74f, 58f, 73f, 82f, 8.5f, 6f, PANTS)
            limb(86f, 58f, 87f, 82f, 8.5f, 6f, PANTS_D)
            oval(72f, 83f, 4.5f, 1.8f, SHOE, fill = true)
            oval(88f, 83f, 4.5f, 1.8f, SHOE, fill = true)
        }
        // Buste
        blob(SHIRT, cx - 12f, sh, cx + 12f, sh, cx + 11f, 46f, cx + 9f, 59f, cx - 9f, 59f, cx - 11f, 46f)
        limb(cx - 12f, sh + 1f, cx - 12.5f, sh + 1.5f, 5f, 5f, SHIRT)
        limb(cx + 12f, sh + 1f, cx + 12.5f, sh + 1.5f, 5f, 5f, SHIRT)
        limb(cx, sh - 4f, cx, sh, 4f, 4.5f, SKIN_D) // cou
        headFront(cx, sh - 10f, 6.5f)
        if (shouldersUp) {
            arc(cx - 14f, sh - 4f, 4f, 200f, 70f, Y, 1f); arc(cx + 14f, sh - 4f, 4f, 270f, 70f, Y, 1f)
        }
        // Sangle : de l'épaule gauche du joueur (droite de l'image) au corps de la guitare
        if (strap) {
            limb(cx + 10f, sh - 0.5f, 73f, 52f, 2.8f, 2.6f, Color(0xFF26264F))
            if (hl == "strap") line(cx + 10f, sh - 0.5f, 73f, 52f, Y, 0.8f, dash = true)
        }
        // Guitare
        val gy = if (seated) 59f else 57f
        guitar(66f, gy, guitarAngle, 0.95f)
        // Bras gauche du joueur (à droite de l'image) vers le manche
        val nx = 66f + 44f * 0.95f * cos(guitarAngle * PI.toFloat() / 180f)
        val ny = gy + 44f * 0.95f * sin(guitarAngle * PI.toFloat() / 180f)
        limb(cx + 12f, sh + 2f, cx + 17f, 48f, 5f, 4.2f, SHIRT_D)
        limb(cx + 17f, 48f, nx, ny + 2f, 4f, 3.2f, SKIN_D)
        blob(SKIN, nx - 2.5f, ny - 3.5f, nx + 2.5f, ny - 3.5f, nx + 3f, ny + 1.5f, nx - 2f, ny + 3f)
        // Bras droit du joueur (à gauche de l'image) posé sur le corps
        limb(cx - 12f, sh + 2f, cx - 17f, 44f, 5.2f, 4.5f, SHIRT)
        limb(cx - 17f, 44f, 66f, 56f, 4.2f, 3.4f, SKIN)
        blob(SKIN_L, 63f, 54f, 68f, 54f, 69f, 59f, 64f, 60f)
        if (hl == "arm") oval(cx - 16f, 47f, 6f, 8f, Y, w = 1f)
    }

    // ------------------------------------------------------------------ Mains

    /** Bout de doigt vu de dessus (rond avec ongle). */
    fun fingertip(x: Float, y: Float, r: Float = 3f, c: Color = SKIN, label: String? = null) {
        circle(x, y, r + 0.5f, SKIN_DD, fill = true)
        circle(x, y, r, c, fill = true)
        oval(x + r * 0.15f, y - r * 0.35f, r * 0.45f, r * 0.35f, NAIL, fill = true)
        if (label != null) text(label, x, y + r * 0.2f, X.Marine, r * 1.3f, bold = true)
    }

    /** Médiator (pointe vers le bas par défaut). */
    fun pick(x: Float, y: Float, size: Float, angle: Float = 0f, c: Color = PICK) {
        rotated(angle, x, y) {
            blobE(c, Color(0xFFC99A1E), x - size, y - size * 0.75f, x + size, y - size * 0.75f, x + size * 0.15f, y + size * 1.05f, x - size * 0.15f, y + size * 1.05f)
        }
    }

    /** Bout de touche vu de face : cordes horizontales (1re en haut), frettes verticales. */
    fun fretboard(x1: Float, x2: Float, top: Float, bottom: Float, frets: List<Float>, nut: Boolean = false) {
        rect(x1, top - 2f, x2 - x1, bottom - top + 4f, ROSE, fill = true, r = 1.5f)
        if (nut) rect(x1, top - 2f, 2.5f, bottom - top + 4f, GUARD, fill = true, r = 0.5f)
        for (fx in frets) rect(fx - 0.8f, top - 2f, 1.6f, bottom - top + 4f, METAL, fill = true, r = 0.4f)
        for (i in 0 until 6) {
            val y = top + i * (bottom - top) / 5f
            line(x1, y, x2, y, STRING, 0.6f + i * 0.17f)
        }
    }

    /** Main de face avec les doigts numérotés 1 (index) à 4 (auriculaire). */
    fun openHand(cx: Float, cy: Float, s: Float = 1f, numbers: Boolean = true) {
        limb(cx - 1f * s, cy + 24f * s, cx, cy + 14f * s, 12f * s, 16f * s, SKIN_D) // poignet
        val tips = listOf(
            Triple(-8.5f, -17f, 4.0f), Triple(-2.8f, -21f, 4.2f), Triple(2.8f, -20f, 4.1f), Triple(8f, -15f, 3.6f),
        )
        val bases = listOf(-8f, -2.7f, 2.7f, 8f)
        for (i in 0 until 4) {
            val (tx, ty, w) = tips[i]
            flimb(cx + bases[i] * s, cy - 1f * s, cx + tx * s, cy + ty * s, w * s, (w - 0.6f) * s, SKIN)
            oval(cx + tx * s, cy + (ty + 1.2f) * s, 1.1f * s, 1.4f * s, NAIL, fill = true)
        }
        // pouce, puis paume par-dessus la base des doigts
        flimb(cx + 9f * s, cy + 12f * s, cx + 19f * s, cy + 3f * s, 6f * s, 4.8f * s, SKIN)
        oval(cx + 19.5f * s, cy + 3.5f * s, 1.2f * s, 1.5f * s, NAIL, fill = true)
        blobE(SKIN, SKIN_DD, cx - 11.5f * s, cy - 1f * s, cx + 11f * s, cy - 2f * s, cx + 12f * s, cy + 12f * s, cx + 2f * s, cy + 19f * s, cx - 11f * s, cy + 13f * s)
        if (numbers) {
            for (i in 0 until 4) {
                val (tx, ty, _) = tips[i]
                circle(cx + tx * s, cy + (ty - 6f) * s, 3f * s, X.Orange, fill = true)
                text("${i + 1}", cx + tx * s, cy + (ty - 6f) * s, W, 4f * s, bold = true)
            }
            circle(cx + 24f * s, cy + 0f * s, 3f * s, DIM, fill = true)
            text("P", cx + 24f * s, cy + 0f * s, W, 4f * s, bold = true)
        }
    }

    // ------------------------------------------------------------------ Ampli

    fun amp(x: Float, y: Float, w: Float, h: Float, knobs: List<String> = emptyList(), values: List<Float> = emptyList(), hl: Set<Int> = emptySet()) {
        rect(x, y, w, h, AMP, fill = true, r = 2f)
        rect(x + 3f, y + h * 0.34f, w - 6f, h * 0.6f, AMP_GRILL, fill = true, r = 1.5f)
        for (k in 0 until 7) line(x + 4f, y + h * 0.38f + k * h * 0.08f, x + w - 4f, y + h * 0.38f + k * h * 0.08f, Color(0xFF6A6C82), 0.5f)
        rect(x + 2f, y + 2f, w - 4f, h * 0.27f, Color(0xFFDCD6C2), fill = true, r = 1f)
        val n = knobs.size
        for (i in 0 until n) {
            val kx = x + 8f + i * (w - 16f) / maxOf(1, n - 1).toFloat()
            val ky = y + 2f + h * 0.16f
            circle(kx, ky, 2.6f, if (i in hl) X.Orange else Color(0xFF26262C), fill = true)
            val v = values.getOrElse(i) { 0.5f }
            val a = (135f + v * 270f) * PI.toFloat() / 180f
            line(kx, ky, kx + 2.2f * cos(a), ky + 2.2f * sin(a), W, 0.6f)
            text(knobs[i], kx, y + 2f + h * 0.05f, X.Marine, 3.2f, bold = true)
        }
    }

    // ------------------------------------------------------------------ Dessins

    fun draw(i: Illus) {
        when (i) {
            Illus.GUITAR_PARTS -> {
                guitar(40f, 50f, 0f, 1.45f)
                fun lab(t: String, tx: Float, ty: Float, px: Float, py: Float) {
                    text(t, tx, ty, W, 4.6f, bold = true)
                    line(tx, ty + (if (ty < py) 2.4f else -2.4f), px, py, Y, 0.6f)
                    circle(px, py, 0.9f, Y, fill = true)
                }
                lab("Tête et mécaniques", 132f, 22f, 132f, 44f)
                lab("Manche", 90f, 14f, 90f, 47f)
                lab("Frettes (les cases sont entre elles)", 112f, 76f, 108f, 51.5f)
                lab("Micros", 40f, 12f, 40f, 46f)
                lab("Chevalet", 12f, 14f, 20f, 47f)
                lab("Volume et tonalité", 20f, 82f, 31f, 62f)
                lab("Sélecteur", 62f, 68f, 48.5f, 59f)
                lab("Prise jack", 4f + 6f, 70f, 15.5f, 65f)
            }
            Illus.PICKUP_SELECTOR -> {
                guitar(38f, 45f, 0f, 1.3f, hl = "pickups")
                text("Micro manche", 70f, 18f, W, 4.4f, bold = true)
                text("doux, rond", 70f, 23f, M, 3.8f)
                line(70f, 26f, 43.2f, 40f, Y, 0.6f)
                text("Micro chevalet", 18f, 18f, W, 4.4f, bold = true)
                text("brillant, mordant", 18f, 23f, M, 3.8f)
                line(18f, 26f, 28.9f, 40f, Y, 0.6f)
                rect(102f, 30f, 52f, 40f, X.Carte, fill = true, r = 3f)
                text("Pour la rythmique", 128f, 36f, Y, 4.2f, bold = true)
                text("Clair : micro manche", 128f, 45f, W, 3.8f)
                text("ou position du milieu", 128f, 50f, W, 3.8f)
                text("Rock saturé :", 128f, 58f, W, 3.8f)
                text("micro chevalet", 128f, 63f, W, 3.8f)
            }
            Illus.PLUG_IN -> {
                guitar(36f, 46f, -10f, 1.0f)
                amp(108f, 26f, 44f, 48f, listOf("Gain", "Vol."), listOf(0.3f, 0f), setOf(1))
                // câble
                val p = Path().apply {
                    moveTo(18.5f * u, 58f * u)
                    cubicTo(10f * u, 80f * u, 70f * u, 88f * u, 104f * u, 66f * u)
                }
                d.drawPath(p, Color(0xFF15151C), style = Stroke(1.6f * u, cap = StrokeCap.Round))
                rect(102f, 64f, 7f, 3f, METAL, fill = true, r = 0.6f)
                text("1. Volume de l'ampli à zéro", 80f, 10f, Y, 4.4f, bold = true)
                text("2. Branche le câble jack, allume", 80f, 16f, W, 4f)
                text("3. Monte le volume doucement", 80f, 22f, W, 4f)
            }
            Illus.AMP_KNOBS -> {
                amp(20f, 12f, 120f, 66f, listOf("Gain", "Basses", "Médiums", "Aigus", "Volume"), listOf(0.35f, 0.5f, 0.55f, 0.5f, 0.3f), setOf(0, 4))
                text("Gain = la saturation", 46f, 85f, Y, 4.2f, bold = true)
                text("Volume = le niveau sonore", 114f, 85f, Y, 4.2f, bold = true)
            }
            Illus.CLEAN_CRUNCH -> {
                val titles = listOf("Clair", "Crunch", "Saturé")
                val gains = listOf(0.4f, 2.5f, 9f)
                for (k in 0 until 3) {
                    val x0 = 8f + k * 50f
                    rect(x0, 20f, 44f, 44f, X.Carte, fill = true, r = 3f)
                    text(titles[k], x0 + 22f, 14f, W, 5f, bold = true)
                    line(x0 + 3f, 42f, x0 + 41f, 42f, DIM, 0.5f)
                    val p = Path()
                    for (j in 0..80) {
                        val t = j / 80f
                        val raw = sin(t * 4f * PI.toFloat() * 2f)
                        val g = gains[k]
                        val v = kotlin.math.tanh(raw * g) / kotlin.math.tanh(g)
                        val px = x0 + 3f + t * 38f
                        val py = 42f - v * 14f
                        if (j == 0) p.moveTo(px * u, py * u) else p.lineTo(px * u, py * u)
                    }
                    d.drawPath(p, if (k == 0) X.Bleu else if (k == 1) Y else X.Orange, style = Stroke(1.2f * u))
                    text(listOf("gain bas", "gain moyen", "gain haut").get(k), x0 + 22f, 70f, M, 3.8f)
                }
                text("Plus il y a de gain, plus le son est compressé et « carré »", 80f, 83f, W, 3.8f)
            }
            Illus.SEATED -> {
                player(seated = true, hl = "thigh")
                text("Corps de la guitare", 30f, 14f, W, 4f, bold = true)
                text("sur la cuisse droite", 30f, 19f, M, 3.8f)
                line(30f, 22f, 64f, 58f, Y, 0.6f)
                text("Dos droit,", 136f, 12f, W, 4f, bold = true)
                text("pieds à plat", 136f, 17f, M, 3.8f)
                check(146f, 70f)
            }
            Illus.STANDING_STRAP -> {
                player(seated = false, strap = true, hl = "strap")
                text("Sangle réglée pour que", 128f, 14f, W, 4f, bold = true)
                text("la guitare soit à la même", 128f, 19f, M, 3.8f)
                text("hauteur qu'assis", 128f, 24f, M, 3.8f)
                line(120f, 27f, 89f, 32f, Y, 0.6f)
            }
            Illus.NECK_ANGLE -> {
                line(46f, 58f, 150f, 58f, DIM, 0.8f, dash = true)
                guitar(46f, 58f, -15f, 1.05f)
                arc(46f, 58f, 62f, -15f, 15f, Y, 1.2f)
                text("10 à 20°", 124f, 52f, Y, 4.4f, bold = true)
                text("La tête un peu plus haute que le corps :", 80f, 10f, W, 4.2f, bold = true)
                text("le poignet gauche reste droit", 80f, 16f, M, 4f)
            }
            Illus.RELAX_SHOULDERS -> {
                d.translate(-40f * u, 0f) { d.scale(0.8f, o(80f, 84f)) { player(seated = true, shouldersUp = true) } }
                d.translate(40f * u, 0f) { d.scale(0.8f, o(80f, 84f)) { player(seated = true) } }
                cross(40f, 12f)
                check(120f, 12f)
                text("Épaules montées, crispé", 40f, 4f, M, 3.6f)
                text("Épaules basses et souples", 120f, 4f, M, 3.6f)
            }
            Illus.FINGER_NUMBERS -> {
                openHand(50f, 50f, 1.25f)
                text("Main gauche (celle du manche)", 120f, 22f, Y, 4.2f, bold = true)
                text("1 index", 120f, 34f, W, 4.4f, bold = true)
                text("2 majeur", 120f, 41f, W, 4.4f, bold = true)
                text("3 annulaire", 120f, 48f, W, 4.4f, bold = true)
                text("4 auriculaire", 120f, 55f, W, 4.4f, bold = true)
                text("Le pouce (P) reste", 120f, 67f, M, 4f)
                text("derrière le manche", 120f, 72f, M, 4f)
            }
            Illus.THUMB_BEHIND -> {
                // Coupe du manche vue depuis la tête : touche en haut (côté public), dos du manche en bas (côté guitariste)
                blobE(WOOD, WOOD_D, 56f, 40f, 104f, 40f, 104f, 46f, 98f, 62f, 80f, 68f, 62f, 62f, 56f, 46f)
                rect(55f, 34f, 50f, 6f, ROSE, fill = true, r = 1f)
                for (k in 0 until 6) circle(59f + k * 8.4f, 33f, 1.5f - k * 0.12f, STRING, fill = true)
                // base de la main sous le manche, côté cordes aiguës
                blobE(SKIN, SKIN_DD, 92f, 72f, 112f, 70f, 124f, 56f, 126f, 44f, 118f, 42f, 110f, 60f, 96f, 66f)
                // pouce : de la base de la main jusqu'au milieu du dos du manche
                flimb(100f, 72f, 80f, 71.5f, 7f, 6f, SKIN)
                oval(78f, 71f, 2.2f, 2.4f, NAIL, fill = true)
                // doigt qui passe par-dessus et appuie du bout
                flimb(122f, 46f, 120f, 26f, 6.2f, 5.6f, SKIN)
                flimb(120f, 26f, 104f, 19f, 5.6f, 5.2f, SKIN)
                flimb(104f, 19f, 92.6f, 30f, 5.2f, 4.6f, SKIN)
                circle(92.6f, 31.5f, 4.5f, Y, w = 0.8f)
                text("Pouce derrière,", 28f, 66f, W, 4.4f, bold = true)
                text("au milieu du manche", 28f, 72f, M, 4f)
                line(44f, 70f, 74f, 71f, Y, 0.6f)
                text("Doigts arrondis :", 140f, 12f, W, 4.4f, bold = true)
                text("ils appuient du bout", 140f, 18f, M, 4f)
                text("Paume décollée du manche", 120f, 84f, M, 3.8f)
                text("Le manche vu en coupe", 30f, 12f, M, 3.6f)
            }
            Illus.FINGERTIPS -> {
                for (k in 0 until 2) {
                    val x0 = 8f + k * 78f
                    rect(x0, 46f, 66f, 6f, ROSE, fill = true, r = 1f)
                    for (sx in 0 until 3) circle(x0 + 16f + sx * 17f, 45f, 1.2f, STRING, fill = true)
                    if (k == 0) {
                        flimb(x0 + 2f, 12f, x0 + 16f, 20f, 7f, 6.2f, SKIN)
                        flimb(x0 + 16f, 20f, x0 + 30f, 30f, 6.2f, 5.6f, SKIN)
                        flimb(x0 + 30f, 30f, x0 + 33f, 42f, 5.6f, 5f, SKIN)
                        check(x0 + 56f, 20f)
                        text("Le bout du doigt, bien vertical", x0 + 33f, 64f, W, 4f, bold = true)
                        text("la corde voisine sonne", x0 + 33f, 70f, M, 3.8f)
                    } else {
                        flimb(x0 + 2f, 26f, x0 + 18f, 36f, 7f, 6.2f, SKIN)
                        flimb(x0 + 18f, 36f, x0 + 34f, 41f, 6.2f, 5.6f, SKIN)
                        flimb(x0 + 34f, 41f, x0 + 50f, 42f, 5.6f, 5f, SKIN)
                        circle(x0 + 50f, 45f, 3.5f, R, w = 0.8f)
                        cross(x0 + 56f, 20f)
                        text("Doigt à plat", x0 + 33f, 64f, W, 4f, bold = true)
                        text("il étouffe la corde voisine", x0 + 33f, 70f, M, 3.8f)
                    }
                }
            }
            Illus.NEAR_FRET -> {
                fretboard(10f, 150f, 22f, 52f, listOf(40f, 82f, 120f))
                fingertip(76f, 34f, 4f)
                check(76f, 64f)
                text("Juste derrière la frette", 48f, 76f, W, 4.2f, bold = true)
                text("son net, peu d'effort", 48f, 82f, M, 3.8f)
                fingertip(100f, 46f, 4f, SKIN_L)
                cross(100f, 64f)
                text("Au milieu de la case", 124f, 76f, W, 4.2f, bold = true)
                text("ça frise", 124f, 82f, M, 3.8f)
                text("sillet ←", 24f, 14f, M, 3.6f)
                text("→ corps", 136f, 14f, M, 3.6f)
            }
            Illus.FINGER_PAIN -> {
                limb(20f, 50f, 100f, 50f, 22f, 20f, SKIN)
                oval(104f, 50f, 14f, 10f, SKIN, fill = true)
                oval(110f, 44f, 6f, 3.5f, NAIL, fill = true)
                for (k in 0 until 3) line(112f + k * 3f, 54f, 116f + k * 2f, 59f, SKIN_DD, 0.8f)
                circle(115f, 56f, 7f, Y, w = 1f)
                text("Le bout des doigts chauffe ?", 80f, 14f, W, 4.4f, bold = true)
                text("C'est normal les 2 ou 3 premières semaines :", 80f, 20f, M, 3.8f)
                text("la peau durcit (corne). Fais des pauses.", 80f, 26f, M, 3.8f)
                text("Douleur vive au poignet ? Arrête-toi.", 80f, 82f, OT, 4f, bold = true)
            }
            Illus.PICK_GRIP -> {
                // Main droite vue du côté du pouce
                blobE(SKIN, SKIN_DD, 14f, 30f, 50f, 22f, 64f, 34f, 60f, 58f, 30f, 70f, 12f, 56f)
                // index plié en crochet
                flimb(58f, 32f, 82f, 34f, 11f, 9.5f, SKIN)
                flimb(82f, 34f, 92f, 52f, 9.5f, 8.5f, SKIN)
                flimb(92f, 52f, 80f, 62f, 8.5f, 7.5f, SKIN)
                // autres doigts repliés dessous
                flimb(56f, 46f, 70f, 56f, 9f, 8f, SKIN_D)
                flimb(52f, 58f, 64f, 66f, 8.5f, 7.5f, SKIN_D)
                // médiator posé sur le côté de la dernière phalange, pointe vers les cordes
                pick(84f, 64f, 7f, -8f)
                // pouce à plat par-dessus le médiator
                flimb(44f, 48f, 86f, 58f, 10f, 8.5f, SKIN_L)
                oval(86.5f, 57f, 2.8f, 2.2f, NAIL, fill = true)
                line(60f, 84f, 120f, 84f, STRING, 0.8f)
                text("Pouce à plat", 124f, 40f, W, 4.4f, bold = true)
                line(116f, 43f, 88f, 55f, Y, 0.6f)
                text("Index plié, médiator", 112f, 14f, W, 4.4f, bold = true)
                text("posé sur son côté", 112f, 20f, M, 4f)
                line(106f, 23f, 92f, 44f, Y, 0.6f)
                text("Pointe : 2 à 3 mm", 128f, 70f, W, 4.2f, bold = true)
                line(116f, 72f, 88f, 76f, Y, 0.6f)
            }
            Illus.PICK_TIP -> {
                for (k in 0 until 2) {
                    val x0 = 40f + k * 80f
                    val tip = if (k == 0) 4f else 13f
                    pick(x0, 40f + tip * 0.6f, 9f, 0f)
                    rect(x0 - 12f, 26f, 24f, 18f, SKIN_L, fill = true, r = 6f)
                    line(x0 - 20f, 66f, x0 + 20f, 66f, STRING, 0.9f)
                    if (k == 0) check(x0 + 22f, 22f) else cross(x0 + 22f, 22f)
                    text(if (k == 0) "Petite pointe : précis" else "Trop de médiator : il accroche", x0, 78f, W, 4f, bold = true)
                }
            }
            Illus.STRUM_WRIST -> {
                // bord du corps de la guitare et cordes
                blobE(BODY, BODY_D, 10f, 60f, 70f, 52f, 140f, 56f, 150f, 90f, 10f, 90f)
                for (k in 0 until 6) line(70f, 70f + k * 2.6f, 150f, 70f + k * 2.6f, STRING, 0.7f)
                // avant-bras posé sur le bord
                limb(10f, 30f, 66f, 50f, 13f, 10f, SKIN)
                // main qui pivote au poignet
                blob(SKIN_L, 64f, 44f, 78f, 46f, 82f, 60f, 70f, 64f, 62f, 56f)
                pick(78f, 68f, 4f, -15f)
                arc(66f, 50f, 30f, 20f, 45f, Y, 1.4f, arrowEnd = true)
                arc(66f, 50f, 38f, 65f, -45f, X.Bleu, 1.4f, arrowEnd = true)
                text("bas", 100f, 76f, Y, 3.8f, bold = true)
                text("haut", 108f, 52f, X.Bleu, 3.8f, bold = true)
                text("Le geste vient du poignet", 112f, 18f, W, 4.4f, bold = true)
                text("et de l'avant-bras qui tourne,", 112f, 24f, M, 4f)
                text("comme pour secouer de l'eau", 112f, 30f, M, 4f)
                text("Avant-bras posé sur le bord", 34f, 20f, M, 3.8f)
            }
            Illus.STRUM_UP_LIGHT -> {
                val names = listOf("6 Mi grave", "5 La", "4 Ré", "3 Sol", "2 Si", "1 Mi aigu")
                for (k in 0 until 6) {
                    val y = 20f + k * 9f
                    line(40f, y, 150f, y, STRING, 1.9f - k * 0.25f)
                    text(names[k], 22f, y, M, 3.6f)
                }
                arrow(70f, 14f, 70f, 70f, X.Orange, 2.2f)
                text("↓ vers le bas :", 70f, 78f, W, 3.8f, bold = true)
                text("toutes les cordes", 70f, 84f, M, 3.6f)
                arrow(120f, 70f, 120f, 40f, X.Bleu, 2.2f)
                text("↑ vers le haut :", 120f, 78f, W, 3.8f, bold = true)
                text("3 ou 4 aiguës, léger", 120f, 84f, M, 3.6f)
                text("Vu d'en haut, comme quand tu regardes ta guitare", 92f, 8f, M, 3.4f)
            }
            Illus.STRUM_PENDULUM -> {
                val counts = listOf("1", "et", "2", "et", "3", "et", "4", "et")
                val play = listOf(true, false, true, true, false, true, true, true)
                for (k in 0 until 8) {
                    val x = 18f + k * 18f
                    val down = k % 2 == 0
                    val c = if (play[k]) (if (down) X.Orange else X.Bleu) else DIM
                    if (down) arrow(x, 24f, x, 56f, c, if (play[k]) 2f else 1.2f) else arrow(x, 56f, x, 24f, c, if (play[k]) 2f else 1.2f)
                    text(counts[k], x, 66f, if (k % 2 == 0) W else M, 4.6f, bold = k % 2 == 0)
                }
                text("La main fait toujours l'aller-retour, même quand elle ne touche pas les cordes (gris)", 80f, 80f, W, 3.6f)
                text("Rythmique pop : bas · bas-haut · haut-bas-haut", 80f, 12f, Y, 4.2f, bold = true)
            }
            Illus.STRING_NUMBERS -> {
                rect(16f, 14f, 86f, 64f, ROSE, fill = true, r = 2f)
                rect(16f, 14f, 3f, 64f, GUARD, fill = true)
                val names = listOf("6 · Mi grave (la plus grosse)", "5 · La", "4 · Ré", "3 · Sol", "2 · Si", "1 · Mi aigu (la plus fine)")
                for (k in 0 until 6) {
                    val y = 20f + k * 10.6f
                    line(16f, y, 102f, y, STRING, 2.1f - k * 0.3f)
                    text(names[k], 106f, y, W, 3.8f, bold = k == 0 || k == 5, align = 1)
                }
                text("Vu d'en haut : la grosse corde est la plus proche de ton visage", 80f, 86f, Y, 3.6f, bold = true)
            }
            Illus.DIAGRAM_READ -> {
                // Diagramme de Mi mineur dessiné en grand
                val left = 56f; val top = 22f; val sx = 9f; val sy = 11f
                for (f in 0..4) line(left, top + f * sy, left + 5 * sx, top + f * sy, M, if (f == 0) 2.4f else 0.8f)
                for (s in 0 until 6) line(left + s * sx, top, left + s * sx, top + 4 * sy, M, 1.2f - s * 0.1f)
                fun dot(s: Int, f: Int, n: Int) {
                    circle(left + s * sx, top + (f - 0.5f) * sy, 3.4f, X.Orange, fill = true)
                    text("$n", left + s * sx, top + (f - 0.5f) * sy, W, 4f, bold = true)
                }
                dot(1, 2, 2); dot(2, 2, 3)
                for (s in listOf(0, 3, 4, 5)) circle(left + s * sx, top - 4f, 1.8f, W, w = 0.8f)
                text("Em", left + 2.5f * sx, 10f, Y, 6f, bold = true)
                text("6e corde", left - 2f, top + 4 * sy + 6f, M, 3.6f)
                text("(Mi grave)", left - 2f, top + 4 * sy + 10.5f, M, 3.4f)
                text("1re corde", left + 5 * sx + 2f, top + 4 * sy + 6f, M, 3.6f)
                text("(Mi aigu)", left + 5 * sx + 2f, top + 4 * sy + 10.5f, M, 3.4f)
                text("Sillet", 30f, top, W, 3.8f, bold = true); line(37f, top, left - 2f, top, Y, 0.5f)
                text("Case 2", 30f, top + 1.5f * sy, W, 3.8f, bold = true); line(37f, top + 1.5f * sy, left - 2f, top + 1.5f * sy, Y, 0.5f)
                text("o = corde à vide", 130f, 24f, W, 3.8f)
                text("x = ne pas jouer", 130f, 32f, W, 3.8f)
                text("2 = majeur, 3 = annulaire", 130f, 40f, W, 3.8f)
                text("Le manche est vu debout,", 130f, 54f, M, 3.6f)
                text("face à toi", 130f, 59f, M, 3.6f)
            }
            Illus.STRUM_ARROWS -> {
                val marks = listOf("↓", "", "↓", "↑", "", "↑", "↓", "↑")
                val counts = listOf("1", "et", "2", "et", "3", "et", "4", "et")
                for (k in 0 until 8) {
                    val x = 18f + k * 18f
                    rect(x - 7f, 22f, 14f, 30f, if (marks[k].isEmpty()) DIM else X.Carte, fill = true, r = 2f)
                    if (marks[k].isNotEmpty()) text(marks[k], x, 37f, if (marks[k] == "↓") X.Orange else X.Bleu, 13f, bold = true)
                    text(counts[k], x, 60f, if (k % 2 == 0) W else M, 4.6f, bold = k % 2 == 0)
                }
                text("↓ = vers le bas (vers le sol)   ↑ = vers le haut", 80f, 76f, W, 4f, bold = true)
                text("case vide = la main passe sans toucher", 80f, 83f, M, 3.6f)
            }
            Illus.PALM_MUTE -> {
                // vue de côté : corps, chevalet, cordes
                rect(10f, 58f, 140f, 10f, BODY, fill = true, r = 2f)
                rect(104f, 50f, 10f, 8f, METAL, fill = true, r = 1f)
                line(10f, 50f, 114f, 50f, STRING, 1f)
                // tranche de la main posée sur les cordes, contre le chevalet
                blobE(SKIN, SKIN_DD, 70f, 22f, 112f, 26f, 116f, 46f, 100f, 50f, 74f, 44f)
                circle(108f, 49f, 4f, Y, w = 1f)
                pick(76f, 52f, 4f, 20f)
                text("Tranche de la main posée sur les cordes,", 80f, 10f, W, 4f, bold = true)
                text("tout contre le chevalet", 80f, 16f, M, 3.8f)
                text("Trop loin du chevalet : plus de note, juste un « tchk »", 80f, 82f, OT, 3.8f)
            }
            Illus.LEFT_MUTE -> {
                fretboard(10f, 150f, 26f, 56f, listOf(46f, 90f, 130f))
                for ((k, x) in listOf(70f, 80f, 112f).withIndex()) {
                    val y = 30f + k * 9f
                    fingertip(x, y, 3.6f, SKIN_L)
                }
                text("Les doigts restent posés mais n'appuient plus :", 80f, 10f, W, 4f, bold = true)
                text("les cordes ne sonnent plus (étouffé main gauche)", 80f, 16f, M, 3.8f)
                text("Utile pour les coupures, le reggae et le funk", 80f, 74f, W, 4f)
            }
            Illus.TUNING_PEGS -> {
                guitar(-30f, 50f, 0f, 1.6f)
                arc(116f, 35f, 8f, 200f, 260f, Y, 1.2f, arrowEnd = true)
                text("Tourne doucement en jouant la corde", 100f, 72f, W, 4.2f, bold = true)
                text("Toujours finir en remontant vers la note juste", 100f, 78f, M, 3.8f)
                text("(la corde tient mieux l'accord)", 100f, 84f, M, 3.6f)
            }
            Illus.TUNER_GAUGE -> {
                arc(80f, 70f, 46f, 200f, 140f, DIM, 4f)
                arc(80f, 70f, 46f, 262f, 16f, G, 4.4f)
                line(80f, 70f, 80f + 44f * cos(-75f * PI.toFloat() / 180f), 70f + 44f * sin(-75f * PI.toFloat() / 180f), Y, 1.4f)
                circle(80f, 70f, 2.4f, Y, fill = true)
                text("Trop bas", 36f, 62f, X.Bleu, 4.4f, bold = true)
                text("Trop haut", 124f, 62f, X.Orange, 4.4f, bold = true)
                text("Juste", 80f, 16f, G, 5f, bold = true)
                text("Sol", 80f, 50f, W, 9f, bold = true)
                text("Visée : l'aiguille au milieu, dans le vert", 80f, 84f, M, 3.8f)
            }
            Illus.PHONE_PLACEMENT -> {
                amp(16f, 30f, 40f, 46f, listOf("Gain", "Vol."), listOf(0.3f, 0.4f))
                rect(104f, 46f, 14f, 26f, Color(0xFF15151C), fill = true, r = 2f)
                rect(105.5f, 49f, 11f, 19f, X.Marine, fill = true, r = 1f)
                circle(111f, 70f, 0.8f, M, fill = true)
                for (k in 0 until 3) arc(56f, 54f, 10f + k * 8f, -30f, 60f, Y, 1f)
                arrow(62f, 80f, 100f, 80f, M, 0.8f, both = true)
                text("1 à 2 m", 81f, 85f, M, 3.8f)
                text("Le téléphone face à l'ampli,", 80f, 12f, W, 4.2f, bold = true)
                text("posé sur un meuble, rien dessus", 80f, 18f, M, 3.8f)
                text("Ampli : volume moyen", 36f, 84f, M, 3.6f)
            }
            Illus.ROUTINE -> {
                val parts = listOf(
                    listOf("Accorder", "3 min", 22f), listOf("Échauffement", "4 min", 28f),
                    listOf("Accords", "8 min", 28f), listOf("Rythmique", "10 min", 34f), listOf("Plaisir", "5 min", 28f),
                )
                val colors = listOf(X.Bleu, X.Vert, X.Orange, Y, X.OrangeTexte)
                var x = 10f
                for ((k, part) in parts.withIndex()) {
                    val w = part[2] as Float
                    rect(x, 34f, w - 1.5f, 22f, colors[k], fill = true, r = 2f)
                    text(part[1] as String, x + w / 2f, 45f, X.Marine, 4.2f, bold = true)
                    text(part[0] as String, x + w / 2f, 62f, W, 3.6f)
                    x += w
                }
                text("30 minutes par jour valent mieux que 3 heures le dimanche", 80f, 16f, W, 4.2f, bold = true)
                text("Toujours avec le métronome, lentement d'abord", 80f, 78f, M, 3.8f)
            }
        }
    }
}
