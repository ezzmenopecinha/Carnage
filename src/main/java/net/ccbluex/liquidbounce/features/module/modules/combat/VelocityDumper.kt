/*
 * Carnage - VelocityDumper
 * Calibration tool: records what the client receives on a knockback (S12) and what the player
 * really does for the next ticks, so the real/expected ratio per axis can be computed offline.
 * It never modifies, delays or cancels any packet.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.*
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.carnage.Motion
import net.ccbluex.liquidbounce.utils.carnage.VelocityDumpRecord
import net.ccbluex.liquidbounce.utils.carnage.formatDumpRecord
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.network.play.client.C0FPacketConfirmTransaction
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S32PacketConfirmTransaction
import net.minecraft.util.ChatComponentText
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

object VelocityDumper : Module("VelocityDumper", Category.COMBAT, Category.SubCategory.COMBAT_RAGE) {

    private val sampleTicks by int("Ticks", 5, 1..20)
        .describe("How many client ticks of real motion to record after each knockback.")
    private val printToChat by boolean("Chat", true)
        .describe("Print each record to the chat.")
    private val saveToFile by boolean("SaveFile", true)
        .describe("Append each record to carnage-dump.txt in the game directory.")

    /** Raw S12 fields (motion * 8000) exactly as this handler saw them. */
    private class RawVelocity(val x: Int, val y: Int, val z: Int)

    /** A capture in progress. Only touched on the main thread (UpdateEvent), except the null check in onPacket. */
    private class Capture(val raw: RawVelocity, val pingMs: Int?, val onGround: Boolean) {
        val motions = ArrayList<Motion>()
    }

    // Written by the network thread (PacketEvent), consumed by the main thread (UpdateEvent).
    @Volatile private var incoming: RawVelocity? = null
    @Volatile private var capture: Capture? = null
    @Volatile private var transactionSeen = false
    @Volatile private var setbackSeen = false
    private val droppedWhileBusy = AtomicInteger(0)

    private var recordIndex = 0

    override val tag
        get() = recordIndex.toString()

    override fun onEnable() {
        recordIndex = 0
        resetState()
    }

    override fun onDisable() {
        resetState()
    }

    private fun resetState() {
        incoming = null
        capture = null
        transactionSeen = false
        setbackSeen = false
        droppedWhileBusy.set(0)
    }

    /**
     * Packet side only stores plain values. Nothing here reads world or player-list state,
     * because this handler can run off the main thread. A knockback that arrives while a
     * capture is still running is counted, not recorded, so records never overlap.
     */
    val onPacket = handler<PacketEvent> { event ->
        if (!handleEvents())
            return@handler

        val player = mc.thePlayer ?: return@handler
        val packet = event.packet

        when (event.eventType) {
            EventState.RECEIVE -> when (packet) {
                is S12PacketEntityVelocity -> {
                    if (packet.entityID != player.entityId)
                        return@handler

                    if (capture != null || incoming != null) {
                        droppedWhileBusy.incrementAndGet()
                        return@handler
                    }

                    // Flags are cleared at the S12 itself: a transaction BEFORE the S12 is not "between".
                    transactionSeen = false
                    setbackSeen = false
                    incoming = RawVelocity(packet.motionX, packet.motionY, packet.motionZ)
                }

                is S32PacketConfirmTransaction -> transactionSeen = true
                is S08PacketPlayerPosLook -> setbackSeen = true
            }

            EventState.SEND -> if (packet is C0FPacketConfirmTransaction) transactionSeen = true

            else -> {}
        }
    }

    /**
     * Main thread: starts a capture when a knockback is waiting, then appends one motion sample per tick.
     * Sample 0 is the first UpdateEvent after the S12 was seen. Depending on whether the game has
     * already applied the packet by then, sample 0 can still hold the OLD motion.
     */
    val onUpdate = handler<UpdateEvent> {
        if (!handleEvents())
            return@handler

        val player = mc.thePlayer ?: return@handler

        var active = capture
        if (active == null) {
            val pending = incoming ?: return@handler
            incoming = null
            active = Capture(pending, readPingMs(player), player.onGround)
            capture = active
        }

        active.motions.add(Motion(player.motionX, player.motionY, player.motionZ))

        if (active.motions.size >= sampleTicks) {
            capture = null
            finish(active, player)
        }
    }

    val onWorld = handler<WorldEvent> {
        resetState()
    }

    /**
     * Tab-list latency as reported by the server (NetworkPlayerInfo.responseTime). It is not a
     * measured round trip and updates rarely. -1 or a missing entry is reported as unknown (null).
     */
    private fun readPingMs(player: EntityPlayerSP): Int? {
        val info = mc.netHandler?.getPlayerInfo(player.uniqueID) ?: return null
        return info.responseTime.takeIf { it >= 0 }
    }

    private fun finish(done: Capture, player: EntityPlayerSP) {
        val record = VelocityDumpRecord(
            rawX = done.raw.x,
            rawY = done.raw.y,
            rawZ = done.raw.z,
            pingMs = done.pingMs,
            onGroundAtReceive = done.onGround,
            transactionBetween = transactionSeen,
            setbackSeen = setbackSeen,
            ticks = done.motions.toList()
        )

        recordIndex++
        val dropped = droppedWhileBusy.getAndSet(0)
        val text = buildString {
            append(formatDumpRecord(recordIndex, record))
            if (dropped > 0)
                append("  note: $dropped knockback(s) arrived during this capture and were not recorded\n")
        }

        if (printToChat) {
            text.lineSequence().filter { it.isNotBlank() }.forEach {
                player.addChatMessage(ChatComponentText("\u00a77[VelocityDumper] \u00a7f$it"))
            }
        }

        if (saveToFile) {
            try {
                File(mc.mcDataDir, "carnage-dump.txt")
                    .appendText("# epochMillis=${System.currentTimeMillis()}\n$text\n")
            } catch (e: IOException) {
                player.addChatMessage(ChatComponentText("\u00a7c[VelocityDumper] could not write carnage-dump.txt: ${e.message}"))
            }
        }
    }
}
