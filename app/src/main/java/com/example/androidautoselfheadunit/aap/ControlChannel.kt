@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.aap

import com.example.androidautoselfheadunit.aap.protocol.AudioConfigs
import com.example.androidautoselfheadunit.aap.protocol.Channel
import com.example.androidautoselfheadunit.aap.protocol.proto.Common
import com.example.androidautoselfheadunit.aap.protocol.proto.Control
import com.example.androidautoselfheadunit.aap.protocol.proto.Media

class ControlChannel(
    private val transport: AapTransport,
) {
    suspend fun handleServiceDiscoveryRequest() {
        val videoService =
            Control.Service.newBuilder().apply {
                id = Channel.ID_VID
                mediaSinkService =
                    Control.Service.MediaSinkService.newBuilder().apply {
                        availableType = Media.MediaCodecType.MEDIA_CODEC_VIDEO_H264_BP
                        addVideoConfigs(
                            Control.Service.MediaSinkService.VideoConfiguration.newBuilder().apply {
                                codecResolution = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480
                                frameRate = Control.Service.MediaSinkService.VideoConfiguration.VideoFrameRateType._30
                                marginHeight = 0
                                marginWidth = 0
                                density = 160
                                pixelAspectRatioE4 = 10000
                                videoCodecType = Media.MediaCodecType.MEDIA_CODEC_VIDEO_H264_BP
                            }.build(),
                        )
                    }.build()
            }.build()

        val inputService =
            Control.Service.newBuilder().apply {
                id = Channel.ID_INP
                inputSourceService =
                    Control.Service.InputSourceService.newBuilder().apply {
                        touchscreen =
                            Control.Service.InputSourceService.TouchConfig.newBuilder().apply {
                                width = 800
                                height = 480
                            }.build()
                        addKeycodesSupported(66) // ENTER
                    }.build()
            }.build()

        val audioSystem =
            Control.Service.newBuilder().apply {
                id = Channel.ID_AU2
                mediaSinkService =
                    Control.Service.MediaSinkService.newBuilder().apply {
                        availableType = Media.MediaCodecType.MEDIA_CODEC_AUDIO_PCM
                        audioType = Media.AudioStreamType.SYSTEM
                        addAudioConfigs(AudioConfigs.get(Channel.ID_AU2))
                    }.build()
            }.build()

        val audioMedia =
            Control.Service.newBuilder().apply {
                id = Channel.ID_AUD
                mediaSinkService =
                    Control.Service.MediaSinkService.newBuilder().apply {
                        availableType = Media.MediaCodecType.MEDIA_CODEC_AUDIO_PCM
                        audioType = Media.AudioStreamType.MEDIA
                        addAudioConfigs(AudioConfigs.get(Channel.ID_AUD))
                    }.build()
            }.build()

        val response =
            Control.ServiceDiscoveryResponse.newBuilder().apply {
                make = "SelfHeadUnit"
                model = "Emulator"
                year = "2026"
                headUnitMake = "SelfHeadUnit"
                headUnitModel = "Emulator"
                headUnitSoftwareBuild = "1"
                headUnitSoftwareVersion = "1.0"
                driverPosition = Control.DriverPosition.DRIVER_POSITION_RIGHT

                headunitInfo =
                    Common.HeadUnitInfo.newBuilder().apply {
                        headUnitMake = "SelfHeadUnit"
                        headUnitModel = "Emulator"
                        make = "SelfHeadUnit"
                        model = "Emulator"
                        year = "2026"
                        headUnitSoftwareBuild = "1"
                        headUnitSoftwareVersion = "1.0"
                    }.build()

                addServices(videoService)
                addServices(inputService)
                addServices(audioSystem)
                addServices(audioMedia)
            }.build()

        transport.sendEncrypted(
            AapMessage(
                Channel.ID_CTR,
                Control.ControlMsgType.MESSAGE_SERVICE_DISCOVERY_RESPONSE_VALUE,
                response,
            ),
        )
    }
}
