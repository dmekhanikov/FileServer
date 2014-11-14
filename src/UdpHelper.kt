import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.DatagramPacket
import java.io.IOException
import okio.Buffer

public class UdpHelper {
    public val port: Int = 7777
    private val socket = DatagramSocket(port, InetAddress.getByName("0.0.0.0"));
    {
        socket.setBroadcast(true)
    }

    public fun getIp(): ByteArray {
        return InetAddress.getLocalHost().getAddress()
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
        val byteBuffer = ByteArray(1024)
        val packet = DatagramPacket(byteBuffer, byteBuffer.size)
        socket.receive(packet)
        val buffer = Buffer()
        buffer.write(byteBuffer)

        val ip = buffer.readInt()
        val fileCount = buffer.readInt()
        val timeStamp = buffer.readLong()
        val name = buffer.readUtf8(buffer.indexOf(0))
        return Host(name, ip, fileCount, timeStamp)
    }
}
