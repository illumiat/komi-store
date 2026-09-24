package zed.rainxch.core.domain.utils

import kotlin.time.Instant

object VersionMath {

    fun normalizeVersion(version: String?): String {
        if (version.isNullOrBlank()) return ""
        val cleaned = stripFullPrefix(version)
        val withoutBuildMetadata = cleaned.substringBefore('+')

        val calverNormalized = normalizeCalverHyphen(withoutBuildMetadata)

        val separated = insertHyphenBeforeKnownMarker(calverNormalized)

        val deflavoured = stripBuildVariantSuffix(separated)
        if (parseSemanticVersion(deflavoured) != null) {
            return deflavoured
        }
        if (isMarkerWithOpaqueSuffix(deflavoured)) return deflavoured
        // Must stay ahead of the numeric-prefix truncation below: a tag like
        // 26.08.11f15e4 returned verbatim is what lets versionsReconcilable
        // see the hex tail. Dropping it to "26.08.11" here would silently
        // route hash builds into the numeric comparison this guards against.
        if (hasHexTailAfterNumericPrefix(deflavoured)) return deflavoured
        val match = DOTTED_DIGIT_PATTERN.find(deflavoured)
        return match?.value ?: deflavoured
    }

    private fun stripFullPrefix(version: String): String {
        val trimmed =
            version
                .trim()
                .removePrefix("refs/tags/")
                .trim()

        val wordMatch = VERSION_WORD_PREFIX.find(trimmed)
        val withoutWord = if (wordMatch != null) trimmed.substring(wordMatch.range.last + 1) else trimmed
        return withoutWord.removePrefix("v").removePrefix("V").trim()
    }

    private fun normalizeCalverHyphen(s: String): String {
        val m = CALVER_HYPHEN_PATTERN.matchEntire(s) ?: return s
        val year = m.groupValues[1]
        val month = m.groupValues[2].padStart(2, '0')
        val day = m.groupValues[3].padStart(2, '0')
        val tail = m.groupValues.getOrNull(4).orEmpty()
        val core = "$year.$month.$day"
        return if (tail.isNotEmpty()) "$core-$tail" else core
    }

    private fun stripBuildVariantSuffix(version: String): String {
        val parsed = parseSemanticVersion(version) ?: return version
        val pre = parsed.preRelease ?: return version
        if (!isBuildVariantMarker(pre)) return version
        return parsed.numbers.joinToString(".")
    }

    private fun isBuildVariantMarker(preRelease: String): Boolean {
        if (preRelease.isEmpty()) return false

        if (preRelease.contains('.') || preRelease.contains('-')) return false
        val token = preRelease.lowercase()

        if (KNOWN_PRE_RELEASE_PREFIXES.any { token.startsWith(it) }) {
            return false
        }
        if (M_DIGIT_TAIL_PATTERN.containsMatchIn(token)) return false
        return BUILD_VARIANT_LITERALS.contains(token)
    }

    private val BUILD_VARIANT_LITERALS =
        setOf(

            "f", "m", "l", "r", "d", "x",

            "full", "mini", "minified", "lite", "release", "debug",
            "extended",

            "stable", "final", "prod", "production",
            "gms", "fdroid", "github", "store",

            "armv7", "armv8", "arm64", "armeabi",
            "x86", "x64", "x86_64", "universal",
            "android", "ios",
        )

    private fun insertHyphenBeforeKnownMarker(s: String): String {
        val match = ADJACENT_ALPHA_PATTERN.find(s) ?: return s
        val letterStart = match.range.first + 1
        val tail = s.substring(letterStart).lowercase()

        val isKnownMarker =
            KNOWN_PRE_RELEASE_PREFIXES.any { tail.startsWith(it) } ||
                M_DIGIT_TAIL_PATTERN.containsMatchIn(tail)
        if (!isKnownMarker) return s
        return s.substring(0, letterStart) + "-" + s.substring(letterStart)
    }

    fun isVersionNewer(candidate: String?, current: String?): Boolean {
        val normCandidate = normalizeVersion(candidate)
        val normCurrent = normalizeVersion(current)
        if (normCandidate.isEmpty() || normCurrent.isEmpty()) return false
        if (normCandidate == normCurrent) return false
        return compareNormalized(normCandidate, normCurrent) > 0
    }

