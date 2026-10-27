package com.emmah.colordock.remote.tv

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TvAppsTest {
    private fun apps(vararg records: String) = JSONObject().put("apps", JSONArray(records.map(::JSONObject)))

    private fun points(vararg records: String) = JSONObject().put("launchPoints", JSONArray(records.map(::JSONObject)))

    @Test fun visibleInstalledNativeAppsAreIncluded() {
        val result =
            parseTvApps(
                apps(
                    """{"id":"netflix","title":"Netflix","visible":true,"type":"native"}""",
                    """{"id":"youtube.leanback.v4","title":"YouTube","visible":true,"systemApp":true}""",
                ),
                points(
                    """{"id":"youtube.leanback.v4","title":"YouTube","hidden":false}""",
                    """{"id":"netflix","title":"Netflix","hidden":false}""",
                ),
            )
        assertEquals(
            listOf("youtube.leanback.v4", "netflix"),
            result.map {
                it.id
            },
        )
    }

    @Test fun launchPointOnlyPlaceholdersAreExcluded() {
        assertTrue(parseTvApps(apps(), points("""{"id":"not.installed","title":"Offer"}""")).isEmpty())
    }

    @Test fun hiddenEntriesAndDuplicateIdsAreHandled() {
        val result =
            parseTvApps(
                apps(
                    """{"id":"a","title":"Installed","visible":true,"icon":"installed.png"}""",
                    """{"id":"a","title":"Duplicate","visible":true}""",
                    """{"id":"b","title":"Hidden","visible":false}""",
                    """{"id":"c","title":"Background","class":{"hidden":true}}""",
                    """{"id":"d","title":"Hidden point","visible":true}""",
                ),
                points(
                    """{"id":"a","title":"Deep link","lptype":"bookmark"}""",
                    """{"id":"b","title":"B"}""",
                    """{"id":"c","title":"C"}""",
                    """{"id":"d","title":"D","hidden":true}""",
                    """{"id":"a","title":"Default","lptype":"default","largeIcon":"large.png",""" +
                        """"icon":"small.png"}""",
                ),
            )
        assertEquals(listOf(TvApp("a", "Default", "large.png")), result)
    }

    @Test fun unknownVisibleAppIsNotRejectedByKnownLogoList() {
        assertEquals(
            listOf(TvApp("unknown.service", "A new service", "https://192.168.1.10:3001/resources/new.png")),
            parseTvApps(
                apps(
                    """{"id":"unknown.service","title":"A new service","visible":true,""" +
                        """"icon":"https://192.168.1.10:3001/resources/new.png"}""",
                ),
                null,
            ),
        )
    }

    @Test fun missingAppsArrayFailsButEmptyAppsSucceeds() {
        for (bad in listOf("{}", "{\"apps\":{}}", "{\"apps\":null}", "{\"returnValue\":false,\"apps\":[]}")) {
            assertThrows(IllegalArgumentException::class.java) {
                parseTvApps(JSONObject(bad), null)
            }
        }
        assertEquals(emptyList<TvApp>(), parseTvApps(apps(), null))
    }

    @Test fun fallbackUsesOnlyExplicitlyVisibleInstalledApps() {
        val installed =
            apps(
                """{"id":"z","title":"Alpha","visible":true}""",
                """{"id":"a","title":"Alpha","visible":true}""",
                """{"id":"b","title":"Beta","visible":true}""",
                """{"id":"service","title":"Background"}""",
                """{"id":"hidden","title":"Hidden","visible":true,"class":{"hidden":true}}""",
            )
        for (metadata in listOf(null, JSONObject(), JSONObject("""{"returnValue":false,"launchPoints":[]}"""))) {
            assertEquals(
                listOf("a", "z", "b"),
                parseTvApps(installed, metadata).map {
                    it.id
                },
            )
        }
        assertTrue(parseTvApps(installed, points()).isEmpty())
    }

    @Test fun oversizedCatalogIsNotSilentlyTruncated() {
        val records =
            JSONArray(
                (0..512).map {
                    JSONObject().put("id", "app-$it").put("title", "App $it").put("visible", true)
                },
            )
        assertThrows(IllegalArgumentException::class.java) {
            parseTvApps(JSONObject().put("apps", records), null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseTvApps(apps(), JSONObject().put("launchPoints", records))
        }
    }

    @Test fun malformedEntriesAreSkippedAndOversizedOptionalArtworkIsIgnored() {
        val installed =
            apps(
                """{"id":123,"title":"Invalid","visible":true}""",
                """{"id":"","title":"Invalid","visible":true}""",
                """{"id":"valid","title":"Valid","visible":true}""",
            )
        installed
            .getJSONArray(
                "apps",
            ).put(JSONObject().put("id", "x".repeat(513)).put("title", "Too long").put("visible", true))
        installed
            .getJSONArray(
                "apps",
            ).put(JSONObject().put("id", "bad-title").put("title", "x".repeat(201)).put("visible", true))
        installed.getJSONArray("apps").getJSONObject(2).put("icon", "x".repeat(2049))
        assertEquals(listOf(TvApp("valid", "Valid", null)), parseTvApps(installed, null))
    }
}
