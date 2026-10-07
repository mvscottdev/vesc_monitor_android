import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin core: compiled here for JVM tests and, as plain sources, into the
// Android native module. Kotlin version must match the app build.
plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

kotlin {
    compilerOptions {
        // -Xjdk-release must equal jvmTarget. 11 restricts the JDK API surface to
        // roughly what Android API 31 can run; the app build still compiles these
        // same sources with its own jvmTarget 17.
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xjdk-release=11")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.json:json:20250517")
}

sourceSets {
    test {
        resources.srcDir("../fixtures")
        resources.srcDir("../contract")
    }
}

ktlint {
    version.set("1.8.0")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("regenerateFixtures", providers.gradleProperty("regenerateFixtures").getOrElse("false"))
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}
