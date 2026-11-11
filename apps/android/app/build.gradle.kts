// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
import app.mauri.sukrara.buildlogic.NativeCore
import app.mauri.sukrara.buildlogic.RELEASE_ROOT
import com.android.build.api.artifact.SingleArtifact
import java.util.zip.ZipFile

// The app: Application, AppGraph, MainActivity + navigation, manifest aggregation, src/debug tools.
plugins {
    id("sukrara.android.application")
    id("sukrara.android.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("sukrara.contracts")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:alerts"))
    implementation(project(":core:protocol"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:graph"))
    implementation(project(":monitor"))
    implementation(project(":sensor"))
    implementation(project(":share"))
    implementation(project(":integrations"))
    implementation(project(":pen"))
    implementation(project(":widgets"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:sharing"))
    implementation(project(":feature:sensor"))
    implementation(project(":feature:treatments"))
    // R8 must see the LSPosed API the System UI module implements (LSPosed provides it at run time; :monitor compiles
    // against it): without it R8 took the hooks' `intercept` for dead code and 0.12.0's module failed every hook with
    // AbstractMethodError. compileOnly reaches R8 as a library, nothing is packaged (monitor consumer-rules.pro).
    compileOnly(libs.libxposed.api)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    // the one HTTP client (DataModule.http) handed to every module that talks to the internet
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

/*
 * A release APK must carry the Libre 3 core from the pinned Juggluco revision: the owner's sensor depends on it.
 * The checkout is checked before anything is built (pre<Variant>Build roots assemble, bundle and install); the
 * finished APK / bundle is checked for the library before assemble, install or bundle complete.
 */
val jugglucoProblem = NativeCore.problem(project, NativeCore.locate(project))
val verifyJuggluco =
    tasks.register("verifyJuggluco") {
        group = "verification"
        description = "Fails a release build without the pinned, clean Juggluco checkout."
        val problem = jugglucoProblem // a plain String: the configuration cache can't hold script references
        doLast { problem?.let { throw GradleException("Release build without the Libre 3 core: $it") } }
    }
tasks.named { RELEASE_ROOT.matches(it) }.configureEach { dependsOn(verifyJuggluco) }

/** Fails unless every archive with [extension] in [outputs] contains [entry]. */
fun registerLibraryCheck(
    taskName: String,
    outputs: Provider<out FileSystemLocation>,
    extension: String,
    entry: String,
) = tasks.register(taskName) {
    group = "verification"
    description = "Fails when the release .$extension lacks $entry."
    inputs.files(outputs)
    doLast {
        val root = outputs.get().asFile
        val archives = root.walk().filter { it.extension == extension }.toList()
        if (archives.isEmpty()) throw GradleException("no .$extension in $root")
        archives.forEach { archive ->
            ZipFile(archive).use { zip -> if (zip.getEntry(entry) == null) throw GradleException("$archive has no $entry") }
        }
    }
}

tasks.withType<Test>().configureEach {
    // StringsParityTest reads every module's values/ and values-es/ strings (a changed translation runs it again)
    inputs
        .files(
            fileTree(rootDir) {
                include("**/src/main/res/values/strings.xml", "**/src/main/res/values-es/strings.xml")
                exclude("**/build/**", ".gradle/**")
            },
        ).withPropertyName("moduleStrings")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // ManifestTest compares the merged manifest's permissions and queries with this list
    inputs
        .file(rootDir.resolve("../../tools/check/allow.json"))
        .withPropertyName("allowJson")
        .withPathSensitivity(PathSensitivity.NONE)
}

androidComponents {
    // ManifestTest reads the merged debug manifests (every module's components) through the public artifact API: the
    // full flavor's, and the play flavor's to check the Play build keeps what the owner decided it keeps.
    onVariants(selector().withBuildType("debug")) { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val property = if (variant.flavorName == "play") "sukrara.playManifest" else "sukrara.mergedManifest"
        tasks.withType<Test>().configureEach {
            inputs.file(manifest).withPropertyName(property)
            jvmArgumentProviders += CommandLineArgumentProvider { listOf("-D$property=${manifest.get().asFile}") }
        }
    }
    // The Play build is an LSPosed module too (META-INF/xposed, from :monitor's resources; owner's decision 2026-11-11,
    // #142). If Play rejects the root features, the fallback (docs/20-publishing.md › Flavors) excludes those files here:
    // onVariants(selector().withFlavor("store" to "play")) { it.packaging.resources.excludes.add("META-INF/xposed/**") }
    onVariants(selector().withBuildType("release")) { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val apk = registerLibraryCheck("verify${name}ApkNativeCore", variant.artifacts.get(SingleArtifact.APK), "apk", NativeCore.LIBRARY)
        val bundle =
            registerLibraryCheck(
                "verify${name}BundleNativeCore",
                variant.artifacts.get(SingleArtifact.BUNDLE),
                "aab",
                "base/${NativeCore.LIBRARY}",
            )
        tasks.named { it == "assemble$name" || it == "install$name" }.configureEach { dependsOn(apk) }
        tasks.named { it == "bundle$name" }.configureEach { dependsOn(bundle) }
    }
    // Release unit tests would compile the release variant (and its guards) in `./gradlew check`; the debug unit
    // tests cover the same code. The play flavor differs only in app/src/play (ManifestTest checks its manifest from
    // the full flavor's run; tools/release/play-bundle.sh checks the bundle).
    beforeVariants(selector().withBuildType("release")) { it.hostTests.values.forEach { t -> t.enable = false } }
    beforeVariants(selector().withFlavor("store" to "play")) { it.hostTests.values.forEach { t -> t.enable = false } }
}
