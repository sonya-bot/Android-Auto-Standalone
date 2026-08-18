package com.example.androidautoselfheadunit.aap

data class AapMessage(
    val channelId: Int,
    val messageType: Int,
    val flags: Byte,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AapMessage
        if (channelId != other.channelId) return false
        if (messageType != other.messageType) return false
        if (flags != other.flags) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = channelId
        result = 31 * result + messageType
        result = 31 * result + flags.toInt()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
