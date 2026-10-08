plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.bracits"
version = "0.0.1-SNAPSHOT"
description = "ledger-service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Optional: -PbuildDirName=build-x gives a separate build directory (parallel local builds).
providers.gradleProperty("buildDirName").orNull?.let { layout.buildDirectory = file(it) }

repositories {
    mavenCentral()
}

// Mockito is loaded as a Java agent: JDK 21+ restricts the dynamic self-attach it would otherwise use.
val mockitoAgent: Configuration by configurations.creating

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.tigerbeetle.java)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.swagger.parser)
    testRuntimeOnly(libs.junit.platform.launcher)
    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

// The OpenAPI contract is authored in openapi/ and served as a static file at GET /openapi.yaml.
tasks.processResources {
    from("openapi/ledger-api.yaml") {
        into("static")
        rename { "openapi.yaml" }
    }
}

// The TigerBeetle client loads a JNI library.
val nativeAccess = "--enable-native-access=ALL-UNNAMED"

tasks.test {
    useJUnitPlatform()
    jvmArgs(nativeAccess, "-javaagent:${mockitoAgent.asPath}")
    // Keep test temp files (e.g. Mockito's inline mock maker jar) under build/, removed by `clean`.
    systemProperty("java.io.tmpdir", temporaryDir.absolutePath)
}

tasks.bootRun {
    jvmArgs(nativeAccess)
}
