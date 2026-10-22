plugins {
    alias(libs.plugins.kotlin.jvm)
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
    api(project(":core:content"))
    testImplementation(libs.kotlin.test)
}

// The placement simulation runs against the real generated content.
tasks.test {
    systemProperty("assetsDir", rootProject.file("app/src/main/assets").absolutePath)
}
