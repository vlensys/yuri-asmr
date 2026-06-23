package vlensys.yuriasmr.client

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import vlensys.yuriasmr.YuriAsmr
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object VersionCheck {
	private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
	private val tagRegex = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")

	fun currentVersion(): String =
		FabricLoader.getInstance().getModContainer(YuriAsmr.MOD_ID)
			.map { it.metadata.version.friendlyString }.orElse("0.0.0")

	fun run() {
		if (AsmrConfig.neverRemind) return
		Thread {
			runCatching {
				val repo = AsmrConfig.githubRepo
				val req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/$repo/releases/latest"))
					.header("User-Agent", "yuri-asmr")
					.header("Accept", "application/vnd.github+json")
					.GET().build()
				val res = http.send(req, HttpResponse.BodyHandlers.ofString())
				if (res.statusCode() != 200) return@runCatching
				val latestVersion = tagRegex.find(res.body())?.groupValues?.get(1)?.removePrefix("v") ?: return@runCatching
				val currentVersion = currentVersion().removePrefix("v")
				if (behind(currentVersion, latestVersion)) announce(currentVersion, latestVersion)
			}
		}.apply { isDaemon = true; name = "yuri-asmr-versioncheck" }.start()
	}

	private fun behind(current: String, latest: String): Boolean {
		val c = parse(current)
		val l = parse(latest)
		for (i in 0 until maxOf(c.size, l.size)) {
			val a = c.getOrElse(i) { 0 }
			val b = l.getOrElse(i) { 0 }
			if (a != b) return a < b
		}
		return false
	}

	private fun parse(v: String): List<Int> =
		v.trim().split('.', '-', '+').mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

	private fun announce(current: String, latest: String) {
		val repo = AsmrConfig.githubRepo
		val mc = Minecraft.getInstance()
		mc.execute {
			val msg = Component.literal("yuri asmr: heads up, ur behind!! u got $current, latest is $latest. ")
				.withStyle(ChatFormatting.YELLOW)
				.append(
					Component.literal("[open github]").withStyle(
						Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
							.withClickEvent(ClickEvent.OpenUrl(URI.create("https://github.com/$repo/releases/latest")))
					)
				)
				.append(Component.literal("  "))
				.append(
					Component.literal("[never remind me again!!]").withStyle(
						Style.EMPTY.withColor(ChatFormatting.GRAY).withUnderlined(true)
							.withClickEvent(ClickEvent.RunCommand("/yuriasmr dontremind"))
					)
				)
			mc.player?.sendSystemMessage(msg)
		}
	}
}
