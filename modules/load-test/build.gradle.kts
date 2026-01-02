plugins {
    java
    application
}

group = "com.bcorp"
version = "0.0.1-SNAPSHOT"
description = "Load testing console application for InFlightKv"

// java {
//     toolchain {
//         languageVersion = JavaLanguageVersion.of(21)
//     }
// }

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":modules:core"))

    implementation("com.fasterxml.jackson.core:jackson-core:2.15.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.0")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.bcorp.loadtest.LoadTestApplication"
}

tasks.test {
    useJUnitPlatform()
}
