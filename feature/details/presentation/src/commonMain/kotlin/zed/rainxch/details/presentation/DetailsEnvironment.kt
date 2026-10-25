package zed.rainxch.details.presentation

import zed.rainxch.core.domain.model.system.SystemArchitecture

// The local install environment is one row of state: it is read without network access, so it is
// applied in one write together with the release content write, never per flag.
internal data class DetailsEnvironment(
    val systemArchitecture: SystemArchitecture,
    val isObtainiumAvailable: Boolean,
    val isObtainiumEnabled: Boolean,
    val isAppManagerAvailable: Boolean,
    val isAppManagerEnabled: Boolean,
    val deviceLanguageCode: String,
    val isComingFromUpdate: Boolean,
)

internal fun RawDetailsState.withEnvironment(
    repoId: Long,
    environment: DetailsEnvironment,
): RawDetailsState =
    if (belongsToRepo(repoId)) {
        copy(
            systemArchitecture = environment.systemArchitecture,
            isObtainiumAvailable = environment.isObtainiumAvailable,
            isObtainiumEnabled = environment.isObtainiumEnabled,
            isAppManagerAvailable = environment.isAppManagerAvailable,
            isAppManagerEnabled = environment.isAppManagerEnabled,
            deviceLanguageCode = environment.deviceLanguageCode,
            isComingFromUpdate = environment.isComingFromUpdate,
        )
    } else {
        this
    }
