plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.2.5")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.4")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.10.0")
}
