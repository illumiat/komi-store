plugins {
    alias(libs.plugins.convention.cmp.library)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.collections.immutable)

                implementation(projects.core.domain)

                // Declared directly rather than inherited: GitHubStoreImage uses Coil's
                // compose APIs, and MarkdownImageTransformer also uses Coil's network layer
                // and Ktor to probe a link before showing it.
                implementation(libs.coil3.compose)
                implementation(libs.coil3.network.ktor)
                implementation(libs.ktor.client.core)

                implementation(libs.jetbrains.lifecycle.compose)

                implementation(libs.jetbrains.compose.components.resources)
                implementation(libs.androidx.compose.ui.tooling.preview)

                implementation(libs.markdown.renderer)
                implementation(libs.markdown.renderer.coil3)
                implementation(libs.highlights)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "zed.rainxch.githubstore.core.presentation.res"
    generateResClass = auto
}
