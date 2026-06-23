package vlensys.yuriasmr.client

import java.util.concurrent.CopyOnWriteArrayList

object Installer {
	val logs = CopyOnWriteArrayList<String>()

	@Volatile var running = false
		private set

	@Volatile var done = false
		private set

	@Volatile private var toChat = false

	fun start() {
		if (running) return
		logs.clear()
		done = false
		toChat = false
		running = true
		Thread {
			try {
				Binaries.install { emit(it) }
			} catch (e: Exception) {
				emit("install flopped: ${e.message}")
			} finally {
				done = true
				running = false
			}
		}.apply { isDaemon = true; name = "yuri-asmr-install" }.start()
	}

	fun routeToChat() {
		toChat = true
	}

	private fun emit(msg: String) {
		logs.add(msg)
		if (toChat) Chat.send(msg)
	}
}
