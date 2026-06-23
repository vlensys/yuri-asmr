package vlensys.yuriasmr.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import vlensys.yuriasmr.YuriAsmr

class AsmrScreen : Screen(Component.literal("yuri asmr")) {
	private companion object {
		val BG: Identifier = YuriAsmr.id("textures/gui/background.png")
		const val BG_W = 3840
		const val BG_H = 2400
	}

	private lateinit var customBox: EditBox
	private lateinit var pauseBtn: Button
	private lateinit var installBtn: Button
	private lateinit var updateBtn: Button
	private lateinit var uninstallBtn: Button
	private lateinit var nowPlayingWidget: StringWidget
	private lateinit var statusWidget: StringWidget

	override fun init() {
		val cx = width / 2
		var y = 8

		addRenderableWidget(StringWidget(cx - 100, y, 200, 12, Component.literal("yuri asmr"), font))
		y += 13

		nowPlayingWidget = StringWidget(cx - 150, y, 300, 12, Component.literal(""), font)
		addRenderableWidget(nowPlayingWidget)
		y += 16

		addRenderableWidget(
			Button.builder(qualityLabel(), Button.OnPress {
				AsmrConfig.quality = AsmrConfig.quality.next()
				AsmrConfig.save()
				it.message = qualityLabel()
			}).bounds(cx - 100, y, 200, 20).build()
		)
		y += 22

		addRenderableWidget(VolumeSlider(cx - 100, y, 200, 20))
		y += 22

		btn("mommy asmr", cx - 100, y, 200) { Player.playSearch("mommy asmr") }
		y += 22
		btn("girlfriend asmr", cx - 100, y, 200) { Player.playSearch("girlfriend asmr") }
		y += 22
		btn("asmr", cx - 100, y, 200) { Player.playSearch("asmr") }
		y += 22

		customBox = EditBox(font, cx - 100, y, 140, 20, Component.literal("custom"))
		customBox.setHint(Component.literal("youtube link or search..."))
		customBox.setMaxLength(512)
		if (AsmrConfig.lastSearch.isNotEmpty()) customBox.value = AsmrConfig.lastSearch
		addRenderableWidget(customBox)
		btn("fetch", cx + 44, y, 56) {
			val v = customBox.value.trim()
			if (v.isNotEmpty()) {
				AsmrConfig.lastSearch = v
				AsmrConfig.save()
				if (isUrl(v)) Player.playUrl(if (v.startsWith("http")) v else "https://$v") else Player.playSearch(v)
			}
		}
		y += 22

		btn("stop", cx - 100, y, 200) { Player.stop() }
		y += 22

		pauseBtn = Button.builder(Component.literal("pause"), Button.OnPress {
			if (Player.paused) Player.resume() else Player.pause()
		}).bounds(cx - 100, y, 200, 20).build()
		pauseBtn.visible = Player.listening
		addRenderableWidget(pauseBtn)
		y += 22

		installBtn = Button.builder(Component.literal("install yt-dlp"), Button.OnPress { openSetup() }).bounds(cx - 100, y, 200, 20).build()
		addRenderableWidget(installBtn)
		updateBtn = Button.builder(Component.literal("update yt-dlp"), Button.OnPress { update() }).bounds(cx - 100, y, 95, 20).build()
		addRenderableWidget(updateBtn)
		uninstallBtn = Button.builder(Component.literal("uninstall yt-dlp"), Button.OnPress { uninstall() }).bounds(cx + 5, y, 95, 20).build()
		addRenderableWidget(uninstallBtn)
		refreshYtDlpButtons()

		addRenderableWidget(Button.builder(Component.literal("yuri"), Button.OnPress {
			AsmrConfig.backgroundOn = !AsmrConfig.backgroundOn
			AsmrConfig.save()
		}).bounds(4, height - 24, 50, 20).build())

		statusWidget = StringWidget(cx - 150, height - 14, 300, 12, Component.literal(Status.line), font)
		addRenderableWidget(statusWidget)
	}

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick)
		if (AsmrConfig.backgroundOn) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, BG, 0, 0, 0f, 0f, width, height, BG_W, BG_H, BG_W, BG_H)
		}
	}

	override fun tick() {
		pauseBtn.visible = Player.listening
		pauseBtn.message = Component.literal(if (Player.paused) "resume" else "pause")
		refreshYtDlpButtons()
		nowPlayingWidget.message = Component.literal(nowPlayingLabel())
		statusWidget.message = Component.literal(Status.line)
	}

	private fun refreshYtDlpButtons() {
		val on = Binaries.ytDlpInstalled()
		installBtn.visible = !on
		updateBtn.visible = on
		uninstallBtn.visible = on
	}

	private fun nowPlayingLabel(): String {
		if (!Player.playing || Player.title.isEmpty()) return ""
		val t = Player.title
		return "♪ " + if (t.length > 56) t.take(53) + "..." else t
	}

	override fun onClose() {
		AsmrConfig.save()
		super.onClose()
	}

	private fun btn(text: String, x: Int, y: Int, w: Int, action: () -> Unit) {
		addRenderableWidget(
			Button.builder(Component.literal(text), Button.OnPress { action() }).bounds(x, y, w, 20).build()
		)
	}

	private fun isUrl(s: String) = s.startsWith("http://") || s.startsWith("https://") ||
		Regex("""^[\w-]+(\.[\w-]+)+(/.*)?$""").matches(s)

	private fun qualityLabel() = Component.literal("quality: ${AsmrConfig.quality.label}")

	private fun openSetup() {
		val mc = Minecraft.getInstance()
		mc.setScreen(SetupScreen(
			onComplete = { mc.setScreen(AsmrScreen()) },
			onSkip = { mc.setScreen(AsmrScreen()) },
			autoStart = true
		))
	}

	private fun uninstall() {
		Player.stop()
		Status.line = "uninstalling yt-dlp..."
		Thread {
			val ok = Binaries.uninstallYtDlp()
			Status.line = if (ok) "yt-dlp uninstalled" else "couldnt remove yt-dlp, is it in use?"
			Chat.send(Status.line)
		}.apply { isDaemon = true; name = "yuri-asmr-uninstall" }.start()
	}

	private fun update() {
		Player.stop()
		Status.line = "updating yt-dlp..."
		Thread {
			val ok = Binaries.updateYtDlp()
			Status.line = if (ok) "yt-dlp updated" else "couldnt update yt-dlp"
			Chat.send(Status.line)
		}.apply { isDaemon = true; name = "yuri-asmr-update" }.start()
	}

	private inner class VolumeSlider(x: Int, y: Int, w: Int, h: Int) :
		AbstractSliderButton(x, y, w, h, Component.empty(), AsmrConfig.volume.toDouble()) {
		init {
			updateMessage()
		}

		override fun updateMessage() {
			message = Component.literal("volume: ${(value * 100).toInt()}%")
		}

		override fun applyValue() {
			AsmrConfig.volume = value.toFloat()
			Player.setVolume(AsmrConfig.volume)
		}
	}
}
