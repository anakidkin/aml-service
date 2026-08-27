plugins {
    id("java")
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"

    id("io.gatling.gradle") version "3.14.9.2"
    id("me.champeau.jmh") version "0.7.3"
}

group = "io.github.anakidkin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
    named("jmh") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.implementation.get())
}

configurations.named("jmhImplementation") {
    extendsFrom(configurations.implementation.get())
}

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-cassandra")
    implementation("org.springframework.kafka:spring-kafka")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.liquibase:liquibase-core")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    implementation("org.redisson:redisson-spring-boot-starter:4.7.0")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(platform("io.micrometer:micrometer-bom:1.17.0"))
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    // testcontainers
    val itImplementation = configurations.named("integrationTestImplementation").get().name
    val itCompileOnly = configurations.named("integrationTestCompileOnly").get().name
    val itAnnotationProcessor = configurations.named("integrationTestAnnotationProcessor").get().name
    add(itImplementation, platform("org.testcontainers:testcontainers-bom:2.0.5"))
    add(itImplementation, "org.springframework.boot:spring-boot-starter-test")
    add(itImplementation, "org.springframework.boot:spring-boot-testcontainers")
    add(itImplementation, "org.awaitility:awaitility:4.3.0")
    add(itImplementation, "org.testcontainers:testcontainers-junit-jupiter")
    add(itImplementation, "org.testcontainers:testcontainers-postgresql")
    add(itImplementation, "org.postgresql:postgresql")
    add(itImplementation, "org.testcontainers:testcontainers-kafka")
    add(itImplementation, "org.testcontainers:testcontainers-cassandra")
    add(itCompileOnly, "org.projectlombok:lombok")
    add(itAnnotationProcessor, "org.projectlombok:lombok")
    // jmh
    val jmhImplementation = configurations.named("jmhImplementation").get().name
    val jmhAnnotationProcessor = configurations.named("jmhAnnotationProcessor").get().name
    add(jmhImplementation, "org.openjdk.jmh:jmh-core:1.37")
    add(jmhAnnotationProcessor, "org.openjdk.jmh:jmh-generator-annprocess:1.37")
    // gatling
    val gatlingImplementation = configurations.named("gatlingImplementation").get().name
    add(gatlingImplementation, "io.gatling.highcharts:gatling-charts-highcharts:3.15.1")
    add(gatlingImplementation, "io.gatling:gatling-app:3.15.1")
    add(gatlingImplementation, "org.galaxio:gatling-kafka-plugin_2.13:2.0.0")
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests using Testcontainers."
    group = "verification"

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath

    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.check {
    dependsOn(integrationTest)
}

jmh {
    jmhVersion.set("1.37")  // me.champeau.jmh forces 1.36
    resultFormat = "JSON"
    profilers.add("gc")
}

val loadTest = tasks.register<Test>("loadTest") {
    description = "Runs Gatling performance test with Testcontainers environment."
    group = "verification"

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath

    useJUnitPlatform {
        includeTags("load-test")
    }

    systemProperty("runGatling", "true")
}
