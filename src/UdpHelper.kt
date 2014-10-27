import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.DatagramPacket
import java.io.IOException

public class UdpHelper {
    public val port: Int = 7777
    private val socket = DatagramSocket(port, InetAddress.getByName("0.0.0.0"));
    {
        socket.setBroadcast(true)
    }

    public fun getIp(): ByteArray {
        return InetAddress.getLocalHost().getAddress();
    }

    public fun broadcast(message: ByteArray) {
        try {
            val datagramPacket = DatagramPacket(message, message.size, InetAddress.getByName("255.255.255.255"), port)
            socket.send(datagramPacket)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    public fun receiveMessage(): Host {
        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        val ip: Int = buffer.toInt(0 .. 3)
        val fileCount: Int = buffer.toInt(4 .. 7)
        val timeStamp = buffer.toLong(8 .. 15)
        var len = 0
        while (buffer[16 + len] != 0: Byte) {
            len++
        }
        val name = String(buffer, 16, len)
        return Host(name, ip, fileCount, timeStamp)
    }
}