    // Whether two versions can be compared meaningfully by number. False when one is
    // unparseable, or when exactly one carries a commit-hash-style suffix (e.g. tag
    // "2.0.9.1" vs APK versionName "2.0.9-1c19925b5") — the hash normalizes to a
    // pre-release and makes the real build look older. Callers should then track by
    // release tag instead of nagging on a bogus numeric diff (GH#729).
    fun versionsReconcilable(installed: String?, latest: String?): Boolean {
        val normalizedInstalled = normalizeVersion(installed)
        val normalizedLatest = normalizeVersion(latest)
        if (hasHexTailAfterNumericPrefix(normalizedInstalled) ||
            hasHexTailAfterNumericPrefix(normalizedLatest)
        ) {
            return false
        }
        val a = parseSemanticVersion(normalizedInstalled) ?: return false
        val b = parseSemanticVersion(normalizedLatest) ?: return false
        val aHash = a.preRelease?.let { isCommitHashPreRelease(it) } == true
        val bHash = b.preRelease?.let { isCommitHashPreRelease(it) } == true
        return aHash == bHash
    }

    private fun isCommitHashPreRelease(preRelease: String): Boolean =
        COMMIT_HASH_PATTERN.matches(preRelease)

    private fun isMarkerWithOpaqueSuffix(version: String): Boolean {
        val lower = version.lowercase()
        val marker = KNOWN_PRE_RELEASE_PREFIXES.firstOrNull { lower.startsWith(it) } ?: return false
        val rest = lower.substring(marker.length)
        if (rest.isEmpty()) return true
        if (rest.first() != '-' && rest.first() != '.') return false
        val suffix = rest.substring(1)
        // A marker followed only by dotted digits ("beta-1.2.3", "rc-1.0.10") is a
        // normal version with a pre-release prefix, not an opaque marker. Falling
        // through lets DOTTED_DIGIT_PATTERN recover "1.2.3" so the pair compares
        // numerically; treating it as opaque sent it to a string compare where
        // "1.10.0" sorted below "1.9.0". Only a non-numeric token (a hash, a word)
        // makes the tag opaque.
        if (suffix.all { it.isDigit() || it == '.' }) return false
        return suffix.isNotEmpty() && !suffix.all { it.isDigit() }
    }

    fun isOpaqueMarker(version: String?): Boolean =
        isMarkerWithOpaqueSuffix(normalizeVersion(version))

    // Tags whose update state is tracked by release timestamp rather than a
    // version number: opaque markers (nightly/rolling) and unparseable hash
    // tails (InstallerX). Both rely on a stored publishedAt baseline, so a
    // transient failure must not clear that baseline.
    fun isTimestampTrackedTag(version: String?): Boolean {
        if (version.isNullOrBlank()) return false
        if (isOpaqueMarker(version)) return true
        return hasHexTailAfterNumericPrefix(normalizeVersion(version))
    }

    // Whether the stored snapshot baseline (latestReleasePublishedAt and the latest*
    // fields that pair with it) must survive a failed check instead of being cleared.
    //
    // A timestamp-tracked tag has no version ordering to fall back on: once the
    // publishedAt baseline is dropped the next check sees a null previous timestamp and
    // re-announces the same, unchanged release. That holds whenever *either* side is
    // timestamp-tracked — a reused `nightly` (the stored latest) or an installed
    // commit-hash build — and also when the two sides cannot be compared at all, which
    // is precisely the `!reconcilable` leg UpdateVerdict routes to the timestamp branch
    // (installed "1.0.0-abc1234" vs matched "1.1.0-beta.2" is such a pair). For plain,
    // comparable pairs the baseline is safe to drop: the next check recomputes the same
    // verdict from the version numbers.
    fun shouldRetainSnapshotBaseline(
        installedTag: String?,
        storedLatestTag: String?,
    ): Boolean =
        isTimestampTrackedTag(storedLatestTag) ||
            isTimestampTrackedTag(installedTag) ||
            !versionsReconcilable(installedTag, storedLatestTag)

