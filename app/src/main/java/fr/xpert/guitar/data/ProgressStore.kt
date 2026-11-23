package fr.xpert.guitar.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SessionRecord(
    val exerciseId: String,
    val title: String,
    val atMillis: Long,
    val durationSec: Int,
    val accuracy: Int,
    val bpm: Int,
    val meanDeltaMs: Int,
    /** Accords justes en % (-1 si non jugé). */
    val chordAccuracy: Int = -1,
    /** Changements d'accords justes en une minute (exercices de changement), sinon 0. */
    val changes: Int = 0,
)

data class BestScore(val accuracy: Int, val bpm: Int, val sessions: Int, val chordAccuracy: Int = -1, val changes: Int = 0)

/** Progression et réglages, stockés localement (rien ne quitte le téléphone). */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("xpert_guitar", Context.MODE_PRIVATE)

    /** Prénom demandé au premier lancement (vide tant qu'il n'est pas saisi). */
    var firstName: String
        get() = prefs.getString("first_name", "") ?: ""
        set(v) = prefs.edit().putString("first_name", v.trim()).apply()

    /** Seuil de détection des coups de médiator au micro (échelle de OnsetDetector). */
    var micThreshold: Float
        get() = prefs.getFloat("mic_threshold", 0.03f)
        set(v) = prefs.edit().putFloat("mic_threshold", v).apply()

    /** Décalage mesuré entre le son joué et sa détection au micro (chaîne audio du téléphone). */
    var micOffsetMs: Int
        get() = prefs.getInt("mic_offset_ms", 0)
        set(v) = prefs.edit().putInt("mic_offset_ms", v).apply()

    val isMicCalibrated: Boolean get() = prefs.contains("mic_threshold")

    /** Accords validés au moins une fois au micro (identifiants de positions). */
    fun markChordMastered(id: String) {
        val set = prefs.getStringSet("chords_ok", emptySet())!!.toMutableSet()
        if (set.add(id)) prefs.edit().putStringSet("chords_ok", set).apply()
    }

    fun chordsMastered(): Set<String> = prefs.getStringSet("chords_ok", emptySet())!!.toSet()

    var metronomeBpm: Int
        get() = prefs.getInt("metronome_bpm", 80)
        set(v) = prefs.edit().putInt("metronome_bpm", v).apply()

    fun markLessonRead(id: String) {
        val set = prefs.getStringSet("lessons_read", emptySet())!!.toMutableSet()
        if (set.add(id)) prefs.edit().putStringSet("lessons_read", set).apply()
    }

    fun lessonsRead(): Set<String> = prefs.getStringSet("lessons_read", emptySet())!!.toSet()

    fun addPracticeSeconds(sec: Int) {
        if (sec <= 0) return
        prefs.edit().putInt("practice_sec", totalPracticeSeconds() + sec).apply()
    }

    fun totalPracticeSeconds(): Int = prefs.getInt("practice_sec", 0)

    fun record(r: SessionRecord) {
        val arr = JSONArray(prefs.getString("history", "[]"))
        arr.put(JSONObject().apply {
            put("id", r.exerciseId); put("title", r.title); put("at", r.atMillis)
            put("dur", r.durationSec); put("acc", r.accuracy); put("bpm", r.bpm); put("delta", r.meanDeltaMs)
            put("chords", r.chordAccuracy); put("changes", r.changes)
        })
        val trimmed = JSONArray()
        val from = maxOf(0, arr.length() - 300)
        for (i in from until arr.length()) trimmed.put(arr.get(i))
        prefs.edit().putString("history", trimmed.toString()).apply()
        addPracticeSeconds(r.durationSec)
    }

    fun history(): List<SessionRecord> {
        val arr = JSONArray(prefs.getString("history", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SessionRecord(
                o.getString("id"), o.getString("title"), o.getLong("at"), o.getInt("dur"),
                o.getInt("acc"), o.getInt("bpm"), o.optInt("delta", 0),
                o.optInt("chords", -1), o.optInt("changes", 0),
            )
        }
    }

    /** Meilleur résultat par exercice : meilleure précision, et tempo le plus haut tenu à 80 % ou plus. */
    fun bests(): Map<String, BestScore> = history().groupBy { it.exerciseId }.mapValues { (_, list) ->
        BestScore(
            accuracy = list.maxOf { it.accuracy },
            bpm = list.filter { it.accuracy >= 80 }.maxOfOrNull { it.bpm } ?: 0,
            sessions = list.size,
            chordAccuracy = list.maxOf { it.chordAccuracy },
            changes = list.maxOf { it.changes },
        )
    }
}
