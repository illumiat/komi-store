package zed.rainxch.core.domain.model.installation

import kotlin.test.Test
import kotlin.test.assertEquals

class TagForObservedBuildTest {
    @Test
    fun digestMismatchKeepsTheInstalledTag() {
        val app =
            testInstalledApp(
                installedVersion = "build-k",
                installedVersionCode = 23,
                latestVersion = "build-o",
                latestVersionCode = 23,
                installedAssetDigest = "sha256:k",
            ).copy(latestAssetDigest = "sha256:o")

        assertEquals("build-k", app.tagForObservedBuild("1.9.4", 23))
    }

    @Test
    fun matchingDigestAdoptsTheSnapshotTag() {
        val app =
            testInstalledApp(
                installedVersion = "build-k",
                installedVersionCode = 23,
                latestVersion = "build-o",
                latestVersionCode = 23,
                installedAssetDigest = "sha256:o",
            ).copy(latestAssetDigest = "sha256:o")

        assertEquals("build-o", app.tagForObservedBuild("1.9.4", 23))
    }
}
