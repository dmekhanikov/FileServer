import java.net.*
import okio.*
import java.io.*
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.locks.Lock
import java.util.concurrent.TimeUnit

public class TcpHelper {
    private val port = 7777
    private val serverSocket = ServerSocket(port)
    private val locks = ConcurrentHashMap<String, ReadWriteLock>()
    private val MD5_SIZE = 16

    public fun connect(host: String): Socket {
        val timeout = 5000
        val socket = Socket()
        val socketAddress = InetSocketAddress(InetAddress.getByName(host), port)
        socket.connect(socketAddress, timeout)
        return socket
    }

    public fun processClient(executor: ExecutorService, fileHelper: FileHelper) {
        val socket = serverSocket.accept()
        executor.submit {
            try {
                val source = Okio.buffer(Okio.source(socket.getInputStream()))
                val sink = Okio.buffer(Okio.sink(socket.getOutputStream()))
                try {
                    val cmd = source.readByte()
                    when (cmd) {
                        1: Byte -> {
                            // List
                            sink.writeByte(4)
                            val files = fileHelper.getFiles()
                            val fileCount = files.size
                            sink.writeInt(fileCount)
                            for (file in files) {
                                sink.write(fileHelper.md5(file))
                                sink.write(file.name.toByteArray())
                                sink.writeByte(0)
                            }
                        }
                        2: Byte -> {
                            // Get
                            val nullIndex = source.indexOf(0)
                            if (nullIndex == -1: Long) {
                                throw ProtocolException()
                            }
                            val fileName = source.readUtf8(source.indexOf(0))
                            val file = File(fileHelper.basedir, fileName)
                            if (!fileHelper.basedir.isDescendant(file)) {
                                throw FileNotFoundException("Cannot access this file")
                            }
                            if (!file.exists()) {
                                throw FileNotFoundException()
                            }
                            readLock(fileName) {
                                println("[${socket.getClientName()} started downloading file $fileName]")
                                System.out.flush()

                                sink.writeByte(5)
                                sink.writeLong(file.length())
                                sink.write(fileHelper.md5(file))
                                val fileSource = Okio.buffer(Okio.source(file))
                                sink.writeAll(fileSource)

                                println("[${socket.getClientName()} finished downloading file $fileName]")
                                System.out.flush()
                            }
                        }
                        3: Byte -> {
                            // Put
                            val nullIndex = source.indexOf(0)
                            if (nullIndex == -1: Long) {
                                throw ProtocolException()
                            }
                            val fileName = source.readUtf8(nullIndex)
                            val file = File(fileHelper.basedir, fileName)
                            if (!fileHelper.basedir.isDescendant(file)) {
                                throw FileNotFoundException("Cannot access this file")
                            }
                            source.skip(1)
                            val size = source.readLong()
                            writeLock(fileName) {
                                println("[${socket.getClientName()} started uploading file $fileName to you]")
                                System.out.flush()

                                val fileSink = Okio.buffer(Okio.sink(file))
                                fileSink.writeAll(source)
                                fileSink.flush()

                                println("[${socket.getClientName()} finishing uploading file $fileName to you]")
                                System.out.flush()
                            }
                        }
                        else -> throw ProtocolException()
                    }
                } catch (e: FileNotFoundException) {
                    sink.writeByte(0xFF)
                    sink.writeByte(0x1)
                } catch (e: TooManyConnectionsException) {
                    sink.writeByte(0xFF)
                    sink.writeByte(0x2)
                } catch (e: ProtocolException) {
                    sink.writeByte(0xFF)
                    sink.writeByte(0x3)
                } catch (e: Exception) {
                    sink.writeByte(0xFF)
                    sink.writeByte(0xFF)
                } finally {
                    sink.flush()
                }
            } finally {
                socket.close()
            }
        }
    }

    public fun list(ip: String): List<FileInfo> {
        val socket = connect(ip)
        try {
            val source: BufferedSource = Okio.buffer(Okio.source(socket.getInputStream()))
            val sink: BufferedSink = Okio.buffer(Okio.sink(socket.getOutputStream()))
            sink.writeByte(1)
            sink.flush()
            if (source.readByte() != 4: Byte) {
                throw ProtocolException("Incorrect response from server")
            }
            val fileCount = source.readInt()
            val list = ArrayList<FileInfo>()
            for (i in 1 .. fileCount) {
                val md5 = source.readMD5()
                val name = source.readUtf8(source.indexOf(0))
                source.skip(1)
                list.add(FileInfo(name, md5))
            }
            return list
        } finally {
            socket.close()
        }
    }

