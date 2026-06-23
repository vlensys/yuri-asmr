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

	val playing: Boolean get() = thread?.isAlive == true

	fun playSearch(query: String) = start("ytsearch15:$query", search = true, label = query)

	fun playUrl(url: String) = start(url, search = false, label = url)

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
		Status.line = "stopped"
	}

	private fun start(input: String, search: Boolean, label: String) {
		stop()
		stopFlag = false
		paused = false
		Status.line = "searching..."
		val quality = AsmrConfig.quality
		val t = Thread { run(input, search, quality, label) }
		t.isDaemon = true
		t.name = "yuri-asmr-player"
		thread = t
		t.start()
	}

	private fun run(input: String, search: Boolean, quality: Quality, label: String) {
		var bytesPlayed = 0L
		var failedToStart = false
		try {
			val ytArgs = mutableListOf(
				Binaries.ytDlp(), "-q", "--no-warnings", "--no-progress", "-f", quality.format
			)
			if (search) ytArgs.addAll(listOf("--playlist-items", Random.nextInt(1, 16).toString()))
			ytArgs.addAll(listOf("-o", "-", input))
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
					if (bytesPlayed == 0L) {
						Status.line = "playing: $label"
						Chat.send("now playing: $label")
					}
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
						Status.line = "yt-dlp/ffmpeg not working"
						Chat.send("cant run yt-dlp/ffmpeg, are they installed right?")
					}
					bytesPlayed == 0L -> {
						Status.line = "found nothing :("
						Chat.send("couldnt find anything for that :(")
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
