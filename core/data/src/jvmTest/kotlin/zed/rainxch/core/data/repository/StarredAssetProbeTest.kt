package zed.rainxch.core.data.repository

import zed.rainxch.core.domain.model.repository.StarredExclusionReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The sync used to answer "can this be installed?" with a bare null, so every failure mode —
 * no releases, only prereleases, no asset for this platform, a failed probe — collapsed into
 * the same silent omission (#1040). These tests pin the distinction, because the UI wording
 * and the user's ability to act on it depend on which one it was.
 */
class StarredAssetProbeTest {
    private val apkOnly: (String) -> Boolean = { name -> name.endsWith(".apk") }

    private fun stable(vararg assets: String) = ReleaseShape(isStable = true, assetNames = assets.toList(), tag = "v1.0.0", url = "u")

    private fun prerelease(vararg assets: String) =
        ReleaseShape(isStable = false, assetNames = assets.toList(), tag = "v2.0.0-rc1", url = "u")

    @Test
    fun `no releases at all is named as such`() {
        val probe = probeFor(emptyList(), apkOnly)

        val rejected = assertIs<AssetProbe.NotInstallable>(probe)
        assertEquals(StarredExclusionReason.NO_RELEASES, rejected.reason)
    }

    @Test
    fun `only prereleases is reported separately from having no releases`() {
        val probe =
            probeFor(
                listOf(prerelease("app.apk"), prerelease("app.apk")),
                apkOnly,
            )

        val rejected = assertIs<AssetProbe.NotInstallable>(probe)
        assertEquals(StarredExclusionReason.PRERELEASE_ONLY, rejected.reason)
    }

    @Test
    fun `a stable release without a matching asset is named`() {
        val probe =
            probeFor(
                listOf(stable("app.exe", "app.dmg")),
                apkOnly,
            )

        val rejected = assertIs<AssetProbe.NotInstallable>(probe)
        assertEquals(StarredExclusionReason.NO_MATCHING_ASSET, rejected.reason)
    }

    @Test
    fun `a stable release with a matching asset is installable`() {
        val probe =
            probeFor(
                listOf(stable("app.exe", "app.apk")),
                apkOnly,
            )

        val accepted = assertIs<AssetProbe.Installable>(probe)
        assertEquals("v1.0.0", accepted.version)
    }

    @Test
    fun `a stable release is preferred over a newer prerelease`() {
        // The prerelease is listed first to prove the choice is by stability, not by order.
        val probe =
            probeFor(
                listOf(prerelease("app.apk"), stable("app.apk")),
                apkOnly,
            )

        val accepted = assertIs<AssetProbe.Installable>(probe)
        assertEquals("v1.0.0", accepted.version)
    }
}
