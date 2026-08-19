@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.aap

import com.example.androidautoselfheadunit.aap.protocol.AudioConfigs
import com.example.androidautoselfheadunit.aap.protocol.Channel
import com.example.androidautoselfheadunit.aap.protocol.proto.Common
import com.example.androidautoselfheadunit.aap.protocol.proto.Control
import com.example.androidautoselfheadunit.aap.protocol.proto.Media
import com.example.androidautoselfheadunit.aap.protocol.proto.Sensors

class ControlChannel(
    private val transport: AapTransport,
) {
    suspend fun handleServiceDiscoveryRequest() {
        val sensorService =
            Control.Service.newBuilder().apply {
                id = Channel.ID_SEN
                sensorSourceService =
                    Control.Service.SensorSourceService.newBuilder().apply {
                        addSensors(sensor(Sensors.SensorType.DRIVING_STATUS))
                        addSensors(sensor(Sensors.SensorType.NIGHT))
                    }.build()
            }.build()

        val videoService =
            Control.Service.newBuilder().apply {
                id = Channel.ID_VID
                mediaSinkService =
                    Control.Service.MediaSinkService.newBuilder().apply {
                        availableType = Media.MediaCodecType.MEDIA_CODEC_VIDEO_H264_BP
                        audioType = Media.AudioStreamType.NONE
                        availableWhileInCall = true
                        addVideoConfigs(
                            Control.Service.MediaSinkService.VideoConfiguration.newBuilder().apply {
                                codecResolution = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1920x1080
                                frameRate = Control.Service.MediaSinkService.VideoConfiguration.VideoFrameRateType._60
                                marginHeight = 0
                                marginWidth = 0
                                density = 213
                                pixelAspectRatioE4 = 10000
                                videoCodecType = Media.MediaCodecType.MEDIA_CODEC_VIDEO_H264_BP
                            }.build(),
                        )
                    }.build()
            }.build()

        val microphoneService =
            Control.Service.newBuilder().apply {
                id = Channel.ID_MIC
                mediaSourceService =
                    Control.Service.MediaSourceService.newBuilder().apply {
                        type = Media.MediaCodecType.MEDIA_CODEC_AUDIO_PCM
                        audioConfig =
                            Media.AudioConfiguration.newBuilder().apply {
                                sampleRate = 16000
                                numberOfBits = 16
                                numberOfChannels = 1
                            }.build()
                        availableWhileInCall = true
                    }.build()
            }.build()

        val mediaPlaybackService =
            Control.Service.newBuilder().apply {
                id = Channel.ID_MPB
                mediaPlaybackService =
                    Control.Service.MediaPlaybackStatusService.newBuilder().build()
            }.build()

        val inputService =
            Control.Service.newBuilder().apply {
                id = Channel.ID_INP
                inputSourceService =
                    Control.Service.InputSourceService.newBuilder().apply {
                        touchscreen =
                            Control.Service.InputSourceService.TouchConfig.newBuilder().apply {
                                width = 1920
                                height = 1080
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
                vehicleId = "SelfHeadUnit"
                headUnitMake = "SelfHeadUnit"
                headUnitModel = "Emulator"
                headUnitSoftwareBuild = "1"
                headUnitSoftwareVersion = "1.0"
                canPlayNativeMediaDuringVr = false
                hideProjectedClock = false
                displayName = "Self Head Unit"
                driverPosition = Control.DriverPosition.DRIVER_POSITION_RIGHT

                headunitInfo =
                    Common.HeadUnitInfo.newBuilder().apply {
                        headUnitMake = "SelfHeadUnit"
                        headUnitModel = "Emulator"
                        make = "SelfHeadUnit"
                        model = "Emulator"
                        year = "2026"
                        vehicleId = "SelfHeadUnit"
                        headUnitSoftwareBuild = "1"
                        headUnitSoftwareVersion = "1.0"
                    }.build()

                addServices(sensorService)
                addServices(videoService)
                addServices(inputService)
                addServices(audioSystem)
                addServices(audioMedia)
                addServices(microphoneService)
                addServices(mediaPlaybackService)
            }.build()

        transport.sendEncrypted(
            AapMessage(
                Channel.ID_CTR,
                Control.ControlMsgType.MESSAGE_SERVICE_DISCOVERY_RESPONSE_VALUE,
                response,
            ),
        )
    }

    private fun sensor(type: Sensors.SensorType): Control.Service.SensorSourceService.Sensor =
        Control.Service.SensorSourceService.Sensor.newBuilder().setType(type).build()
}
