package com.iqsoo.qodirect.charging

/** Pure transport result, testable without an Android runtime. */
data class ShellReply(
    val ints: Map<String, Int> = emptyMap(),
    val strings: Map<String, String> = emptyMap(),
) {
    fun getInt(key: String, fallback: Int = 0) = ints[key] ?: fallback

    fun getString(key: String) = strings[key]
}

interface ShellTransport {
    suspend fun execute(op: Int): ShellReply
}

class ShellTransportTimeoutException(message: String = "Shizuku UserService 调用超时") :
    java.io.IOException(message)
