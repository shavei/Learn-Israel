plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:geo"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}

// Tests read the real generated assets to catch pipeline/schema drift.
tasks.test {
    systemProperty("assetsDir", rootProject.file("app/src/main/assets").absolutePath)
}
