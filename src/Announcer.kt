import java.net.UnknownHostException;
import okio.BufferedSink
import okio.Buffer

public class Announcer(private val udpHelper: UdpHelper,
                       private val fileHelper: FileHelper,
                       private val name: String) {
    private val ip = udpHelper.getIp()

    public fun announce() {
        val buffer = Buffer()
        buffer.write(ip)
        buffer.writeInt(fileHelper.getFileCount())
        buffer.writeLong(fileHelper.getTimeStamp())
        buffer.write(name.toByteArray())
        buffer.writeByte(0)
        udpHelper.broadcast(buffer.readByteArray());
    }
}
