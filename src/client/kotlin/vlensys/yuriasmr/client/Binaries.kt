package vlensys.yuriasmr.client

import java.io.BufferedInputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object Binaries {
	private val os = System.getProperty("os.name").lowercase()
	private val windows = os.contains("win")
	private val mac = os.contains("mac") || os.contains("darwin")

	private const val YTDLP_WIN = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
	private const val YTDLP_MAC = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos"
	private const val YTDLP_NIX = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
	private const val FFMPEG_WIN = "https://github.com/BtbN/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-win64-gpl.zip"

	private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

	private val binDir: Path get() = AsmrConfig.dir.resolve("bin")
	private val ytDlpFile: Path get() = binDir.resolve(if (windows) "yt-dlp.exe" else "yt-dlp")
	private val ffmpegFile: Path get() = binDir.resolve(if (windows) "ffmpeg.exe" else "ffmpeg")

	fun ytDlp(): String =
		AsmrConfig.ytDlpPath.ifBlank { if (Files.exists(ytDlpFile)) ytDlpFile.toString() else "yt-dlp" }

	fun ffmpeg(): String =
		AsmrConfig.ffmpegPath.ifBlank { if (Files.exists(ffmpegFile)) ffmpegFile.toString() else "ffmpeg" }

	fun installed(): Boolean =
		(AsmrConfig.ytDlpPath.isNotBlank() || Files.exists(ytDlpFile)) &&
			(AsmrConfig.ffmpegPath.isNotBlank() || Files.exists(ffmpegFile))

	fun install(log: (String) -> Unit): Boolean {
		Files.createDirectories(binDir)
		log("grabbing yt-dlp...")
		download(ytDlpUrl(), ytDlpFile)
		if (!windows) makeExecutable(ytDlpFile)
		if (windows) {
			log("grabbing ffmpeg... this ones chunky, hang tight")
			installFfmpegWindows()
		} else if (onPath("ffmpeg")) {
			AsmrConfig.ffmpegPath = "ffmpeg"
		} else {
			log("couldnt auto grab ffmpeg on ur os, install it yourself (apt/brew install ffmpeg)")
		}
		AsmrConfig.save()
		val ok = installed()
		log(if (ok) "all set :3" else "ffmpeg didnt make it, mod wont play till u sort that out")
		return ok
	}

	private fun ytDlpUrl(): String = when {
		windows -> YTDLP_WIN
		mac -> YTDLP_MAC
		else -> YTDLP_NIX
	}

	private fun installFfmpegWindows() {
		val tmp = Files.createTempFile("ffmpeg", ".zip")
		try {
			download(FFMPEG_WIN, tmp)
			ZipInputStream(BufferedInputStream(Files.newInputStream(tmp))).use { zis ->
				var entry = zis.nextEntry
				while (entry != null) {
					if (!entry.isDirectory && entry.name.replace('\\', '/').endsWith("bin/ffmpeg.exe")) {
						Files.copy(zis, ffmpegFile, StandardCopyOption.REPLACE_EXISTING)
						return
					}
					entry = zis.nextEntry
				}
			}
		} finally {
			Files.deleteIfExists(tmp)
		}
	}

	private fun download(url: String, target: Path) {
		val req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "yuri-asmr").GET().build()
		val res = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
		if (res.statusCode() != 200) throw IOException("http ${res.statusCode()} for $url")
		res.body().use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
	}

	private fun makeExecutable(p: Path) {
		runCatching {
			val perms = Files.getPosixFilePermissions(p).toMutableSet()
			perms.add(PosixFilePermission.OWNER_EXECUTE)
			perms.add(PosixFilePermission.GROUP_EXECUTE)
			perms.add(PosixFilePermission.OTHERS_EXECUTE)
			Files.setPosixFilePermissions(p, perms)
		}
	}

	private fun onPath(cmd: String): Boolean = runCatching {
		val p = ProcessBuilder(cmd, "-version")
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start()
		p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0
	}.getOrDefault(false)
}
