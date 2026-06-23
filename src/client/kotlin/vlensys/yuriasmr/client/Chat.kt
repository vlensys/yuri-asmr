package vlensys.yuriasmr.client

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object Chat {
	fun send(msg: String) {
		val mc = Minecraft.getInstance()
		mc.execute { mc.player?.sendSystemMessage(Component.literal("yuri asmr: $msg").withStyle(ChatFormatting.YELLOW)) }
	}
}
