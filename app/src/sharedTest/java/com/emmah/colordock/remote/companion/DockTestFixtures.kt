package com.emmah.colordock.remote.companion

import org.json.JSONArray
import org.json.JSONObject

internal object DockTestFixtures {
    fun hello() =
        JSONObject(
            """{"protocolVersion":1,"sessionId":"00000000-0000-4000-8000-000000000099",""" +
                """"limits":{"chunkBytes":12288,"imageBytes":4194304,"thumbnailBytes":131072}}""",
        )

    fun snapshot(
        revision: Int = 0,
        personal: Int = 0,
        shelfOrder: List<String> = listOf("one"),
        title: String? = "First",
    ) = JSONObject(
        """{"revision":0,"settings":{"wallpaper":{"current":"a","pool":["a"],""" +
            """"mode":"fixed","trigger":"open"},"clock":{"idleMinutes":0},""" +
            """"weather":{"enabled":false,"location":null},"shelf":{"order":[],"hidden":[]}},""" +
            """"currentWallpaper":{"id":"a","fit":"cover"},"galleryCounts":{"personal":0,""" +
            """"builtin":139},"clockActive":false}""",
    ).apply {
        put("revision", revision)
        getJSONObject("galleryCounts").put("personal", personal)
        getJSONObject("settings").getJSONObject("shelf").put("order", JSONArray(shelfOrder))
        getJSONObject("currentWallpaper").put("title", title)
    }

    fun reply(
        request: JSONObject,
        result: JSONObject,
    ) = JSONObject()
        .put("protocol", "color-dock")
        .put("version", 1)
        .put("id", request.getString("id"))
        .put("ok", true)
        .put("result", result)
}
