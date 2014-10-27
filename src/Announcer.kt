import java.net.UnknownHostException;

public class Announcer(private val udpHelper: UdpHelper,
                       private val fileHelper: FileHelper,
                       private val name: String) {
    private val ip = udpHelper.getIp()

    public fun announce() {
        val nameBytes = name.toByteArray();
        val length = 4 + 4 + 8 + nameBytes.size + 1;
        val message = ByteArray(length);
        for (i in 0 .. 3) {
            message[i] = ip[i];
        }
        var fileCount = fileHelper.getFileCount()
        for (i in (4 .. 7).reversed()) {
            message[i] = (fileCount and ((1 shl 8) - 1)).toByte()
            fileCount = fileCount shr 8
        }
        var timeStamp = fileHelper.getTimeStamp()
        for (i in (8 .. 15).reversed()) {
            message[i] = (timeStamp and ((1 shl 8) - 1)).toByte()
            timeStamp = timeStamp shr 8;
        }
        for (i in 0 .. nameBytes.size - 1) {
            message[16 + i] = nameBytes[i];
        }
        message[length - 1] = 0;
        udpHelper.broadcast(message);
    }
}
