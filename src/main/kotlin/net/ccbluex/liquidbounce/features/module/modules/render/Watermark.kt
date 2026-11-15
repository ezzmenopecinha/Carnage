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

/**
 * Watermark HUD para o client Carnage.
 * Exibe informações do sistema (FPS, Ping, TPS) nos cantos da tela.
 */
object Watermark : Module("Watermark", Category.Render) { // Corrigido para PascalCase

    // --- Configurações via Delegates (Padrão NextGen) ---
    private val position by choices("Position", arrayOf("TopLeft", "TopRight", "BottomLeft", "BottomRight"), "TopLeft")
    private val style by choices("Style", arrayOf("Legacy", "Modern"), "Modern")
    private val showFps by boolean("ShowFPS", true)
    private val showPing by boolean("ShowPing", true)
    private val showTps by boolean("ShowTPS", true)
    private val scale by float("Scale", 1.0f, 0.5f..2.0f)

    // --- Lógica interna de TPS (Contador de ticks por segundo) ---
    private var tps = 20.0f
    private var tickCount = 0
    private var lastTime = System.currentTimeMillis()

    val onUpdate = handler<UpdateEvent> {
        tickCount++
        val currentTime = System.currentTimeMillis()
        // A cada 1 segundo, calcula o TPS e reseta o contador
        if (currentTime - lastTime >= 1000) {
            tps = tickCount.toFloat()
            tickCount = 0
            lastTime = currentTime
        }
    }

    val onRender2D = handler<Render2DEvent> {
        val font = Fonts.fontSemibold40
        
        // Correção: Instanciar ScaledResolution manualmente já que o evento não expõe
        val sr = ScaledResolution(mc) 
        
        data class TextPart(val text: String, val color: Int)
        val parts = mutableListOf<TextPart>()
        
        // 1. "Carnage" sempre em vermelho sangue
        parts.add(TextPart("Carnage", Color(255, 0, 0).rgb))
        
        // Função auxiliar para adicionar separadores e valores em branco
        val addPart = { text: String ->
            if (parts.size > 1) parts.add(TextPart(" | ", Color(128, 128, 128).rgb)) // Separador cinza
            parts.add(TextPart(text, Color(255, 255, 255).rgb)) // Texto branco
        }
        
        // 2. Adiciona métricas conforme config
        if (showFps) addPart("FPS: ${Minecraft.getDebugFPS()}")
        if (showPing) {
            // Safe call para evitar NPE se estiver em menu sem player instanciado
            val ping = mc.thePlayer?.let { player ->
                mc.netHandler?.getPlayerInfo(player.uniqueID)?.responseTime ?: -1
            } ?: -1
            addPart("Ping: ${if (ping >= 0) "${ping}ms" else "n/a"}")
        }
        if (showTps) addPart("TPS: ${"%.1f".format(tps)}")
        
        // --- Cálculo de Dimensões (Conversão explícita para Float para evitar ambiguidade) ---
        var totalWidth = 0f
        parts.forEach { totalWidth += font.getStringWidth(it.text).toFloat() }
        val boxHeight = font.FONT_HEIGHT.toFloat() + 4f // Altura da fonte + padding vertical
        
        val padding = 4f
        var x = 0f
        var y = 0f
        
        val scaledTotalWidth = totalWidth * scale
        val scaledBoxHeight = boxHeight * scale
        
        val screenWidth = sr.scaledWidth.toFloat()
        val screenHeight = sr.scaledHeight.toFloat()
        
        // --- Cálculo de Posição (X, Y) baseado no canto escolhido ---
        when (position) {
            "TopLeft" -> { x = padding; y = padding }
            "TopRight" -> { x = screenWidth - scaledTotalWidth - padding; y = padding }
            "BottomLeft" -> { x = padding; y = screenHeight - scaledBoxHeight - padding }
            "BottomRight" -> { x = screenWidth - scaledTotalWidth - padding; y = screenHeight - scaledBoxHeight - padding }
        }
        
        // --- Renderização ---
        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, 0f)
        GlStateManager.scale(scale, scale, 1f) // Aplica a escala configurada
        
        // Estilo Modern: Fundo arredondado semi-transparente
        if (style == "Modern") {
            RenderUtils.drawRoundedRect(
                -2f, -2f, 
                totalWidth + 2f, boxHeight + 2f, 
                Color(0, 0, 0, 128).rgb, // Preto com 50% de alpha
                2f, 
                RenderUtils.RoundedCorners.NONE // Enum correto do seu fork
            )
        }
        
        var currentX = 0f
        val textY = 2f // Padding superior para centralizar verticalmente
        
        // Desenha cada parte do texto
        for (part in parts) {
            if (style == "Legacy") {
                // Sombra preta simulada (deslocada 1px para baixo e direita)
                font.drawString(part.text, currentX + 1f, textY + 1f, Color(0, 0, 0).rgb, false)
            }
            // Texto principal com a cor da parte (shadow = false para evitar duplicação)
            font.drawString(part.text, currentX, textY, part.color, false)
            currentX += font.getStringWidth(part.text).toFloat()
        }
        
        GlStateManager.popMatrix()
    }
}
