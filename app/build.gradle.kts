plugins {
    id("com.android.application")
}

// Signing a release is opt-in. Without SIGNING_KEYSTORE pointing at a keystore the release build
// is intentionally left unsigned rather than quietly falling back to the debug key, which would
// produce an APK that looks like a release but is signed with the wrong identity. See
// docs/releasing.md for how to produce a signed release.
val signingKeystore = providers.environmentVariable("SIGNING_KEYSTORE").orNull

android {
    namespace = "fork.MaxrregMustermann.AntiSplitNG"
    compileSdk = 36

    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
            }
        }
    }

    defaultConfig {
        applicationId = "fork.MaxrregMustermann.AntiSplitNG"
        minSdk = 19
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        // ARSCLib targets Java 8 APIs that Android only gained in API 24
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true

            all { test ->
                // Robolectric replaces framework internals reflectively, which the module system
                // blocks unless these packages are opened up.
                test.jvmArgs(
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                )
            }
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    lint {
        abortOnError = true
        // Translations are community maintained and land per locale, so a locale missing a
        // string, or still carrying one the default locale dropped, is expected.
        disable += setOf("ExtraTranslation", "MissingTranslation")
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.androidx.core)
    implementation(libs.material)
    implementation(libs.arsclib)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.bouncycastle)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
}
