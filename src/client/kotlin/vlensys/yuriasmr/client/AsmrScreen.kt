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
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs

class AsmrScreen : Screen(Component.literal("yuri asmr")) {
	private lateinit var customBox: EditBox
	private lateinit var pauseBtn: Button
	private lateinit var ytDlpBtn: Button
	private lateinit var statusWidget: StringWidget

	override fun init() {
		Background.ensureLoaded()
		val cx = width / 2
		var y = 10

		addRenderableWidget(StringWidget(cx - 100, y, 200, 12, Component.literal("yuri asmr"), font))
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
		addRenderableWidget(customBox)
		btn("fetch", cx + 44, y, 56) {
			val v = customBox.value.trim()
			if (v.isNotEmpty()) {
				if (isUrl(v)) Player.playUrl(v) else Player.playSearch(v)
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

		ytDlpBtn = Button.builder(ytDlpLabel(), Button.OnPress {
			if (Binaries.ytDlpInstalled()) uninstall() else openSetup()
		}).bounds(cx - 100, y, 200, 20).build()
		addRenderableWidget(ytDlpBtn)
		y += 22

		addRenderableWidget(Button.builder(Component.literal("set bg"), Button.OnPress { pickBackground() }).bounds(cx - 100, y, 95, 20).build())
		addRenderableWidget(Button.builder(Component.literal("reset bg"), Button.OnPress { resetBackground() }).bounds(cx + 5, y, 95, 20).build())

		statusWidget = StringWidget(cx - 150, height - 14, 300, 12, Component.literal(Status.line), font)
		addRenderableWidget(statusWidget)
	}

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick)
		if (Background.loaded) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, Background.id, 0, 0, 0f, 0f, width, height, Background.texW, Background.texH, Background.texW, Background.texH)
		}
	}

	override fun tick() {
		pauseBtn.visible = Player.listening
		pauseBtn.message = Component.literal(if (Player.paused) "resume" else "pause")
		ytDlpBtn.message = ytDlpLabel()
		statusWidget.message = Component.literal(Status.line)
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

	private fun isUrl(s: String) = s.startsWith("http://") || s.startsWith("https://")

	private fun qualityLabel() = Component.literal("quality: ${AsmrConfig.quality.label}")

	private fun ytDlpLabel() = Component.literal(if (Binaries.ytDlpInstalled()) "uninstall yt-dlp" else "install yt-dlp")

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

	private fun pickBackground() {
		Thread {
			val path = runCatching { openPngDialog() }.getOrNull()
			if (!path.isNullOrBlank()) {
				val mc = Minecraft.getInstance()
				mc.execute {
					Background.set(path)
					Status.line = if (Background.loaded) "background set" else "couldnt load that png :("
					Chat.send(Status.line)
				}
			}
		}.apply { isDaemon = true; name = "yuri-asmr-bgpick" }.start()
	}

	private fun openPngDialog(): String? {
		MemoryStack.stackPush().use { stack ->
			val filters = stack.mallocPointer(1)
			filters.put(stack.UTF8("*.png"))
			filters.flip()
			return TinyFileDialogs.tinyfd_openFileDialog("pick a background png", "", filters, "png images", false)
		}
	}

	private fun resetBackground() {
		Background.clear()
		Status.line = "background reset"
		Chat.send(Status.line)
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
