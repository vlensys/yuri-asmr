package vlensys.yuriasmr.client

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class SetupScreen(
	private val onComplete: () -> Unit,
	private val onSkip: () -> Unit,
	private val autoStart: Boolean = false
) : Screen(Component.literal("yuri asmr setup")) {

	private var installing = false
	private var completeAdded = false
	private val logWidgets = mutableListOf<StringWidget>()

	override fun init() {
		if (autoStart && !installing) {
			installing = true
			Installer.start()
		}
		logWidgets.clear()
		completeAdded = false
		val cx = width / 2
		val cy = height / 2
		if (!installing) {
			addRenderableWidget(StringWidget(cx - 150, cy - 52, 300, 12, Component.literal("yuri asmr needs yt-dlp to work. grab it?"), font))
			addRenderableWidget(StringWidget(cx - 150, cy - 38, 300, 12, Component.literal("downloads yt-dlp + ffmpeg from their official github"), font))
			addRenderableWidget(StringWidget(cx - 150, cy - 27, 300, 12, Component.literal("releases over https. they run as programs on your pc."), font))
			addRenderableWidget(Button.builder(Component.literal("install"), Button.OnPress { startInstall() }).bounds(cx - 100, cy - 12, 200, 20).build())
			addRenderableWidget(Button.builder(Component.literal("no thanks"), Button.OnPress { onSkip() }).bounds(cx - 100, cy + 12, 200, 20).build())
		} else {
			val top = cy - 60
			for (i in 0 until 7) {
				val sw = StringWidget(cx - 150, top + i * 12, 300, 12, Component.literal(""), font)
				logWidgets.add(sw)
				addRenderableWidget(sw)
			}
			addRenderableWidget(Button.builder(Component.literal("exit"), Button.OnPress {
				Installer.routeToChat()
				onComplete()
			}).bounds(cx - 100, cy + 40, 95, 20).build())
		}
	}

	private fun startInstall() {
		installing = true
		Installer.start()
		rebuildWidgets()
	}

	override fun tick() {
		if (!installing) return
		val lines = Installer.logs.toList().takeLast(logWidgets.size)
		for (i in logWidgets.indices) {
			logWidgets[i].message = Component.literal(lines.getOrElse(i) { "" })
		}
		if (Installer.done && !completeAdded) {
			completeAdded = true
			val cx = width / 2
			val cy = height / 2
			addRenderableWidget(Button.builder(Component.literal("complete"), Button.OnPress { onComplete() }).bounds(cx + 5, cy + 40, 95, 20).build())
		}
	}

	override fun shouldCloseOnEsc(): Boolean = !installing
}
