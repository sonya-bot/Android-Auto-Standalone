@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.aap

import com.example.androidautoselfheadunit.aap.protocol.Channel
import com.example.androidautoselfheadunit.aap.protocol.MessageTypes
import com.google.protobuf.Message

open class AapMessage(
    val channelId: Int,
    val flags: Byte,
    val messageType: Int,
    val payload: ByteArray,
) {
    // Constructor to build an outgoing AAP message from a protobuf Message
    constructor(channelId: Int, messageType: Int, proto: Message) : this(
        channelId = channelId,
        flags = flags(channelId, messageType),
        messageType = messageType,
        payload = proto.toByteArray(),
    )

    fun <T : Message.Builder> parse(builder: T): T {
        builder.mergeFrom(payload)
        return builder
    }

    companion object {
        const val HEADER_SIZE = 4

        fun flags(
            channelId: Int,
            type: Int,
        ): Byte {
            var flags: Byte = 0x0b
            if (channelId != Channel.ID_CTR && MessageTypes.isControl(type)) {
                flags = 0x0f
            }
            return flags
        }
    }
}
