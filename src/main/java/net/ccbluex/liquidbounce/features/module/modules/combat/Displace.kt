/*
 * Carnage Client
 * Based on FDPClient
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.boolean
import net.ccbluex.liquidbounce.config.choices
import net.ccbluex.liquidbounce.config.float
import net.ccbluex.liquidbounce.config.int
import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.extensions.rotation
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.nextFloat
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationPriority
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.input.Keyboard
import kotlin.math.abs

object Displace : Module("Displace", Category.COMBAT, Category.SubCategory.COMBAT_RAGE, Keyboard.KEY_NONE) {

    // ─── Opções ───
    private val mode by choices("Mode", arrayOf("Left", "Right", "Random"), "Left")
        .describe("Direção do deslocamento do knockback.")

    private val angle by float("Angle", 45.0f, 0.0f..180.0f)
        .describe("Ângulo do deslocamento.")

    private val cooldown by int("Cooldown", 500, 0..2000, "ms")
        .describe("Tempo mínimo entre deslocamentos.")

    private val playersOnly by boolean("PlayersOnly", true)
        .describe("Só aplica em jogadores.")

    private val onlyOnKillAura by boolean("OnlyOnKillAura", true)
        .describe("Só aplica quando o KillAura atacar.")

    // ─── Estado ───
    private var lastAttack = 0L
    private var pendingSide = 0 // 0 = esquerda, 1 = direita

    // Usamos o sistema de rotação do FDPClient
    private val rotationSettings = RotationSettings(this)
        .withoutKeepRotation()
        .withRequestPriority(RotationPriority.HIGH)

    val onAttack = handler<AttackEvent>(priority = -1) { event ->
        if (!state) return@handler
        if (onlyOnKillAura && !KillAura.state) return@handler

        val target = event.targetEntity
        if (target !is EntityLivingBase) return@handler
        if (!target.isEntityAlive) return@handler
        if (playersOnly && target !is net.minecraft.entity.player.EntityPlayer) return@handler

        // Cooldown
        if (System.currentTimeMillis() - lastAttack < cooldown) return@handler

        val player = mc.thePlayer ?: return@handler
        val baseYaw = player.rotationYaw
        val basePitch = player.rotationPitch

        // Define o lado
        val offset = when (mode) {
            "Left" -> -angle
            "Right" -> angle
            "Random" -> if (Math.random() > 0.5) -angle else angle
            else -> 0f
        }

        // Aplica a rotação silenciosa
        val flickYaw = baseYaw + offset

        RotationUtils.setTargetRotation(
            rotation = Rotation(flickYaw, basePitch),
            options = rotationSettings,
            ticks = 1
        )

        lastAttack = System.currentTimeMillis()
    }
}