    // Release publish times arrive in two RFC 3339 shapes: GitHub returns UTC
    // ("2025-05-22T22:48:54Z") while Forgejo/Codeberg return a numeric offset
    // ("2026-09-17T17:27:32+02:00"). Both name the same instant as their UTC
    // spelling but compare differently as plain strings, so every "which is
    // newer" question must be answered on the parsed Instant, never on
    // lexicographic order.
    //
    // Degradation: null, blank, or unparseable input yields null. Callers read
    // a null as "no usable publish time" and therefore report nothing rather
    // than guessing — a build is only announced as newer when both sides carry
    // a real, comparable timestamp.
    internal fun parsePublishedAtToInstant(raw: String?): Instant? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching { Instant.parse(trimmed) }.getOrNull()
    }

    // True only when both timestamps parse and `candidate` is strictly later.
    // A missing or unparseable side is not evidence of a newer build (see the
    // degradation note above), so it returns false instead of falling back to
    // string order.
    internal fun isPublishedAtAfter(candidate: String?, baseline: String?): Boolean {
        val candidateInstant = parsePublishedAtToInstant(candidate) ?: return false
        val baselineInstant = parsePublishedAtToInstant(baseline) ?: return false
        return candidateInstant > baselineInstant
    }

    fun shouldReportTimestampUpdate(
        matchedTag: String?,
        matchedPublishedAt: String?,
        previousLatestPublishedAt: String?,
        previousWasUpdateAvailable: Boolean,
        previousLatestTag: String?,
    ): Boolean {
        // Presence, not parseability, decides the first-scan case: with no stored
        // baseline any non-null matched timestamp is the first observation. The
        // order comparison below is the part that needs absolute instants.
        if (previousLatestPublishedAt == null && matchedPublishedAt != null) return true
        val newerByTimestamp = isPublishedAtAfter(matchedPublishedAt, previousLatestPublishedAt)
        return newerByTimestamp ||
            (previousWasUpdateAvailable && isExactSameVersion(matchedTag, previousLatestTag))
    }

    fun compareVersions(a: String?, b: String?): Int {
        val normA = normalizeVersion(a)
        val normB = normalizeVersion(b)
        return compareNormalized(normA, normB)
    }

    fun isSameVersion(a: String?, b: String?): Boolean = compareVersions(a, b) == 0

    fun isExactSameVersion(a: String?, b: String?): Boolean {
        val cleanedA = stripCommonPrefixes(a) ?: return false
        val cleanedB = stripCommonPrefixes(b) ?: return false
        return cleanedA == cleanedB
    }

    private fun stripCommonPrefixes(version: String?): String? {
        if (version.isNullOrBlank()) return null
        val cleaned = stripFullPrefix(version)
        return cleaned.takeIf { it.isNotEmpty() }
    }

    private fun compareNormalized(a: String, b: String): Int {
        if (a == b) return 0
        val parsedA = parseSemanticVersion(a)
        val parsedB = parseSemanticVersion(b)
        if (parsedA != null && parsedB != null) {
            return compareSemver(parsedA, parsedB)
        }

        return a.compareTo(b)
    }

    private fun compareSemver(a: SemanticVersion, b: SemanticVersion): Int {
        val maxLen = maxOf(a.numbers.size, b.numbers.size)
        for (i in 0 until maxLen) {
            val ai = a.numbers.getOrElse(i) { 0L }
            val bi = b.numbers.getOrElse(i) { 0L }
            if (ai != bi) return ai.compareTo(bi)
        }

        return when {
            a.preRelease == null && b.preRelease == null -> 0
            a.preRelease == null -> 1
            b.preRelease == null -> -1
            else -> comparePreRelease(a.preRelease, b.preRelease)
        }
    }

    private fun comparePreRelease(a: String, b: String): Int {
        val aParts = a.split(".")
        val bParts = b.split(".")
        for (i in 0 until minOf(aParts.size, bParts.size)) {
            val ap = aParts[i]
            val bp = bParts[i]
            val aNum = ap.toLongOrNull()
            val bNum = bp.toLongOrNull()
            val cmp =
                when {
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    aNum != null -> -1
                    bNum != null -> 1
                    else -> ap.compareTo(bp)
                }
            if (cmp != 0) return cmp
        }
        return aParts.size.compareTo(bParts.size)
    }

    private data class SemanticVersion(
        val numbers: List<Long>,
        val preRelease: String?,
    )

    private fun parseSemanticVersion(version: String): SemanticVersion? {
        if (version.isEmpty()) return null
        val hyphenIndex = version.indexOf('-')
        val numberPart = if (hyphenIndex >= 0) version.substring(0, hyphenIndex) else version
        val preRelease =
            if (hyphenIndex >= 0 && hyphenIndex < version.length - 1) {
                version.substring(hyphenIndex + 1)
            } else {
                null
            }
        val parts = numberPart.split(".")
        val numbers = parts.mapNotNull { it.toLongOrNull() }
        if (numbers.isEmpty() || numbers.size != parts.size) return null
        return SemanticVersion(numbers, preRelease)
    }

    private val DOTTED_DIGIT_PATTERN = Regex("""\d+(?:\.\d+)*(?:-[\w.]+)?""")

    // A hash tail is a commit short-sha: a run of >=4 hex characters of which at least
    // two are letters. A digit-prefixed tag whose *version* part carries letters
    // ("1.2.3b1234", "2.0.5a2024", "2026.09.23a1") has only one a-f among digits — a
    // build/revision suffix, not a commit hash — and must keep falling through to the
    // numeric comparison. The letter count is taken from the captured tail alone, not
    // from the whole string, so letters before the tail cannot satisfy it.
    private val HEX_TAIL_AFTER_NUMERIC_PREFIX =
        Regex("""^\d+(?:\.\d+)*(?:\.)?([0-9a-f]{4,})$""", RegexOption.IGNORE_CASE)

    private fun hasHexTailAfterNumericPrefix(version: String): Boolean {
        val tail =
            HEX_TAIL_AFTER_NUMERIC_PREFIX.matchEntire(version)?.groupValues?.getOrNull(1)
                ?: return false
        return tail.count { it in 'a'..'f' || it in 'A'..'F' } >= 2
    }

    private val VERSION_WORD_PREFIX =
        Regex(
            """^(version|release|app|build|ver)\s*[-_/.]\s*""",
            RegexOption.IGNORE_CASE,
        )

    private val CALVER_HYPHEN_PATTERN =
        Regex("""^((?:19|20|21)\d{2})-(\d{1,2})-(\d{1,2})(?:[-.](.+))?$""")

    private val ADJACENT_ALPHA_PATTERN = Regex("""\d[A-Za-z]""")

    private val M_DIGIT_TAIL_PATTERN = Regex("""^m\d+""", RegexOption.IGNORE_CASE)

    private val DATE_INTEGER_PATTERN = Regex("""(?:19|20|21)\d{2}\d{2}\d{2}""")

    private val DOTTED_CALVER_PATTERN =
        Regex("""(?:19|20|21)\d{2}\.\d{1,2}\.\d{1,2}(?:\.\d+)?""")

    private val COMMIT_HASH_PATTERN = Regex("""[0-9a-f]{7,40}""")

    private val KNOWN_PRE_RELEASE_PREFIXES =
        listOf(
            "alpha",
            "beta",
            "rc",
            "preview",
            "prerelease",
            "snapshot",
            "canary",
            "nightly",
            "rolling",
            "milestone",
            "ea",
            "dev",
            "pre",
        )

    fun isPreReleaseTag(tag: String?): Boolean {
        if (tag.isNullOrBlank()) return false

        val separated = insertHyphenBeforeKnownMarker(tag)
        return PRE_RELEASE_MARKER_PATTERN.containsMatchIn(separated)
    }

    fun preReleaseMarkerLabel(tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val separated = insertHyphenBeforeKnownMarker(tag)
        val match = PRE_RELEASE_MARKER_PATTERN.find(separated) ?: return null
        val raw = match.groupValues.getOrNull(1)?.lowercase().orEmpty()
        return when {
            raw.startsWith("alpha") -> "Alpha"
            raw.startsWith("beta") -> "Beta"
            raw.startsWith("rc") -> "RC"
            raw == "preview" -> "Preview"
            raw == "prerelease" -> "Pre-release"
            raw == "snapshot" -> "Snapshot"
            raw == "canary" -> "Canary"
            raw == "nightly" -> "Nightly"
            raw == "rolling" -> "Rolling"
            raw == "milestone" || raw.startsWith("m") -> "Milestone"
            raw == "ea" -> "Early Access"
            raw == "dev" -> "Dev"
            raw == "pre" -> "Pre"
            else -> null
        }
    }

    private val PRE_RELEASE_MARKER_PATTERN =

        Regex(
            "\\b(alpha|beta|rc|preview|prerelease|snapshot|canary|nightly|rolling|milestone|ea|dev|pre|m\\d+)\\d*\\b",
            RegexOption.IGNORE_CASE,
        )

    fun detectScheme(version: String?): Scheme {
        if (version.isNullOrBlank()) return Scheme.Unknown
        val cleaned = stripFullPrefix(version).substringBefore('+')
        if (cleaned.isEmpty()) return Scheme.Unknown

        if (CALVER_HYPHEN_PATTERN.matchEntire(cleaned) != null) return Scheme.CalVer

        DATE_INTEGER_PATTERN.matchEntire(cleaned)?.let { return Scheme.CalVer }

        DOTTED_CALVER_PATTERN.matchEntire(cleaned)?.let { return Scheme.CalVer }

        val separated = insertHyphenBeforeKnownMarker(cleaned)
        if (parseSemanticVersion(separated) != null) return Scheme.SemVer

        if (COMMIT_HASH_PATTERN.matchEntire(cleaned) != null) return Scheme.CommitHash
        return Scheme.Unknown
    }

    enum class Scheme {
        SemVer,
        CalVer,
        CommitHash,
        Unknown,
    }
}
