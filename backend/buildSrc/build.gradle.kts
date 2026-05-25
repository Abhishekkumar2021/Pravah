plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        register("javaConventions") {
            id = "pravah.java-conventions"
            implementationClass = "io.pravah.gradle.PravahJavaConventionsPlugin"
        }
        register("springBootConventions") {
            id = "pravah.spring-boot-conventions"
            implementationClass = "io.pravah.gradle.PravahSpringBootConventionsPlugin"
        }
        register("jpaConventions") {
            id = "pravah.jpa-conventions"
            implementationClass = "io.pravah.gradle.PravahJpaConventionsPlugin"
        }
        register("kafkaConventions") {
            id = "pravah.kafka-conventions"
            implementationClass = "io.pravah.gradle.PravahKafkaConventionsPlugin"
        }
        register("grpcConventions") {
            id = "pravah.grpc-conventions"
            implementationClass = "io.pravah.gradle.PravahGrpcConventionsPlugin"
        }
    }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Plugin classpath only — keep these aligned with gradle/libs.versions.toml [versions]:
    // spring-boot, spring-dependency-management, protobuf-plugin
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.4.5")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.10.0")
}
