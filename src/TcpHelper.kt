import java.io.OutputStream
import java.net.Socket
import java.net.ProtocolException
import java.io.IOException
import java.util.ArrayList
import java.io.InputStream
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.net.InetSocketAddress

public class TcpHelper {
    private val port = 8888
    private val serverSocket = ServerSocket(port, 0, InetAddress.getLocalHost())

    public fun processClient(executor: ExecutorService, fileHelper: FileHelper) {
        val socket = serverSocket.accept()
        executor.submit {
            try {
                val inputStream = socket.getInputStream()
                val outputStream = socket.getOutputStream()
                val cmd = inputStream.read()
                when (cmd) {
                    1 -> { // List
                        outputStream.write(4)
                        val files = fileHelper.getFiles()
                        val fileCount = files.size
                        for (i in (0 .. 3).reversed()) {
                            outputStream.write(fileCount shr (i * 8))
                        }
                        for (file in files) {
                            outputStream.write(fileHelper.md5(file))
                            outputStream.write(file.name.toByteArray())
                            outputStream.write(0)
                        }
                    }
                    2 -> { // Get

                    }
                    3 -> { // Put

                    }
                    else -> throw ProtocolException("Incorrect ")
                }
            } finally {
                socket.close()
            }
        }
    }

    public fun connect(host: String): Socket {
        val timeout = 5000
        val socket = Socket()
        val socketAddress = InetSocketAddress(InetAddress.getByName(host), port)
        socket.connect(socketAddress, timeout)
        return socket
    }

    private class Reader(val inputStream: InputStream) {
        private val buffer = ByteArray(1024)
        private var len = 0
        private var cur = 0

        fun readFileCount(): Int {
            return getNBytes(4).toInt(0 .. 3)
        }

        fun readMD5(): String {
            val md5 = StringBuilder()
            val md5Buf = getNBytes(16)
            for (i in 0 .. 15) {
                md5.append(java.lang.String.format("%02X", md5Buf[i]))
            }
            return md5.toString()
        }

        fun readName(): String {
            if (cur == len) {
                read()
            }
            val sb = StringBuilder()
            var i = cur
            while (buffer[cur] != 0: Byte) {
                while (i < len && buffer[i] != 0: Byte) {
                    i++
                }
                sb.append(String(buffer, cur, i - cur))
                cur = i
                if (cur == len) {
                    read()
                }
            }
            cur++
            return sb.toString()
        }

        private fun getNBytes(n: Int): ByteArray {
            val result = ByteArray(n)
            for (i in 0 .. n - 1) {
                if (cur == len) {
                    read()
                }
                result[i] = buffer[cur++]
            }
            return result
        }

        private fun read() {
            len = inputStream.read(buffer)
            cur = 0
            if (len == -1) {
                throw IOException("Not enough bytes at the input stream")
            }
        }
    }

    public fun list(ip: String): List<FileInfo> {
        val socket = connect(ip)
        try {
            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()
            outputStream.write(byteArray(1))
            if (inputStream.read() != 4) {
                throw ProtocolException("Incorrect response from server")
            }
            val reader = Reader(inputStream)
            val fileCount = reader.readFileCount()
            val list = ArrayList<FileInfo>()
            for (i in 1..fileCount) {
                val md5 = reader.readMD5()
                val name = reader.readName()
                list.add(FileInfo(name, md5))
            }
            return list
        } finally {
            socket.close()
        }
    }
}
