package com.emmah.colordock.remote.tv

import org.json.JSONObject

private const val MAX_CHANNEL_ID_LENGTH = 512
private const val MAX_CHANNEL_NUMBER_LENGTH = 40
private const val MAX_CHANNEL_NAME_LENGTH = 200

internal fun parseChannel(value: JSONObject): TvChannel? {
    val id = value.opt("channelId") as? String
    val number = value.opt("channelNumber") as? String
    val validId = !id.isNullOrBlank() && id.length <= MAX_CHANNEL_ID_LENGTH
    val validNumber = !number.isNullOrBlank() && number.length <= MAX_CHANNEL_NUMBER_LENGTH
    return if (validId && validNumber) {
        TvChannel(
            id,
            number,
            value.optString("channelName").take(MAX_CHANNEL_NAME_LENGTH),
        )
    } else {
        null
    }
}
