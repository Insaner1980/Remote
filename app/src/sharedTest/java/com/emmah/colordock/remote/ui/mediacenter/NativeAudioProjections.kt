package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ActionKind
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmMethod
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.ErrorPayload
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.MethodName
import com.emmah.colordock.remote.mediacenter.protocol.OutputsTopic
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import org.json.JSONObject

// Exact JSON emitted by the native task10_fix1_audio_projection test, not a phone-invented state.
private val nativeAudioJson =
    """
    {
      "active": {
        "effectiveOutputId": "hdmi",
        "outputs": [
          {
            "available": true,
            "id": "hdmi",
            "name": "TV"
          }
        ],
        "requestedOutputId": "hdmi",
        "revision": "7",
        "unavailable": false
      },
      "idle": {
        "outputs": [
          {
            "available": true,
            "id": "hdmi",
            "name": "TV"
          }
        ],
        "revision": "7",
        "unavailable": false
      },
      "lost": {
        "effectiveOutputId": "hdmi",
        "outputs": [],
        "requestedOutputId": "hdmi",
        "revision": "7",
        "unavailable": true
      },
      "lostAfterClear": {
        "outputs": [],
        "requestedOutputId": "hdmi",
        "revision": "7",
        "unavailable": true
      },
      "preference": {
        "outputs": [
          {
            "available": true,
            "id": "hdmi",
            "name": "TV"
          }
        ],
        "requestedOutputId": "hdmi",
        "revision": "7",
        "unavailable": false
      },
      "recovered": {
        "effectiveOutputId": "hdmi",
        "outputs": [
          {
            "available": true,
            "id": "hdmi",
            "name": "TV"
          }
        ],
        "requestedOutputId": "hdmi",
        "revision": "7",
        "unavailable": false
      },
      "returnedWithoutStream": {
        "outputs": [
          {
            "available": true,
            "id": "hdmi",
            "name": "TV"
          }
        ],
        "requestedOutputId": "hdmi",
        "revision": "7",
        "unavailable": true
      }
    }
    """.trimIndent()

internal fun nativeAudioProjection(name: String): OutputsTopic {
    val reply =
        JSONObject()
            .put(
                "v",
                1,
            ).put("id", "native-projection")
            .put("ok", true)
            .put("result", JSONObject(nativeAudioJson).getJSONObject(name))
    return (
        (
            RemoteJson.parseReply(
                MethodName.AUDIO_OUTPUTS,
                reply.toString(),
            ) as McReply.Success
        ).result as RemoteResult.AudioOutputs
    ).value
}

internal fun audioConfirmation(id: String = "confirmation"): ConfirmationPreview =
    ConfirmationPreview(
        id,
        "phone",
        ActionKind.CHANGE_AUDIO,
        listOf("TV"),
        Expected(serverEpoch = "epoch"),
        60000,
        confirmMethod = ConfirmMethod.AUDIO_CONFIRM,
    )

internal fun audioFailure(
    request: String,
    code: WireErrorCode,
    replacement: ConfirmationPreview? = null,
): McReply.Failure =
    McReply.Failure(request, ErrorPayload(code, "Review the sound output again.", false, emptyList(), replacement))
