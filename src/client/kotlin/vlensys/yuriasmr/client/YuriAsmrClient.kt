package vlensys.yuriasmr.client

import com.mojang.brigadier.Command
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import vlensys.yuriasmr.YuriAsmr

object YuriAsmrClient : ClientModInitializer {
	private lateinit var openKey: KeyMapping

	@Volatile private var checkedThisSession = false
	@Volatile private var setupSkipped = false

	override fun onInitializeClient() {
		AsmrConfig.load()

		val category = KeyMapping.Category.register(YuriAsmr.id("main"))
		openKey = KeyMappingHelper.registerKeyMapping(KeyMapping("key.yuri-asmr.open", GLFW.GLFW_KEY_O, category))

		ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, _ ->
			dispatcher.register(
				ClientCommands.literal("yuriasmr")
					.executes { open(); Command.SINGLE_SUCCESS }
					.then(ClientCommands.literal("dontremind").executes { dontRemind(); Command.SINGLE_SUCCESS })
			)
		})

		ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick {
			while (openKey.consumeClick()) open()
		})

		ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, _, _ ->
			if (!checkedThisSession) {
				checkedThisSession = true
				if (!AsmrConfig.firstLaunchDone) {
					AsmrConfig.firstLaunchDone = true
					AsmrConfig.save()
					Chat.send("thnx for installing yuri asmr, do /yuriasmr to open the GUI!")
				}
				VersionCheck.run()
			}
		})
	}

	private fun open() {
		val mc = Minecraft.getInstance()
		mc.execute {
			if (Binaries.ytDlpInstalled() || setupSkipped) {
				mc.setScreen(AsmrScreen())
			} else {
				mc.setScreen(SetupScreen(
					onComplete = { mc.setScreen(AsmrScreen()) },
					onSkip = { setupSkipped = true; mc.setScreen(AsmrScreen()) }
				))
			}
		}
	}

	private fun dontRemind() {
		AsmrConfig.neverRemind = true
		AsmrConfig.save()
		Chat.send("ok i wont bug u about updates again!")
	}
}
