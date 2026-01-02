plugins {
    id("java")
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
        compileClasspath += sourceSets.test.get().output
        runtimeClasspath += sourceSets.test.get().output
    }
}

configurations {
    named("integrationTestImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    named("integrationTestRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
}

group = "com.bcorp"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":modules:core"))

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.cucumber:cucumber-java:7.33.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.15.0")
    testImplementation("org.junit.platform:junit-platform-suite:1.10.2")

    // REST-assured for HTTP API testing
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.15.0")
}

tasks.test {
    useJUnitPlatform()
    // Exclude the integration test suite from the unit test run
    exclude("com/bcorp/docs/singleNodeTest/**")
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    // Explicitly point to the integrationTest output
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath

    useJUnitPlatform()

    // Optional: Force it to run every time you call this specific task
    outputs.upToDateWhen { false }
}