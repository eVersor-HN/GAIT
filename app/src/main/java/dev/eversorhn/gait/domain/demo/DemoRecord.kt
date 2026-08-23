package dev.eversorhn.gait.domain.demo

import android.content.Context

/**
 * What the demo loader put into an enrolment, so exactly that can be taken back out again —
 * the sample sessions by id, and the profile numbers as they stood before.
 *
 * Kept per enrolment. Real sessions are never touched: removal deletes only these ids.
 */
object DemoRecord {

    private const val PREFS = "gait_demo_record"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Before(
        val generation: Int,
        val fidelity: Float,
        val vacationDaysUsed: Int,
        val vacationYear: Int,
    )

    fun save(context: Context, profileId: Long, sessionIds: List<Long>, before: Before) {
        prefs(context).edit()
            .putString("ids_$profileId", sessionIds.joinToString(","))
            .putString("before_$profileId", "${before.generation},${before.fidelity},${before.vacationDaysUsed},${before.vacationYear}")
            .apply()
    }

    fun has(context: Context, profileId: Long): Boolean =
        !prefs(context).getString("ids_$profileId", null).isNullOrBlank()

    fun sessionIds(context: Context, profileId: Long): List<Long> =
        prefs(context).getString("ids_$profileId", null)
            ?.split(",")?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()

    fun before(context: Context, profileId: Long): Before? {
        val raw = prefs(context).getString("before_$profileId", null) ?: return null
        val p = raw.split(",")
        if (p.size < 4) return null
        return Before(
            generation = p[0].toIntOrNull() ?: 1,
            fidelity = p[1].toFloatOrNull() ?: 0.5f,
            vacationDaysUsed = p[2].toIntOrNull() ?: 0,
            vacationYear = p[3].toIntOrNull() ?: 0,
        )
    }

    fun clear(context: Context, profileId: Long) {
        prefs(context).edit().remove("ids_$profileId").remove("before_$profileId").apply()
    }
}
