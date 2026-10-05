plugins {
    id("com.android.application")
}

android {
    namespace = "fork.MaxrregMustermann.AntiSplitNG"
    compileSdk = 36

    defaultConfig {
        applicationId = "fork.MaxrregMustermann.AntiSplitNG"
        minSdk = 19
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("keystore.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEYSTORE_ENTRY_ALIAS")
                keyPassword = System.getenv("KEYSTORE_ENTRY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Without a keystore the release build still has to work, so it falls back to the
            // debug key rather than failing. CI supplies a real keystore.
            signingConfig = if (file("keystore.jks").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
