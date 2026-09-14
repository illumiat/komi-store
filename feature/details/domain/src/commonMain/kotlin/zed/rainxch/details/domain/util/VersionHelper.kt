package zed.rainxch.details.domain.util

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.utils.VersionMath

object VersionHelper {
    fun normalizeVersion(version: String?): String = VersionMath.normalizeVersion(version)

    fun isDowngradeVersion(
        candidate: String,
        current: String,
        allReleases: List<GithubRelease>,
    ): Boolean {
        val candidateScheme = VersionMath.detectScheme(candidate)
        val currentScheme = VersionMath.detectScheme(current)
        val bothSchemed =
            candidateScheme != VersionMath.Scheme.Unknown &&
                currentScheme != VersionMath.Scheme.Unknown

        val cmp = VersionMath.compareVersions(candidate, current)

        if (bothSchemed) {
            return cmp < 0
        }

        if (cmp == 0) return false

        val normalizedCandidate = VersionMath.normalizeVersion(candidate)
        val normalizedCurrent = VersionMath.normalizeVersion(current)
        val candidateIndex =
            allReleases.indexOfFirst {
                VersionMath.normalizeVersion(it.tagName) == normalizedCandidate
            }
        val currentIndex =
            allReleases.indexOfFirst {
                VersionMath.normalizeVersion(it.tagName) == normalizedCurrent
            }
        if (candidateIndex != -1 && currentIndex != -1) {
            return candidateIndex > currentIndex
        }
        // Neither tag could be placed in the release list, so there is no ordering to
        // prove. The old `cmp < 0` fallback guessed anyway by comparing raw strings —
        // and "2.0.2" sorts below "nightly", so an opaque tag whose Release was deleted
        // (the CI rolling-tag pattern) made every stable release look like a downgrade
        // and leaving the nightly channel demanded an uninstall. Defer to the platform,
        // which rejects a genuine downgrade on install.
        return false
    }

    fun compareSemanticVersions(
        a: String,
        b: String,
    ): Int = VersionMath.compareVersions(a, b)
}
