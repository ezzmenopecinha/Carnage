package net.ccbluex.liquidbounce.utils.carnage

import java.util.Locale
import kotlin.math.abs

/** S12PacketEntityVelocity sends motion * 8000 as a short: client motion = raw / 8000.0. */
const val VELOCITY_RAW_SCALE = 8000.0

/** Below this |expected| (blocks/tick) a real/expected ratio is noise, so it is reported as n/a. */
const val MIN_EXPECTED_FOR_RATIO = 0.02

data class Motion(val x: Double, val y: Double, val z: Double)

data class VelocityDumpRecord(
    val rawX: Int,
    val rawY: Int,
    val rawZ: Int,
    /** null = player info unavailable. Never 0 as a stand-in. */
    val pingMs: Int?,
    val onGroundAtReceive: Boolean,
    /** A confirm-transaction packet (C0F/S32) was seen between the S12 and the last sampled tick. */
    val transactionBetween: Boolean,
    /** An S08 setback arrived during the window: the server corrected the player. */
    val setbackSeen: Boolean,
    /** Real thePlayer motion, one entry per client tick after the S12 (index 0 = first tick). */
    val ticks: List<Motion>
)

fun expectedMotion(r: VelocityDumpRecord): Motion = Motion(
    r.rawX / VELOCITY_RAW_SCALE,
    r.rawY / VELOCITY_RAW_SCALE,
    r.rawZ / VELOCITY_RAW_SCALE
)

fun ratioOrNull(real: Double, expected: Double): Double? =
    if (!real.isFinite() || !expected.isFinite() || abs(expected) < MIN_EXPECTED_FOR_RATIO) null
    else real / expected

private fun fmt(v: Double?): String =
    if (v == null) "n/a" else String.format(Locale.ROOT, "%.4f", v)

fun formatDumpRecord(index: Int, r: VelocityDumpRecord): String {
    val e = expectedMotion(r)
    val sb = StringBuilder()
    sb.append("#").append(index)
        .append(" ping=").append(r.pingMs?.toString() ?: "n/a").append("ms")
        .append(" ground=").append(r.onGroundAtReceive)
        .append(" txBetween=").append(r.transactionBetween)
        .append(" setback=").append(r.setbackSeen)
        .append(" raw=").append(r.rawX).append('/').append(r.rawY).append('/').append(r.rawZ)
        .append('\n')
    sb.append("  expected x=").append(fmt(e.x)).append(" y=").append(fmt(e.y)).append(" z=").append(fmt(e.z)).append('\n')
    r.ticks.forEachIndexed { i, m ->
        sb.append("  tick").append(i)
            .append(" real x=").append(fmt(m.x)).append(" y=").append(fmt(m.y)).append(" z=").append(fmt(m.z))
            .append(" | ratio x=").append(fmt(ratioOrNull(m.x, e.x)))
            .append(" y=").append(fmt(ratioOrNull(m.y, e.y)))
            .append(" z=").append(fmt(ratioOrNull(m.z, e.z)))
            .append('\n')
    }
    return sb.toString()
}
