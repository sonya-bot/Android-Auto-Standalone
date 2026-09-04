@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.aap

import android.util.Log
import com.example.androidautoselfheadunit.aap.protocol.Channel
import com.example.androidautoselfheadunit.aap.protocol.proto.Common
import com.example.androidautoselfheadunit.aap.protocol.proto.Control
import com.example.androidautoselfheadunit.aap.protocol.proto.Input
import com.example.androidautoselfheadunit.aap.protocol.proto.Media
import com.example.androidautoselfheadunit.aap.protocol.proto.Sensors
import com.example.androidautoselfheadunit.audio.AudioChannel
import com.example.androidautoselfheadunit.video.VideoChannel

class AapMessageRouter(
    private val transport: AapTransport,
    private val controlChannel: ControlChannel,
    var videoChannel: VideoChannel?,
    private val audioChannels: Map<Int, AudioChannel>,
    private val onPeerDisconnect: suspend () -> Unit = {},
) {
    private val sessionIds = mutableMapOf<Int, Int>()

    suspend fun handleMessage(message: AapMessage) {
        // Media data is high-frequency; keep logs useful without slowing the read loop.
        if (message.messageType != 0 && message.messageType != 1) {
            Log.i(
                TAG,
                "Received channel=${message.channelId} type=${message.messageType} " +
                    "flags=0x${(message.flags.toInt() and 0xff).toString(16)} size=${message.payload.size}",
            )
        }
        if (message.messageType == Control.ControlMsgType.MESSAGE_CHANNEL_OPEN_REQUEST_VALUE) {
            val request = Control.ChannelOpenRequest.parseFrom(message.payload)
            val isSupported = request.serviceId in SUPPORTED_SERVICE_IDS && request.serviceId == message.channelId
            val response =
                Control.ChannelOpenResponse.newBuilder()
                    .setStatus(
                        if (isSupported) {
                            Common.MessageStatus.STATUS_SUCCESS
                        } else {
                            Common.MessageStatus.STATUS_INVALID_SERVICE
                        },
                    )
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
                    val request = Control.PingRequest.parseFrom(message.payload)
                    val pingResponse =
                        Control.PingResponse.newBuilder()
                            .setTimestamp(request.timestamp)
                            .build()
                    transport.sendEncrypted(
                        AapMessage(
                            message.channelId,
                            Control.ControlMsgType.MESSAGE_PING_RESPONSE_VALUE,
                            pingResponse,
                        ),
                    )
                } else if (message.messageType == Control.ControlMsgType.MESSAGE_AUDIO_FOCUS_REQUEST_VALUE) {
                    handleAudioFocusRequest(message)
                } else if (message.messageType == Control.ControlMsgType.MESSAGE_NAV_FOCUS_REQUEST_VALUE) {
                    val request = Control.NavFocusRequestNotification.parseFrom(message.payload)
                    val response = Control.NavFocusNotification.newBuilder().setFocusType(request.focusType).build()
                    transport.sendEncrypted(
                        AapMessage(message.channelId, Control.ControlMsgType.MESSAGE_NAV_FOCUS_NOTIFICATION_VALUE, response),
                    )
                } else if (message.messageType == Control.ControlMsgType.MESSAGE_BYEBYE_REQUEST_VALUE) {
                    transport.sendEncrypted(
                        AapMessage(
                            message.channelId,
                            Control.ControlMsgType.MESSAGE_BYEBYE_RESPONSE_VALUE,
                            Control.ByeByeResponse.getDefaultInstance(),
                        ),
                    )
                    onPeerDisconnect()
                } else if (message.messageType == Control.ControlMsgType.MESSAGE_CHANNEL_CLOSE_NOTIFICATION_VALUE) {
                    sessionIds.remove(message.channelId)
                }
            }
            Channel.ID_VID -> handleMediaControl(message)
            Channel.ID_AUD, Channel.ID_AU1, Channel.ID_AU2 -> handleMediaControl(message)
            Channel.ID_SEN -> handleSensor(message)
            Channel.ID_MIC -> handleMicrophone(message)
            Channel.ID_MPB -> Unit
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

    private suspend fun handleSensor(message: AapMessage) {
        if (message.messageType != Sensors.SensorsMsgType.SENSOR_STARTREQUEST_VALUE) return

        val request = Sensors.SensorRequest.parseFrom(message.payload)
        transport.sendEncrypted(
            AapMessage(
                message.channelId,
                Sensors.SensorsMsgType.SENSOR_STARTRESPONSE_VALUE,
                Sensors.SensorResponse.newBuilder()
                    .setStatus(Common.MessageStatus.STATUS_SUCCESS)
                    .build(),
            ),
        )

        val batch =
            when (request.type) {
                Sensors.SensorType.DRIVING_STATUS ->
                    Sensors.SensorBatch.newBuilder()
                        .addDrivingStatus(
                            Sensors.SensorBatch.DrivingStatusData.newBuilder()
                                .setStatus(Sensors.SensorBatch.DrivingStatusData.Status.UNRESTRICTED.number),
                        ).build()
                Sensors.SensorType.NIGHT ->
                    Sensors.SensorBatch.newBuilder()
                        .addNightMode(
                            Sensors.SensorBatch.NightData.newBuilder().setIsNightMode(false),
                        ).build()
                else -> null
            }
        if (batch != null) {
            transport.sendEncrypted(
                AapMessage(message.channelId, Sensors.SensorsMsgType.SENSOR_EVENT_VALUE, batch),
            )
        }
    }

    private suspend fun handleMicrophone(message: AapMessage) {
        if (message.messageType != Media.MediaMsgType.MEDIA_MESSAGE_MICROPHONE_REQUEST_VALUE) return
        val request = Media.MicrophoneRequest.parseFrom(message.payload)
        val status =
            if (request.open) {
                Common.MessageStatus.STATUS_COMMAND_NOT_SUPPORTED_VALUE
            } else {
                Common.MessageStatus.STATUS_SUCCESS_VALUE
            }
        val response = Media.MicrophoneResponse.newBuilder().setStatus(status).setSessionId(0).build()
        transport.sendEncrypted(
            AapMessage(message.channelId, Media.MediaMsgType.MEDIA_MESSAGE_MICROPHONE_RESPONSE_VALUE, response),
        )
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

    private suspend fun handleAudioFocusRequest(message: AapMessage) {
        val request = Control.AudioFocusRequestNotification.parseFrom(message.payload)
        val mappedState =
            when (request.request) {
                Control.AudioFocusRequestNotification.AudioFocusRequestType.RELEASE -> Control.AudioFocusNotification.AudioFocusStateType.STATE_LOSS
                Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN -> Control.AudioFocusNotification.AudioFocusStateType.STATE_GAIN
                Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_TRANSIENT -> Control.AudioFocusNotification.AudioFocusStateType.STATE_GAIN_TRANSIENT
                Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_TRANSIENT_MAY_DUCK -> Control.AudioFocusNotification.AudioFocusStateType.STATE_GAIN_TRANSIENT_GUIDANCE_ONLY
                else -> Control.AudioFocusNotification.AudioFocusStateType.STATE_GAIN
            }
        val response =
            Control.AudioFocusNotification.newBuilder()
                .setFocusState(mappedState)
                .build()
        transport.sendEncrypted(
            AapMessage(
                message.channelId,
                Control.ControlMsgType.MESSAGE_AUDIO_FOCUS_NOTIFICATION_VALUE,
                response,
            ),
        )
    }

    private suspend fun handleMediaControl(message: AapMessage) {
        when (message.messageType) {
            Control.ControlMsgType.MESSAGE_AUDIO_FOCUS_REQUEST_VALUE -> {
                handleAudioFocusRequest(message)
            }
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
            32770 -> {
                sessionIds.remove(message.channelId)
                if (message.channelId == Channel.ID_VID) videoChannel?.stopStream()
            }
            32772 -> Unit
            32775 -> {
                if (message.channelId == Channel.ID_VID) {
                    val request = Media.VideoFocusRequestNotification.parseFrom(message.payload)
                    val response =
                        Media.VideoFocusNotification.newBuilder()
                            .setMode(request.mode)
                            .setUnsolicited(false)
                            .build()
                    transport.sendEncrypted(AapMessage(Channel.ID_VID, 32776, response))
                }
            }
            else -> {
                val isMediaDataOrConfig = message.messageType == 0 || message.messageType == 1

                if (message.channelId == Channel.ID_VID) {
                    val accepted = videoChannel?.handleMessage(message, isConfig = message.messageType == 1) == true
                    if (accepted && isMediaDataOrConfig) {
                        sendMediaAck(message.channelId)
                    }
                } else if (message.channelId == Channel.ID_AUD || message.channelId == Channel.ID_AU1 || message.channelId == Channel.ID_AU2) {
                    audioChannels[message.channelId]?.handleMessage(message)
                    if (isMediaDataOrConfig) {
                        sendMediaAck(message.channelId)
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "AapMessageRouter"
        val SUPPORTED_SERVICE_IDS =
            setOf(
                Channel.ID_SEN,
                Channel.ID_VID,
                Channel.ID_INP,
                Channel.ID_AUD,
                Channel.ID_AU2,
                Channel.ID_MIC,
                Channel.ID_MPB,
            )
    }
}
