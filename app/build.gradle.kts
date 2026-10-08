plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val glyphSdk = file("libs/glyph-matrix-sdk-2.0.aar")

android {
    namespace = "com.madebygps.dotfasting"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.madebygps.dotfasting"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "glyph"
    productFlavors {
        create("withoutGlyph") { dimension = "glyph" }
        create("withGlyph") { dimension = "glyph" }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work)
    implementation(libs.kotlinx.coroutines)
    add("withGlyphImplementation", files(glyphSdk))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.test)
    debugImplementation(libs.androidx.compose.tooling)
    debugImplementation(libs.androidx.compose.test.manifest)
}

tasks.configureEach {
    if (name.contains("WithGlyph") && !glyphSdk.exists()) {
        doFirst {
            error("Glyph SDK missing: acquire the official SDK after reviewing its EULA and place it at app/libs/glyph-matrix-sdk-2.0.aar. Use the withoutGlyph variant otherwise.")
        }
    }
}
