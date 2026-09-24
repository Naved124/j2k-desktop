plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":android-compat"))
    api(libs.kotlinx.coroutinesCore)
    api(libs.kotlinx.serializationJson)
    api(libs.rxjava)
    api(libs.okhttp)
    api(libs.jsoup)

    testImplementation(libs.kotlin.testJunit)
}