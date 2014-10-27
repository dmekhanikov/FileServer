import java.util.Timer
import java.util.TimerTask
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.Scanner
import java.io.InputStream
import java.io.FileInputStream
import java.io.IOException

private val PERIOD: Int = 1000
private val hosts = ConcurrentHashMap<Int, Host>()

public fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("Usage:\n\tjava _DefaultPackage <name> <basedir>")
        System.exit(1)
    }
    val name = args[0]
    val basedir = File(args[1])
    if (!basedir.exists()) {
        basedir.mkdirs()
    }

    val fileHelper = FileHelper(basedir)
    val udpHelper = UdpHelper()

    val announcer = Announcer(udpHelper, fileHelper, name)
    val announcerTimer = Timer()
    announcerTimer.schedule(object : TimerTask() {
        override fun run() {
            announcer.announce()
        }
    }, 0, PERIOD.toLong())

    val executor = Executors.newCachedThreadPool()

    // Announce receiver
    executor.submit {
        while (!Thread.interrupted()) {
            try {
                val host = udpHelper.receiveMessage()
                hosts[host.ip] = host
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Server
    val tcpHelper = TcpHelper()
    executor.submit {
        while (!Thread.interrupted()) {
            tcpHelper.processClient(executor, fileHelper)
        }
    }

    // Terminal (Client)
    while (true) {
        val line = readLine()
        if (line == null) break
        val cmd = line.substringBefore(' ')
        when (cmd) {
            "hosts" -> {
                for (host in hosts.values()) {
                    println(host)
                }
                System.`out`.flush()
            }
            "list" -> {
                val ip = line.substringAfter(' ')
                try {
                    val files = tcpHelper.list(ip)
                    for (file in files) {
                        println(file)
                    }
                    System.out.flush()
                } catch (e: Exception) {
                    System.err.println(e.getMessage())
                }
            }
        }
    }
    executor.shutdownNow()
    announcerTimer.cancel()
}
