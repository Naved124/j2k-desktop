plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
}

// Minimal stand-ins for the Android classes Tachiyomi extensions call
// (Context, SharedPreferences, Uri, Base64, androidx.preference, ...).
dependencies {
    api(libs.kotlinx.serializationJson)
}