    public fun get(ip: String, fileName: String, fileHelper: FileHelper) {
        writeLock(fileName) {
            val socket = connect(ip)
            try {
                val source: BufferedSource = Okio.buffer(Okio.source(socket.getInputStream()))
                val sink: BufferedSink = Okio.buffer(Okio.sink(socket.getOutputStream()))
                sink.writeByte(2)
                sink.writeUtf8(fileName)
                sink.writeByte(0)
                sink.flush()

                val resp = source.readByte()
                when (resp) {
                    5: Byte -> {
                        val size = source.readLong()
                        val expectedMd5 = source.readByteArray(MD5_SIZE.toLong())
                        val tmpFile = File.createTempFile("FileServer", ".tmp")
                        tmpFile.deleteOnExit()
                        val fileSink = Okio.buffer(Okio.sink(tmpFile))
                        fileSink.writeAll(source)
                        fileSink.flush()
                        val actualMd5 = fileHelper.md5(tmpFile)
                        if (!md5Match(expectedMd5, actualMd5)) {
                            throw IOException("Checksum error")
                        }
                        tmpFile.copyTo(File(fileHelper.basedir, fileName))
                    }
                    -1: Byte -> {
                        val err = source.readByte()
                        throw when (err) {
                            1: Byte -> FileNotFoundException("No such file on a server")
                            2: Byte -> TooManyConnectionsException("Server is too busy")
                            3: Byte -> ProtocolException("Your message is badly-formed")
                            -1: Byte -> Exception("Internal server error")
                            else -> Exception("Unknown error")
                        }
                    }
                    else -> throw ProtocolException("Bad response from server")
                }
            } finally {
                socket.close()
            }
        }
    }

    public fun put(ip:String, fileName: String, fileHelper: FileHelper) {
        val file = File(fileHelper.basedir, fileName)
        if (!file.exists()) {
            throw FileNotFoundException("File not found")
        }
        readLock(fileName) {
            val socket = connect(ip)
            try {
                val sink: BufferedSink = Okio.buffer(Okio.sink(socket.getOutputStream()))
                sink.writeByte(3)
                sink.writeUtf8(fileName)
                sink.writeByte(0)
                sink.writeLong(file.length())
                val fileSource = Okio.buffer(Okio.source(file))
                sink.writeAll(fileSource)
                sink.flush()
            } finally {
                socket.close()
            }
        }
    }

    private fun Socket.getClientName(): String {
        val ip = getInetAddress().getAddress().toInt()
        return if (hosts.contains(ip)) {
            hosts[ip].name
        } else {
            ip.ipToString()
        }
    }

    private class TooManyConnectionsException(message: String = "") : Exception(message)

    private fun getLock(fileName: String): ReadWriteLock {
        var lock = locks[fileName]
        if (lock == null) {
            lock = ReentrantReadWriteLock(true)
            locks[fileName] = lock
        }
        return lock!!
    }

    private fun readLock(fileName: String, inline block: () -> Unit) {
        val lock = getLock(fileName).readLock()
        if (!lock.tryLock(5, TimeUnit.SECONDS)) {
            throw TooManyConnectionsException("Too many connections to this file")
        }
        try {
            block()
        } finally {
            lock.unlock()
        }
    }

    private fun writeLock(fileName: String, inline block: () -> Unit) {
        val lock = getLock(fileName).writeLock()
        if (!lock.tryLock(5, TimeUnit.SECONDS)) {
            throw TooManyConnectionsException("Too many connections to this file")
        }
        try {
            block()
        } finally {
            lock.unlock()
        }
    }

    private fun BufferedSource.readMD5(): String {
        val md5 = StringBuilder()
        for (i in 1 .. MD5_SIZE) {
            md5.append(java.lang.String.format("%02X", readByte()))
        }
        return md5.toString()
    }

    private fun md5Match(expected: ByteArray, actual: ByteArray): Boolean {
        if (expected.size != MD5_SIZE || actual.size != MD5_SIZE) {
            throw IllegalArgumentException("MD5 should consist of 16 bytes")
        }
        for (i in 0 .. MD5_SIZE - 1) {
            if (expected[i] != actual[i]) {
                return false
            }
        }
        return true
    }
}
