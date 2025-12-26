//plugins {
//	java
//	id("org.springframework.boot") version "4.0.1"
//	id("io.spring.dependency-management") version "1.1.7"
//	id("org.graalvm.buildtools.native") version "0.11.3"
//}
//
//group = "com.bcorp"
//version = "0.0.1-SNAPSHOT"
//description = "In memory key-value store"
//
//java {
//	toolchain {
//		languageVersion = JavaLanguageVersion.of(21)
//	}
//}
//
//configurations {
//	compileOnly {
//		extendsFrom(configurations.annotationProcessor.get())
//	}
//}
//
//repositories {
//	mavenCentral()
//}
//
//dependencies {
//	implementation("org.springframework.boot:spring-boot-starter-webflux")
//	compileOnly("org.projectlombok:lombok")
//	annotationProcessor("org.projectlombok:lombok")
//	testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
//	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
//}
//
//tasks.withType<Test> {
//	useJUnitPlatform()
//}
