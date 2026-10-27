package com.emmah.colordock.remote.tv

import org.json.JSONArray
import org.json.JSONObject

private const val MAX_CATALOG_ENTRIES = 512
private const val MAX_APP_ID_LENGTH = 512
private const val MAX_APP_TITLE_LENGTH = 200
private const val MAX_APP_ICON_URL_LENGTH = 2048

internal fun parseTvApps(
    installed: JSONObject,
    launchPoints: JSONObject?,
): List<TvApp> {
    require(installed.opt("returnValue") != false)
    val records =
        requireNotNull(installed.optJSONArray("apps")) {
            "Missing app list"
        }
    require(records.length() <= MAX_CATALOG_ENTRIES) {
        "App list too large"
    }
    val apps = linkedMapOf<String, Pair<TvApp, JSONObject>>()
    records.objects().forEach { record ->
        val app = record.app() ?: return@forEach
        if (!record.hidden()) apps.putIfAbsent(app.id, app to record)
    }
    val points =
        launchPoints
            ?.takeIf {
                it.opt("returnValue") != false
            }?.optJSONArray("launchPoints")
    if (points == null) {
        return apps.values
            .filter {
                it.second.opt("visible") == true
            }.map {
                it.first
            }.sortedWith(
                compareBy<TvApp> {
                    it.title.lowercase(java.util.Locale.ROOT)
                }.thenBy {
                    it.id
                },
            )
    }
    require(points.length() <= MAX_CATALOG_ENTRIES) {
        "Launch point list too large"
    }
    val selected = linkedMapOf<String, Pair<TvApp, Boolean>>()
    points.objects().forEach { point ->
        val app = point.app() ?: return@forEach
        val installedApp = apps[app.id]?.first ?: return@forEach
        if (point.hidden()) return@forEach
        val isDefault = point.opt("lptype") == "default"
        val previous = selected[app.id]
        if (previous == null || (isDefault && !previous.second)) {
            selected[app.id] =
                app.copy(
                    iconUrl =
                        point.text("largeIcon", MAX_APP_ICON_URL_LENGTH) ?: app.iconUrl ?: installedApp.iconUrl,
                ) to
                isDefault
        }
    }
    return selected.values.map {
        it.first
    }
}

private fun JSONArray.objects() = (0 until length()).mapNotNull(::optJSONObject)

private fun JSONObject.text(
    key: String,
    limit: Int,
) = (opt(key) as? String)?.takeIf {
    it.isNotBlank() && it.length <= limit
}

private fun JSONObject.app(): TvApp? =
    TvApp(
        text("id", MAX_APP_ID_LENGTH) ?: return null,
        text("title", MAX_APP_TITLE_LENGTH) ?: return null,
        text("icon", MAX_APP_ICON_URL_LENGTH),
    )

private fun JSONObject.hidden() =
    opt("visible") == false || opt("hidden") == true || optJSONObject("class")?.opt("hidden") == true
