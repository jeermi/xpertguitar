package fr.xpert.guitar.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** FFT complexe radix-2 en place (taille puissance de 2), tables précalculées. Pur Kotlin. */
class Fft(val n: Int) {
    private val cosT = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { -sin(2 * PI * it / n) }
    private val rev = IntArray(n)

    init {
        require(n >= 2 && n and (n - 1) == 0) { "taille non puissance de 2 : $n" }
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) rev[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun transform(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val l = j + half
                    val tr = re[l] * cosT[k] - im[l] * sinT[k]
                    val ti = re[l] * sinT[k] + im[l] * cosT[k]
                    re[l] = re[j] - tr; im[l] = im[j] - ti
                    re[j] += tr; im[j] += ti
                    k += step
                }
                i += size
            }
            size *= 2
        }
    }
}
