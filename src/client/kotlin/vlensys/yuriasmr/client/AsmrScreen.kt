package vlensys.yuriasmr.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class AsmrScreen : Screen(Component.literal("yuri asmr")) {
	private lateinit var customBox: EditBox

	override fun init() {
		val cx = width / 2
		var y = height / 6

		addRenderableWidget(StringWidget(cx - 100, y, 200, 12, Component.literal("yuri asmr"), font))
		y += 20

		addRenderableWidget(
			Button.builder(qualityLabel(), Button.OnPress {
				AsmrConfig.quality = AsmrConfig.quality.next()
				AsmrConfig.save()
				it.message = qualityLabel()
			}).bounds(cx - 100, y, 200, 20).build()
		)
		y += 24

		addRenderableWidget(VolumeSlider(cx - 100, y, 200, 20))
		y += 28

		btn("mommy asmr", cx - 100, y, 200) { tryPlay { Player.playSearch("mommy asmr") } }
		y += 24
		btn("girlfriend asmr", cx - 100, y, 200) { tryPlay { Player.playSearch("girlfriend asmr") } }
		y += 24
		btn("general asmr duos", cx - 100, y, 200) { tryPlay { Player.playSearch("asmr duo") } }
		y += 28

		customBox = EditBox(font, cx - 100, y, 140, 20, Component.literal("custom"))
		customBox.setHint(Component.literal("youtube link or search..."))
		customBox.setMaxLength(512)
		addRenderableWidget(customBox)
		btn("fetch", cx + 44, y, 56) {
			val v = customBox.value.trim()
			if (v.isNotEmpty()) tryPlay { if (isUrl(v)) Player.playUrl(v) else Player.playSearch(v) }
		}
		y += 28

		btn("stop", cx - 100, y, 200) { Player.stop() }
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

	private fun tryPlay(action: () -> Unit) {
		if (Binaries.installed()) action()
		else Minecraft.getInstance().setScreen(InstallPromptScreen(this, action))
	}

	private fun isUrl(s: String) = s.startsWith("http://") || s.startsWith("https://")

	private fun qualityLabel() = Component.literal("quality: ${AsmrConfig.quality.label}")

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
