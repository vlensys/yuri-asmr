package vlensys.yuriasmr.client

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10
import kotlin.random.Random

object Status {
	@Volatile var line: String = ""
}

object Player {
	private val format = AudioFormat(44100f, 16, 2, true, false)

	@Volatile private var thread: Thread? = null
	@Volatile private var ytProc: Process? = null
	@Volatile private var ffProc: Process? = null
	@Volatile private var line: SourceDataLine? = null
	@Volatile private var stopFlag = false

	@Volatile var paused = false
		private set

	@Volatile var title = ""
		private set

	val playing: Boolean get() = thread?.isAlive == true

	val listening: Boolean get() = thread?.isAlive == true && line != null

	fun playSearch(query: String) = start("ytsearch15:$query", search = true)

	fun playUrl(url: String) = start(url, search = false)

	fun setVolume(v: Float) {
		line?.let { applyGain(it, v) }
	}

	fun pause() {
		line?.let { runCatching { it.stop() } }
		paused = true
		Status.line = "paused"
	}

	fun resume() {
		line?.let { runCatching { it.start() } }
		paused = false
		Status.line = "resumed"
	}

	fun stop() {
		stopFlag = true
		paused = false
		destroyProcs()
		line?.let { runCatching { it.stop(); it.flush(); it.close() } }
		line = null
		thread?.let { runCatching { it.interrupt() } }
		thread = null
		title = ""
		Status.line = "stopped"
	}

	private fun start(input: String, search: Boolean) {
		stop()
		stopFlag = false
		paused = false
		title = ""
		Status.line = "searching..."
		val t = Thread { run(input, search) }
		t.isDaemon = true
		t.name = "yuri-asmr-player"
		thread = t
		t.start()
	}

	private fun run(input: String, search: Boolean) {
		val item = if (search) Random.nextInt(1, 16) else 0
		val resolved = try {
			resolve(input, item)
		} catch (e: Exception) {
			if (!stopFlag) {
				Status.line = "yt-dlp not working"
				Chat.send("cant run yt-dlp, is it installed?")
			}
			return
		}
		if (stopFlag) return
		if (resolved == null) {
			if (!stopFlag) {
				Status.line = "found nothing :("
				Chat.send("couldnt find anything for that :(")
			}
			return
		}
		title = resolved.first
		Status.line = "playing: ${resolved.first}"
		Chat.send("now playing: ${resolved.first}")
		stream(resolved.second)
	}

	private fun resolve(input: String, item: Int): Pair<String, String>? {
		val args = mutableListOf(Binaries.ytDlp(), "-q", "--no-warnings")
		if (item > 0) args.addAll(listOf("--playlist-items", item.toString()))
		args.addAll(listOf("--print", "%(title)s", "--print", "%(webpage_url)s", input))
		val proc = ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
		val lines = proc.inputStream.bufferedReader().use { it.readLines() }
		proc.waitFor()
		val vidTitle = lines.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
		val url = lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
		return vidTitle to url
	}

	private fun stream(url: String) {
		var bytesPlayed = 0L
		var failedToStart = false
		try {
			val ytArgs = listOf(
				Binaries.ytDlp(), "-q", "--no-warnings", "--no-progress",
				"-f", AsmrConfig.quality.format, "-o", "-", url
			)
			val ffArgs = listOf(
				Binaries.ffmpeg(), "-hide_banner", "-loglevel", "error",
				"-i", "pipe:0", "-vn", "-f", "s16le", "-ar", "44100", "-ac", "2", "pipe:1"
			)
			val procs = try {
				ProcessBuilder.startPipeline(
					listOf(
						ProcessBuilder(ytArgs).redirectError(ProcessBuilder.Redirect.DISCARD),
						ProcessBuilder(ffArgs).redirectError(ProcessBuilder.Redirect.DISCARD)
					)
				)
			} catch (e: Exception) {
				failedToStart = true
				throw e
			}
			ytProc = procs[0]
			ffProc = procs[1]

			val out = AudioSystem.getSourceDataLine(format)
			out.open(format)
			applyGain(out, AsmrConfig.volume)
			out.start()
			line = out
			if (paused) runCatching { out.stop() }

			val src = procs[1].inputStream
			val buf = ByteArray(8192)
			while (!stopFlag) {
				val n = src.read(buf)
				if (n < 0) break
				if (n > 0) {
					out.write(buf, 0, n)
					bytesPlayed += n
				}
			}
			if (!stopFlag) out.drain()
			runCatching { out.stop(); out.close() }
		} catch (e: Exception) {
		} finally {
			destroyProcs()
			line = null
			if (!stopFlag) {
				when {
					failedToStart -> {
						Status.line = "ffmpeg not working"
						Chat.send("cant run ffmpeg, is it installed?")
					}
					bytesPlayed == 0L -> {
						Status.line = "couldnt grab that audio :("
						Chat.send("couldnt grab that audio :(")
					}
					else -> Status.line = "finished"
				}
			}
		}
	}

	private fun applyGain(l: SourceDataLine, vol: Float) {
		if (!l.isControlSupported(FloatControl.Type.MASTER_GAIN)) return
		val c = l.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
		val v = vol.coerceIn(0f, 1f)
		c.value = if (v <= 0.0001f) c.minimum
		else (20.0 * log10(v.toDouble())).toFloat().coerceIn(c.minimum, c.maximum)
	}

	private fun destroyProcs() {
		ffProc?.destroyForcibly()
		ytProc?.destroyForcibly()
		ffProc = null
		ytProc = null
	}
}
