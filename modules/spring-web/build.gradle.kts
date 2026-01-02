plugins {
    java
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.11.3"
}

group = "com.bcorp"
version = "0.0.1-SNAPSHOT"
description = "In memory key-value store"

// java {
//     toolchain {
//         languageVersion = JavaLanguageVersion.of(21)
//     }
// }

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.0")

    implementation(project(":modules:core"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test") // Use this for standard testing
    testImplementation("io.projectreactor:reactor-test") // Specific for WebFlux

//    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
//    testImplementation("org.mockito:mockito-junit-jupiter")
//    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
//    testLogging {
//        events("passed", "skipped", "failed")
//    }
}
