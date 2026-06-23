package vlensys.yuriasmr.client

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

enum class Quality(val label: String, val format: String) {
	FAST("fast", "worstaudio/worst"),
	NORMAL("normal", "bestaudio[abr<=128]/bestaudio/best"),
	QUALITY("quality", "bestaudio/best");

	fun next(): Quality = entries[(ordinal + 1) % entries.size]
}

object AsmrConfig {
	val dir: Path = FabricLoader.getInstance().configDir.resolve("yuri-asmr")
	private val file: Path = dir.resolve("config.properties")

	var quality: Quality = Quality.NORMAL
	var volume: Float = 1.0f
	var githubRepo: String = "vlensys/yuri-asmr"
	var neverRemind: Boolean = false
	var firstLaunchDone: Boolean = false
	var ytDlpPath: String = ""
	var ffmpegPath: String = ""
	var backgroundPath: String = ""

	fun load() {
		if (!Files.exists(file)) return
		val p = Properties()
		Files.newInputStream(file).use { p.load(it) }
		quality = runCatching { Quality.valueOf(p.getProperty("quality", quality.name)) }.getOrDefault(quality)
		volume = p.getProperty("volume", volume.toString()).toFloatOrNull() ?: volume
		githubRepo = p.getProperty("githubRepo", githubRepo)
		neverRemind = p.getProperty("neverRemind", "false").toBoolean()
		firstLaunchDone = p.getProperty("firstLaunchDone", "false").toBoolean()
		ytDlpPath = p.getProperty("ytDlpPath", "")
		ffmpegPath = p.getProperty("ffmpegPath", "")
		backgroundPath = p.getProperty("backgroundPath", "")
	}

	fun save() {
		Files.createDirectories(dir)
		val p = Properties()
		p.setProperty("quality", quality.name)
		p.setProperty("volume", volume.toString())
		p.setProperty("githubRepo", githubRepo)
		p.setProperty("neverRemind", neverRemind.toString())
		p.setProperty("firstLaunchDone", firstLaunchDone.toString())
		p.setProperty("ytDlpPath", ytDlpPath)
		p.setProperty("ffmpegPath", ffmpegPath)
		p.setProperty("backgroundPath", backgroundPath)
		Files.newOutputStream(file).use { p.store(it, null) }
	}
}
