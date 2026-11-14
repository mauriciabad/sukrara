// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import app.mauri.sukrara.buildlogic.AppVersion
import app.mauri.sukrara.buildlogic.RELEASE_ROOT
import app.mauri.sukrara.buildlogic.Sdk
import app.mauri.sukrara.buildlogic.configureAndroid
import app.mauri.sukrara.buildlogic.repoRoot
import app.mauri.sukrara.buildlogic.testDependencies
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File

/**
 * The app module: id `app.mauri.sukrara` for debug and release (one install per phone), version from `<repo>/VERSION`,
 * arm64 only, R8 + resource shrinking on release, release signed with the dedicated Sukrara key.
 *
 * Two product flavors in the `store` dimension, one package and one key (docs/20-publishing.md › Flavors): `full`
 * (GitHub, Obtainium, IzzyOnDroid: everything) and `play` (Google Play, an app bundle from
 * tools/release/play-bundle.sh, without what Play's policies don't allow: its differences are all in `app/src/play`).
 *
 * What the build is (docs/DEVELOPMENT.md › Development and release builds): the manifest's `app.mauri.sukrara.build`
 * meta-data, which About and every report read (`AppBuildInfo`). Release builds take it from
 * `tools/release/build-info.sh`: "release" only with `SUKRARA_RELEASE_BUILD=1` (the release scripts) at the release
 * commit, else "development <commits> <commit> <date>"; debug builds are "development" (the same for every commit,
 * so the build cache keeps them). verify-apk.sh checks it.
 *
 * Release signing (docs/DEVELOPMENT.md): keystore `SUKRARA_KEYSTORE` (default `~/.android/sukrara-release.jks`,
 * alias `sukrara`), password from env `SUKRARA_KEYSTORE_PASSWORD` only — Gradle never reads the Keychain; the release
 * scripts export it. A release build without them stops in `verifyReleaseSigning` with the instructions.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("sukrara.quality")
        val version = AppVersion.parse(repoRoot.file("VERSION").asFile.readText())
        val keystore = providers.environmentVariable("SUKRARA_KEYSTORE").orNull?.let(::File)
            ?: File(System.getProperty("user.home"), ".android/sukrara-release.jks")
        val password = providers.environmentVariable("SUKRARA_KEYSTORE_PASSWORD").orNull
        val signable = keystore.isFile && !password.isNullOrEmpty()

        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            defaultConfig {
                applicationId = "app.mauri.sukrara"
                targetSdk = Sdk.TARGET
                versionCode = version.code
                versionName = version.name
                manifestPlaceholders[BUILD_PLACEHOLDER] = "development"
                ndk { abiFilters += "arm64-v8a" }
            }
            buildFeatures.buildConfig = true
            flavorDimensions += STORE
            productFlavors.create("full") {
                dimension = STORE
                isDefault = true
            }
            productFlavors.create("play") { dimension = STORE }
            // Every language in every install: Sukrara has its own language setting, and a language split that Play
            // didn't install would leave that choice without its strings.
            bundle.language.enableSplit = false
            signingConfigs.create("release") {
                if (signable) {
                    storeFile = keystore
                    storePassword = password
                    keyAlias = "sukrara"
                    keyPassword = password
                }
            }
            buildTypes.getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                if (signable) signingConfig = signingConfigs.getByName("release")
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }
            androidResources {
                generateLocaleConfig = true
                localeFilters += listOf("en", "es")
            }
        }
        testDependencies()

        val releaseFlag = providers.environmentVariable("SUKRARA_RELEASE_BUILD").orElse("")
        val buildInfoScript = repoRoot.file("tools/release/build-info.sh").asFile.path
        val buildInfo =
            providers.exec {
                commandLine("sh", buildInfoScript)
                environment("SUKRARA_RELEASE_BUILD", releaseFlag.get())
                isIgnoreExitValue = true
            }
        val buildLine =
            buildInfo.standardOutput.asText.map { out ->
                if (buildInfo.result.get().exitValue != 0) throw GradleException(buildInfo.standardError.asText.get().trim())
                out.trim()
            }
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants(selector().withBuildType("release")) { it.manifestPlaceholders.put(BUILD_PLACEHOLDER, buildLine) }
        }

        val keystorePath = keystore.path
        val verifySigning = tasks.register("verifyReleaseSigning") {
            group = "verification"
            description = "Fails a release build that would not be signed with the Sukrara release key."
            doLast {
                if (!signable) {
                    throw GradleException(
                        "Release signing key not available (keystore $keystorePath, env SUKRARA_KEYSTORE_PASSWORD). " +
                            "Create the key once with tools/release/make-keystore.sh, then build with\n" +
                            "  SUKRARA_KEYSTORE_PASSWORD=\"\$(security find-generic-password -s sukrara-android-release " +
                            "-a sukrara -w)\" ./gradlew :app:assembleFullRelease",
                    )
                }
            }
        }
        // pre<Variant>Build is the root of every release output (assemble, bundle, install) of either flavor.
        tasks.named { RELEASE_ROOT.matches(it) }.configureEach { dependsOn(verifySigning) }
    }

    private companion object {
        /** The flavor dimension: where the build is distributed. */
        const val STORE = "store"

        /** The manifest placeholder of the `app.mauri.sukrara.build` meta-data (tools/release/build-info.sh). */
        const val BUILD_PLACEHOLDER = "sukraraBuild"
    }
}
