plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
    kotlin("plugin.serialization") version "1.9.22"
}

kotlin {
    jvm {
        withSourcesJar()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                enableLanguageFeature("ExpectActualClasses")
            }
        }

        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("com.github.hypfvieh:dbus-java-core:5.0.0")
                implementation("com.github.hypfvieh:dbus-java-transport-jnr-unixsocket:5.0.0")
                implementation("net.java.dev.jna:jna-jpms:5.15.0")
            }
        }
    }
}

/**
 * For build call the following from x64 VS CLI:
 * ```
 * MSBuild.exe -m /property:Platform=x64 /property:Configuration=Release
 * ```
 */
tasks.register<Copy>("copyX64Dll") {
    from("src/nativeInterop/mingw-x86_64/thirdparty/libsmtc/bin/Release/x64/SMTCAdapter.dll")
    into("src/jvmMain/resources/win32-x86-64")
    rename { "libSMTCAdapter.dll" }
}

/**
 * Build the macOS Now Playing dylib for arm64 (Apple Silicon).
 * Run: ./gradlew buildMacOsAarch64Dylib
 */
tasks.register<Exec>("buildMacOsAarch64Dylib") {
    workingDir("src/nativeInterop/macos-aarch64")
    inputs.file("src/nativeInterop/macos-aarch64/nowplaying.m")
    outputs.file("${projectDir}/src/jvmMain/resources/darwin-aarch64/libnowplaying.dylib")
    commandLine(
        "clang",
        "-fobjc-arc",
        "-framework", "Foundation",
        "-framework", "AppKit",
        "-framework", "MediaPlayer",
        "-dynamiclib",
        "-o", "${projectDir}/src/jvmMain/resources/darwin-aarch64/libnowplaying.dylib",
        "nowplaying.m"
    )
}

/**
 * Build the macOS Now Playing dylib for x86_64 (Intel).
 * Run: ./gradlew buildMacOsX8664Dylib
 */
tasks.register<Exec>("buildMacOsX8664Dylib") {
    workingDir("src/nativeInterop/macos-aarch64")
    inputs.file("src/nativeInterop/macos-aarch64/nowplaying.m")
    outputs.file("${projectDir}/src/jvmMain/resources/darwin-x86-64/libnowplaying.dylib")
    commandLine(
        "clang",
        "-fobjc-arc",
        "-framework", "Foundation",
        "-framework", "AppKit",
        "-framework", "MediaPlayer",
        "-target", "x86_64-apple-macos10.13.2",
        "-dynamiclib",
        "-o", "${projectDir}/src/jvmMain/resources/darwin-x86-64/libnowplaying.dylib",
        "nowplaying.m"
    )
}
