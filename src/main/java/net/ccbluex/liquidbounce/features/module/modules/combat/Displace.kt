package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.boolean
import net.ccbluex.liquidbounce.config.choices
import net.ccbluex.liquidbounce.config.float
import net.ccbluex.liquidbounce.config.int
import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.extensions.rotation
import net.ccbluex.liquidbounce.utils.rotation.PostRotationExecutor
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationPriority
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.currentRotation
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.input.Keyboard

object Displace : Module("Displace", Category.COMBAT, Category.SubCategory.COMBAT_RAGE, Keyboard.KEY_NONE) {

    private val mode by choices("Mode", arrayOf("Left", "Right", "Random"), "Left")
    private val angle by float("Angle", 90.0f, 0.0f..180.0f)
    private val onlyOnKillAura by boolean("OnlyOnKillAura", true)
    private val chance by int("Chance", 100, 0..100, "%")

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

        // 1. Aplica rotação silenciosa
        val success = RotationUtils.setTargetRotation(
            rotation = Rotation(flickYaw, basePitch),
            options = rotationSettings,
            ticks = 1
        )

        if (!success) return@handler

        // 2. Cancela o ataque original do KillAura
        event.cancelEvent()

        // 3. Agenda o ataque usando o PostRotationExecutor do FDPClient
        // Isso garante que o servidor receba a rotação FALSA antes do ataque.
        PostRotationExecutor.runPostMove {
            val livePlayer = mc.thePlayer ?: return@runPostMove
            val world = mc.theWorld ?: return@runPostMove
            val liveTarget = world.getEntityByID(target.entityId) as? EntityLivingBase ?: return@runPostMove

            if (liveTarget.isEntityAlive) {
                // Re-aplica a rotação e ataca
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
