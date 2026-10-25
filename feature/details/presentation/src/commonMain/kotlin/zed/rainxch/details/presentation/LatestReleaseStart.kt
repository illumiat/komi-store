package zed.rainxch.details.presentation

// The latest release is the only release read that needs nothing but owner/repo. A route that
// carries both can read it while the repository is still loading; a route with only a numeric id
// has to wait for the repository to turn that id into owner/name, and a page whose sections are
// deferred never reads a release at all.
internal fun latestReleaseStartsBeforeRepo(
    ownerParam: String,
    repoParam: String,
    sectionsDeferred: Boolean,
): Boolean = !sectionsDeferred && ownerParam.isNotBlank() && repoParam.isNotBlank()
