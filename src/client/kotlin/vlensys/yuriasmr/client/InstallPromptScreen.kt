package vlensys.yuriasmr.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class InstallPromptScreen(private val parent: Screen?, private val onReady: () -> Unit) :
	Screen(Component.literal("yuri asmr setup")) {

	override fun init() {
		val cx = width / 2
		val cy = height / 2
		addRenderableWidget(StringWidget(cx - 150, cy - 44, 300, 12, Component.literal("yuri asmr needs yt-dlp + ffmpeg to actually work."), font))
		addRenderableWidget(StringWidget(cx - 150, cy - 28, 300, 12, Component.literal("can i auto grab em? goes into the mod folder, nowhere else."), font))
		addRenderableWidget(
			Button.builder(Component.literal("yeah, install em"), Button.OnPress { startInstall() })
				.bounds(cx - 100, cy, 200, 20).build()
		)
		addRenderableWidget(
			Button.builder(Component.literal("nah"), Button.OnPress { Minecraft.getInstance().setScreen(UselessScreen(parent)) })
				.bounds(cx - 100, cy + 24, 200, 20).build()
		)
	}

	private fun startInstall() {
		Chat.send("installing, give it a sec...")
		Thread {
			val ok = try {
				Binaries.install { Chat.send(it) }
			} catch (e: Exception) {
				Chat.send("install flopped: ${e.message}")
				false
			}
			Minecraft.getInstance().execute { if (ok) onReady() }
		}.apply { isDaemon = true; name = "yuri-asmr-install" }.start()
		Minecraft.getInstance().setScreen(parent)
	}
}

class UselessScreen(private val parent: Screen?) : Screen(Component.literal("oh well")) {
	override fun init() {
		val cx = width / 2
		val cy = height / 2
		addRenderableWidget(StringWidget(cx - 150, cy - 20, 300, 12, Component.literal("the mod is kinda useless for u then. continue?"), font))
		addRenderableWidget(
			Button.builder(Component.literal("continue"), Button.OnPress { Minecraft.getInstance().setScreen(parent) })
				.bounds(cx - 100, cy + 8, 200, 20).build()
		)
	}
}
