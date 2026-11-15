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
import net.ccbluex.liquidbounce.utils.timing.TickedActions.nextTick
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

    // Evita loop infinito se o ataque disparar o AttackEvent de novo
    private var ignoreNextAttack = false

    val onAttack = handler<AttackEvent>(priority = -1) { event ->
        if (!state) return@handler
        
        // Se fomos nós que causamos esse evento, ignora.
        if (ignoreNextAttack) {
            ignoreNextAttack = false
            return@handler
        }

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

        // 1. Aplica a rotação silenciosa
        val success = RotationUtils.setTargetRotation(
            rotation = Rotation(flickYaw, basePitch),
            options = rotationSettings,
            ticks = 1
        )

        if (!success) return@handler

        // 2. Cancela o ataque original do KillAura
        event.cancelEvent()

        // 3. Agenda o ataque para o PRÓXIMO TICK (Thread Principal)
        val targetId = target.entityId

        nextTick {
            val livePlayer = mc.thePlayer ?: return@nextTick
            val world = mc.theWorld ?: return@nextTick
            val liveTarget = world.getEntityByID(targetId) as? EntityLivingBase ?: return@nextTick

            if (!liveTarget.isEntityAlive) return@nextTick

            // Re-aplica a rotação no tick do ataque
            RotationUtils.setTargetRotation(
                rotation = Rotation(flickYaw, basePitch),
                options = rotationSettings,
                ticks = 1
            )

            // Marca para ignorar o evento que vamos disparar
            ignoreNextAttack = true
            
            // Executa o ataque
            livePlayer.attackEntityWithModifiedSprint(liveTarget, false) {}
        }
    }
}
