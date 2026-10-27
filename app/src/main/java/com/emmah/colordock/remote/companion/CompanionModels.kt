package com.emmah.colordock.remote.companion

import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_LOCATION_TEXT = 120
private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

enum class DockConnectionState {
    Unavailable,
    Connecting,
    Ready,
    Incompatible,
    Failed,
}

data class DockState(
    val connection: DockConnectionState = DockConnectionState.Unavailable,
    val snapshot: DockSnapshot? = null,
    val message: String? = null,
)

data class WallpaperSettings(
    val current: String,
    val pool: List<String>,
    val mode: String,
    val trigger: String,
)

data class WeatherLocation(
    val name: String,
    val admin1: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
) {
    val label: String get() = listOf(name, admin1, country).filter(String::isNotBlank).distinct().joinToString(", ")

    fun json() =
        JSONObject()
            .put(
                "name",
                name,
            ).put("admin1", admin1)
            .put("country", country)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("timezone", timezone)
}

data class WallpaperMetadata(
    val id: String,
    val title: String,
    val fit: String,
    val category: String?,
    val categoryLabel: String?,
    val thumbnailHash: String? = null,
) {
    val personal get() = id.startsWith("user:")
}

data class DockSnapshot(
    val revision: Long,
    val wallpaper: WallpaperSettings,
    val current: WallpaperMetadata,
    val personalCount: Int,
    val builtinCount: Int,
    val idleMinutes: Int,
    val weatherEnabled: Boolean,
    val location: WeatherLocation?,
    val shelfOrder: List<String>,
    val shelfHidden: List<String>,
    val clockActive: Boolean,
)

data class GalleryPage(
    val items: List<WallpaperMetadata>,
    val revision: Long,
    val nextCursor: String?,
)

data class DockLimits(
    val chunkBytes: Int = 12288,
    val imageBytes: Int = 4194304,
    val thumbnailBytes: Int = 131072,
)

enum class DockMethod(
    val wire: String,
) {
    State("state.get"),
    Gallery("gallery.list"),
    Thumbnail("gallery.thumbnail"),
    Patch("settings.patch"),
    Use("wallpaper.use"),
    Next("wallpaper.next"),
    Fit("wallpaper.fit"),
    Delete("wallpaper.delete"),
    ClockStart("clock.start"),
    ClockStop("clock.stop"),
    UploadBegin("upload.begin"),
    UploadChunk("upload.chunk"),
    UploadCommit("upload.commit"),
    UploadCancel("upload.cancel"),
    UploadStatus("upload.status"),
}

sealed interface DockPatch {
    fun json(): JSONObject

    data class Wallpaper(
        val mode: String? = null,
        val trigger: String? = null,
        val pool: List<String>? = null,
    ) : DockPatch {
        override fun json() =
            JSONObject().put(
                "wallpaper",
                JSONObject().apply {
                    mode?.let {
                        put("mode", it)
                    }
                    trigger?.let {
                        put("trigger", it)
                    }
                    pool?.let {
                        put("pool", JSONArray(it))
                    }
                },
            )
    }

    data class Clock(
        val minutes: Int,
    ) : DockPatch {
        override fun json() = JSONObject().put("clock", JSONObject().put("idleMinutes", minutes))
    }

    data class Weather(
        val enabled: Boolean,
        val location: WeatherLocation?,
    ) : DockPatch {
        override fun json() =
            JSONObject().put(
                "weather",
                JSONObject().put("enabled", enabled).put(
                    "location",
                    location?.json() ?: JSONObject.NULL,
                ),
            )
    }

    data class Shelf(
        val order: List<String>,
        val hidden: List<String>,
    ) : DockPatch {
        override fun json() =
            JSONObject().put("shelf", JSONObject().put("order", JSONArray(order)).put("hidden", JSONArray(hidden)))
    }
}

sealed interface DockResult<out T> {
    data class Success<T>(
        val value: T,
    ) : DockResult<T>

