/*
 * Carnage Client
 * Based on FDPClient
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.*
import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.rotation.*
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.currentRotation
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.input.Keyboard

object Displace : Module("Displace", Category.COMBAT, Category.SubCategory.COMBAT_RAGE, Keyboard.KEY_NONE) {

    private val mode by choices("Mode", arrayOf("Left", "Right", "Random"), "Left")
        .describe("Direção do deslocamento do knockback.")

    private val angle by float("Angle", 90.0f, 0.0f..180.0f)
        .describe("Ângulo do deslocamento.")

    private val onlyOnKillAura by boolean("OnlyOnKillAura", true)
        .describe("Só aplica quando o KillAura atacar.")

    private val chance by int("Chance", 100, 0..100, "%")
        .describe("Chance de aplicar o deslocamento.")

    private val rotationSettings = RotationSettings(this)
        .withoutKeepRotation()
        .withRequestPriority(RotationPriority.CRITICAL)

    val onAttack = handler<AttackEvent>(priority = -1) { event ->
        if (!state) return@handler
        if (onlyOnKillAura && !KillAura.state) return@handler
        if (Math.random() * 100 >= chance) return@handler

        val target = event.targetEntity
        if (target !is EntityLivingBase) return@handler
        if (!target.isEntityAlive) return@handler

        val player = mc.thePlayer ?: return@handler
        val baseYaw = player.rotationYaw
        val basePitch = player.rotationPitch

        val offset = when (mode) {
            "Left" -> -angle
            "Right" -> angle
            "Random" -> if (Math.random() > 0.5) -angle else angle
            else -> 0f
        }

        val flickYaw = baseYaw + offset

        val success = RotationUtils.setTargetRotation(
            rotation = Rotation(flickYaw, basePitch),
            options = rotationSettings,
            ticks = 1
        )

        if (!success) return@handler

        event.cancelEvent()

        PostRotationExecutor.runPostMove {
            val livePlayer = mc.thePlayer ?: return@runPostMove
            val world = mc.theWorld ?: return@runPostMove
            val liveTarget = world.getEntityByID(target.entityId) as? EntityLivingBase ?: return@runPostMove

            if (liveTarget.isEntityAlive) {
                RotationUtils.setTargetRotation(
                    rotation = Rotation(flickYaw, basePitch),
                    options = rotationSettings,
                    ticks = 1
                )
                livePlayer.attackEntityWithModifiedSprint(liveTarget, false) {}
            }
        }
    }
}
