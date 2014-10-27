fun ByteArray.toInt(range: IntRange): Int {
    return toLong(range).toInt()
}

fun ByteArray.toLong(range: IntRange): Long {
    var result: Long = 0
    for (i in range) {
        result = result shl 8
        result += this[i]
    }
    return result
}

fun String.toLength(length: Int): String {
    val sb = StringBuilder()
    if (this.length < length) {
        val half = (length - this.length) / 2
        for (i in 1 .. half) {
            sb.append(" ")
        }
        sb.append(this)
        for (i in 1 .. length - this.length - half) {
            sb.append(" ")
        }
    } else {
        sb.append(this.substring(0, length))
    }
    return sb.toString()
}

fun Int.ipToString(): String {
    val mask = (1 shl 8) - 1
    return "${(this shr 24) and mask}.${(this shr 16) and mask}.${(this shr 8) and mask}.${this and mask}"
}

public class Host(public val name: String,
                  public val ip: Int,
                  public val fileCount: Int,
                  public val timeStamp: Long) {
    override fun toString(): String {
        return "| ${name.toLength(15)} | ${ip.ipToString().toLength(15)} | ${fileCount.toString().toLength(5)} | " +
                "${timeStamp.toString().toLength(15)} |"
    }
}

public class FileInfo(val name: String, val md5: String) {
    override fun toString(): String {
        return "$md5: $name"
    }
}