    data class Failure(
        val code: String,
        val message: String,
    ) : DockResult<Nothing>
}

interface DockControl {
    val state: StateFlow<DockState>

    suspend fun join(): DockResult<DockSnapshot>

    suspend fun snapshot(): DockResult<DockSnapshot>

    suspend fun patch(
        expectedRevision: Long,
        patch: DockPatch,
    ): DockResult<DockSnapshot>

    suspend fun call(
        method: DockMethod,
        params: JSONObject,
    ): DockResult<JSONObject>

    fun disconnect()
}

internal object DockCodec {
    fun integer(value: Any?): Long {
        require(value is Number)
        return try {
            value.toString().toBigDecimal().longValueExact()
        } catch (
            _: ArithmeticException,
        ) {
            throw IllegalArgumentException("Invalid integer")
        }
    }

    fun strings(array: JSONArray): List<String> =
        (0 until array.length()).map {
            array.getString(it)
        }

    fun image(value: JSONObject) =
        WallpaperMetadata(
            value.getString(
                "id",
            ),
            value.optString("title", value.optString("name", "Picture")),
            value.getString("fit"),
            value.optString("category").ifEmpty {
                null
            },
            value.optString("categoryLabel").ifEmpty {
                null
            },
            value.optString("thumbnailHash").ifEmpty {
                null
            },
        )

    fun location(value: JSONObject): WeatherLocation {
        fun text(key: String) =
            (value.get(key) as String).also {
                require(it.length <= MAX_LOCATION_TEXT)
            }
        val latitude = (value.get("latitude") as Number).toDouble()
        val longitude = (value.get("longitude") as Number).toDouble()
        require(
            latitude.isFinite() && latitude in -MAX_LATITUDE..MAX_LATITUDE && longitude.isFinite() &&
                longitude in -MAX_LONGITUDE..MAX_LONGITUDE,
        )
        val timezone = value.get("timezone") as String
        java.time.ZoneId.of(timezone)
        return WeatherLocation(
            text("name").also {
                require(it.isNotBlank())
            },
            text("admin1"),
            text("country"),
            latitude,
            longitude,
            timezone,
        )
    }

    fun snapshot(value: JSONObject): DockSnapshot {
        val settings = value.getJSONObject("settings")
        val wall = settings.getJSONObject("wallpaper")
        val weather = settings.getJSONObject("weather")
        val shelf = settings.getJSONObject("shelf")
        val counts = value.getJSONObject("galleryCounts")
        val revision =
            integer(value.opt("revision")).also {
                require(it >= 0)
            }
        return DockSnapshot(
            revision,
            WallpaperSettings(
                wall.getString("current"),
                strings(wall.getJSONArray("pool")),
                wall.getString("mode"),
                wall.getString("trigger"),
            ),
            image(value.getJSONObject("currentWallpaper")),
            counts.getInt("personal"),
            counts.getInt("builtin"),
            settings.getJSONObject("clock").getInt("idleMinutes"),
            weather.getBoolean("enabled"),
            if (weather.isNull("location")) null else location(weather.getJSONObject("location")),
            strings(shelf.getJSONArray("order")),
            strings(shelf.getJSONArray("hidden")),
            value.getBoolean("clockActive"),
        )
    }

    fun reply(
        value: JSONObject,
        id: String,
    ): JSONObject {
        require(value.opt("protocol") == "color-dock" && integer(value.opt("version")) == 1L && value.opt("id") == id)
        val ok = requireNotNull(value.opt("ok") as? Boolean) { "Invalid reply" }
        if (!ok) {
            val code =
                requireNotNull(value.getJSONObject("error").opt("code") as? String) { "Invalid error" }
            require(code.isNotBlank())
            throw DockException(code)
        }
        return value.getJSONObject("result")
    }
}

internal class DockException(
    val code: String,
) : Exception(code)
