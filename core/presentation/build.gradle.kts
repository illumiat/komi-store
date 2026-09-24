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
                // The SVG decoder for the badge URLs MarkdownImageTransformer recognises. It
                // is registered by the host, but coil3.network.ktor and MarkdownImageTransformer
                // both live here, and a host that does not add this would have its SVG badges
                // silently fall back to a broken-image glyph.
                implementation(libs.coil3.svg)
                // `api`, not `implementation`: MarkdownImageTransformer's public constructor
                // takes an `io.ktor.client.HttpClient`, so it appears in this module's ABI and
                // consumers that call the constructor need Ktor on their compile classpath.
                // No engine is declared in commonMain on purpose: the engine is platform
                // specific and is added in the android/jvm source sets below.
                api(libs.ktor.client.core)

                implementation(libs.jetbrains.lifecycle.compose)

                implementation(libs.jetbrains.compose.components.resources)
                implementation(libs.androidx.compose.ui.tooling.preview)

                // `api`, not `implementation`: MarkdownImageTransformer implements the public
                // `com.mikepenz.markdown.model.ImageTransformer` and returns `ImageData` from
                // `transform`, so both types are part of this module's ABI. A consumer that
                // refers to them (or to the transformer's own type) needs the renderer on its
                // compile classpath.
                api(libs.markdown.renderer)
                implementation(libs.markdown.renderer.coil3)
                implementation(libs.highlights)
            }
        }

        androidMain {
            dependencies {
                // coil3.network.ktor registers its fetcher through Coil's service loader and
                // constructs a default HttpClient(), which needs a Ktor engine on the runtime
                // classpath. Mirrors core/data, which supplies the same engine per platform.
                implementation(libs.ktor.client.okhttp)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
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
