plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "es")
    }
    namespace = "dev.hostyn.phone"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.hostyn.phone"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        // Development builds install next to the released app instead of replacing it.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.graphics.shapes)
    // Only for MaterialColors.harmonize/getColorRoles (M3 custom colors), the official implementation.
    implementation(libs.material.components)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.documentfile)
    // vCard parsing for importing contacts. Only the plain-text vCard reader/writer is used.
    implementation(libs.ezvcard) {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "org.freemarker")
        exclude(group = "org.jsoup")
    }
    debugImplementation(libs.androidx.ui.tooling)
}
