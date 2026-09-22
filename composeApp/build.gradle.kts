import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val localPurchaseProperties = Properties().apply {
    load(providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orElse("").get().reader())
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
        // RevenueCat's native library includes a build-machine Swift search path.
        // Resolve the active Xcode toolchain so native tests can link its runtime.
        if (System.getProperty("os.name") == "Mac OS X") {
            val swiftCompiler = providers.exec {
                commandLine("/usr/bin/xcrun", "--find", "swiftc")
            }.standardOutput.asText.get().trim()
            val swiftLibraries = file(swiftCompiler).parentFile.parentFile
                .resolve("lib/swift/${if (iosTarget.name == "iosArm64") "iphoneos" else "iphonesimulator"}")
            iosTarget.binaries.all {
                linkerOpts("-L${swiftLibraries.absolutePath}")
            }
        }
    }

    sourceSets {
        matching { it.name.startsWith("ios") }.configureEach {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.java.websocket)
        }
        commonMain.dependencies {
            implementation(libs.revenuecat.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    buildFeatures { buildConfig = true }
    namespace = "dev.jamescullimore.dontgotobed"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.jamescullimore.dontgotobed"
        minSdk = 26
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("release.versionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("release.versionName").orElse("1.0").get()
        val revenueCatKey = providers.gradleProperty("revenuecat.android.key")
            .orElse(localPurchaseProperties.getProperty("revenuecat.android.key", "")).get()
        require(revenueCatKey.isBlank() || revenueCatKey.matches(Regex("goog_[A-Za-z0-9]+"))) {
            "Use a Google public SDK key for revenuecat.android.key; put Test Store keys in revenuecat.test.key"
        }
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatKey\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // The optional Test Store key is local and overrides only Debug builds.
    val testPurchaseKey = providers.gradleProperty("revenuecat.test.key")
        .orElse(localPurchaseProperties.getProperty("revenuecat.test.key", "")).get()
    require(testPurchaseKey.isBlank() || testPurchaseKey.matches(Regex("test_[A-Za-z0-9]+"))) {
        "Expected a RevenueCat Test Store public SDK key for revenuecat.test.key"
    }
    buildTypes {
        getByName("debug") {
            if (testPurchaseKey.isNotBlank()) {
                buildConfigField("String", "REVENUECAT_API_KEY", "\"$testPurchaseKey\"")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    val uploadStore = providers.environmentVariable("DGTB_UPLOAD_STORE_FILE").orNull
    if (!uploadStore.isNullOrBlank()) {
        val uploadSigning = signingConfigs.create("upload") {
            storeFile = file(uploadStore)
            storePassword = providers.environmentVariable("DGTB_UPLOAD_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("DGTB_UPLOAD_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("DGTB_UPLOAD_KEY_PASSWORD").orNull
        }
        buildTypes.getByName("release").signingConfig = uploadSigning
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Opt in for the actual upload build; ordinary Release playtests remain possible.
if (providers.gradleProperty("storeRelease").orElse("false").get().toBoolean()) {
    val publicKey = providers.gradleProperty("revenuecat.android.key")
        .orElse(localPurchaseProperties.getProperty("revenuecat.android.key", ""))
    val uploadInputs = listOf("DGTB_UPLOAD_STORE_FILE", "DGTB_UPLOAD_STORE_PASSWORD", "DGTB_UPLOAD_KEY_ALIAS", "DGTB_UPLOAD_KEY_PASSWORD")
        .associateWith { providers.environmentVariable(it).orElse("") }
    val policy = layout.projectDirectory.file("src/commonMain/composeResources/files/privacy-android.txt")
    val uploadKeystore = uploadInputs.getValue("DGTB_UPLOAD_STORE_FILE").get()
        .takeIf { it.isNotBlank() }?.let { file(it) }
    val verifyStoreRelease = tasks.register("verifyStoreRelease") {
        inputs.property("purchaseKeyConfigured", publicKey.map { it.startsWith("goog_") && !it.contains("REPLACE") })
        inputs.file(policy)
        doLast {
            check(publicKey.get().startsWith("goog_") && !publicKey.get().contains("REPLACE")) { "Configure the public Google RevenueCat SDK key before the store build." }
            check(uploadInputs.values.all { it.get().isNotBlank() }) { "Configure all DGTB_UPLOAD_* signing values before the store build." }
            check(uploadKeystore?.isFile == true) { "Upload keystore file does not exist." }
            check(!policy.asFile.readText().contains("DRAFT") && !policy.asFile.readText().contains("[Publisher")) { "Finalize publisher details and regenerate the privacy policies before the store build." }
        }
    }
    tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyStoreRelease) }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
