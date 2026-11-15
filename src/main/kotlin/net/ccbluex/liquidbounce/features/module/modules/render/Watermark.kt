package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.*
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import java.awt.Color

object Watermark : Module("Watermark", Category.COMBAT) { 
    // Nota: Se o seu fork tiver Category.VISUAL ou Category.CLIENT, você pode trocar COMBAT por elas depois.

    private val position by choices("Position", arrayOf("TopLeft", "TopRight", "BottomLeft", "BottomRight"), "TopLeft")
    private val style by choices("Style", arrayOf("Legacy", "Modern"), "Modern")
    private val showFps by boolean("ShowFPS", true)
    private val showPing by boolean("ShowPing", true)
    private val showTps by boolean("ShowTPS", true)
    private val scale by float("Scale", 1.0f, 0.5f..2.0f)

    private var tps = 20.0f
    private var tickCount = 0
    private var lastTime = System.currentTimeMillis()

    val onUpdate = handler<UpdateEvent> {
        tickCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTime >= 1000) {
            tps = tickCount.toFloat()
            tickCount = 0
            lastTime = currentTime
        }
    }

    val onRender2D = handler<Render2DEvent> {
        val font = Fonts.fontSemibold40
        val sr = ScaledResolution(mc) 
        
        data class TextPart(val text: String, val color: Int)
        val parts = mutableListOf<TextPart>()
        
        parts.add(TextPart("Carnage", Color(255, 0, 0).rgb))
        
        val addPart = { text: String ->
            if (parts.size > 1) parts.add(TextPart(" | ", Color(128, 128, 128).rgb))
            parts.add(TextPart(text, Color(255, 255, 255).rgb))
        }
        
        if (showFps) addPart("FPS: ${Minecraft.getDebugFPS()}")
        if (showPing) {
            val ping = mc.thePlayer?.let { player ->
                mc.netHandler?.getPlayerInfo(player.uniqueID)?.responseTime ?: -1
            } ?: -1
            addPart("Ping: ${if (ping >= 0) "${ping}ms" else "n/a"}")
        }
        if (showTps) addPart("TPS: ${"%.1f".format(tps)}")
        
        var totalWidth = 0f
        parts.forEach { totalWidth += font.getStringWidth(it.text).toFloat() }
        val boxHeight = font.FONT_HEIGHT.toFloat() + 4f
        
        val padding = 4f
        var x = 0f
        var y = 0f
        
        val scaledTotalWidth = totalWidth * scale
        val scaledBoxHeight = boxHeight * scale
        
        val screenWidth = sr.scaledWidth.toFloat()
        val screenHeight = sr.scaledHeight.toFloat()
        
        when (position) {
            "TopLeft" -> { x = padding; y = padding }
            "TopRight" -> { x = screenWidth - scaledTotalWidth - padding; y = padding }
            "BottomLeft" -> { x = padding; y = screenHeight - scaledBoxHeight - padding }
            "BottomRight" -> { x = screenWidth - scaledTotalWidth - padding; y = screenHeight - scaledBoxHeight - padding }
        }
        
        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0f)
        GlStateManager.scale(scale, scale, 1f)
        
        if (style == "Modern") {
            RenderUtils.drawRoundedRect(
                -2f, -2f, 
                totalWidth + 2f, boxHeight + 2f, 
                Color(0, 0, 0, 128).rgb, 
                2f, 
                RenderUtils.RoundedCorners.NONE 
            )
        }
        
        var currentX = 0f
        val textY = 2f
        
        for (part in parts) {
            if (style == "Legacy") {
                font.drawString(part.text, currentX + 1f, textY + 1f, Color(0, 0, 0).rgb, false)
            }
            font.drawString(part.text, currentX, textY, part.color, false)
            currentX += font.getStringWidth(part.text).toFloat()
        }
        
        GlStateManager.popMatrix()
    }
}
