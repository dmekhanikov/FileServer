import java.io.File;
import java.security.MessageDigest
import java.nio.file.Files
import java.io.FileInputStream
import java.security.DigestInputStream

public class FileHelper(private val basedir: File) {
    public fun getTimeStamp(): Long {
        return basedir.lastModified()
    }

    public fun getFileCount(): Int {
        val files = basedir.listFiles();
        if (files != null) {
            return files.size;
        } else {
            return 0;
        }
    }

    public fun md5(file: File): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(1024)
        val inputStream = FileInputStream(file)
        try {
            var read = inputStream.read(buffer)
            while (read != -1) {
                md.update(buffer, 0, read)
                read = inputStream.read(buffer)
            }
            return md.digest()
        } finally {
            inputStream.close()
        }
    }

    public fun getFiles(): Array    <File> {
        return basedir.listFiles()
    }
}

