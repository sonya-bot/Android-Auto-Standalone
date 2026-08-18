@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.aap

import com.example.androidautoselfheadunit.aap.protocol.Channel
import com.example.androidautoselfheadunit.aap.protocol.proto.Common
import com.example.androidautoselfheadunit.aap.protocol.proto.Control
import com.example.androidautoselfheadunit.aap.protocol.proto.Input
import com.example.androidautoselfheadunit.aap.protocol.proto.Media
import com.example.androidautoselfheadunit.audio.AudioChannel
import com.example.androidautoselfheadunit.video.VideoChannel

class AapMessageRouter(
    private val transport: AapTransport,
    private val controlChannel: ControlChannel,
    private val videoChannel: VideoChannel?,
    private val audioChannel: AudioChannel?,
) {
    private val sessionIds = mutableMapOf<Int, Int>()

    suspend fun handleMessage(message: AapMessage) {
        if (message.messageType == Control.ControlMsgType.MESSAGE_CHANNEL_OPEN_REQUEST_VALUE) {
            val response =
                Control.ChannelOpenResponse.newBuilder()
                    .setStatus(Common.MessageStatus.STATUS_SUCCESS)
                    .build()
            transport.sendEncrypted(
                AapMessage(
                    message.channelId,
                    Control.ControlMsgType.MESSAGE_CHANNEL_OPEN_RESPONSE_VALUE,
                    response,
                ),
            )
            return
        }

        when (message.channelId) {
            Channel.ID_CTR -> {
                if (message.messageType == Control.ControlMsgType.MESSAGE_SERVICE_DISCOVERY_REQUEST_VALUE) {
                    controlChannel.handleServiceDiscoveryRequest()
                } else if (message.messageType == Control.ControlMsgType.MESSAGE_PING_REQUEST_VALUE) {
                    val pingResponse =
                        Control.PingResponse.newBuilder()
                            .setTimestamp(System.nanoTime())
                            .build()
                    transport.sendEncrypted(
                        AapMessage(
                            message.channelId,
                            Control.ControlMsgType.MESSAGE_PING_RESPONSE_VALUE,
                            pingResponse,
                        ),
                    )
                }
            }
            Channel.ID_VID -> handleMediaControl(message)
            Channel.ID_AUD, Channel.ID_AU1, Channel.ID_AU2 -> handleMediaControl(message)
            Channel.ID_INP -> {
                if (message.messageType == 32770) {
                    val response =
                        Input.BindingResponse.newBuilder().setStatus(
                            Common.MessageStatus.STATUS_SUCCESS,
                        ).build()
                    transport.sendEncrypted(
                        AapMessage(
                            message.channelId,
                            32771,
                            response,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun sendMediaAck(channelId: Int) {
        val sessionId = sessionIds[channelId] ?: 0
        val ack = Media.Ack.newBuilder().setSessionId(sessionId).setAck(1).build()
        transport.sendEncrypted(
            AapMessage(
                channelId,
                32772,
                ack,
            ),
        )
    }

    private suspend fun handleMediaControl(message: AapMessage) {
        when (message.messageType) {
            32768 -> {
                val configResponse =
                    Media.Config.newBuilder()
                        .setStatus(Media.Config.ConfigStatus.HEADUNIT)
                        .setMaxUnacked(4)
                        .addConfigurationIndices(0)
                        .build()
                transport.sendEncrypted(
                    AapMessage(
                        message.channelId,
                        32771,
                        configResponse,
                    ),
                )

                if (message.channelId == Channel.ID_VID) {
                    val focusRequest =
                        Media.VideoFocusNotification.newBuilder()
                            .setMode(Media.VideoFocusMode.VIDEO_FOCUS_PROJECTED)
                            .setUnsolicited(false)
                            .build()
                    transport.sendEncrypted(
                        AapMessage(
                            Channel.ID_VID,
                            32776,
                            focusRequest,
                        ),
                    )
                }
            }
            32769 -> {
                val startRequest = Media.Start.parseFrom(message.payload)
                sessionIds[message.channelId] = startRequest.sessionId
            }
            32770, 32772, 32775 -> {}
            else -> {
                val flagInt = message.flags.toInt()
                val isFirstOrSingle = flagInt == 9 || flagInt == 11
                val isMediaDataOrConfig = message.messageType == 0 || message.messageType == 1

                if (message.channelId == Channel.ID_VID) {
                    videoChannel?.handleMessage(message)
                    if (isFirstOrSingle && isMediaDataOrConfig) {
                        sendMediaAck(message.channelId)
                    }
                } else if (message.channelId == Channel.ID_AUD || message.channelId == Channel.ID_AU1 || message.channelId == Channel.ID_AU2) {
                    audioChannel?.handleMessage(message)
                    if (isFirstOrSingle && isMediaDataOrConfig) {
                        sendMediaAck(message.channelId)
                    }
                }
            }
        }
    }
}
