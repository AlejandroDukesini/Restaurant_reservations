// Parches para el classpath de los plugins (no llega al JAR): el plugin de Spring Boot
// trae Jackson 2.21.4, commons-lang3 3.16.0 y HttpComponents 5.5/5.3 con avisos publicados.
buildscript {
    dependencies {
        constraints {
            classpath("com.fasterxml.jackson:jackson-bom:2.21.8")
            classpath("com.fasterxml.jackson.core:jackson-core:2.21.8")
            classpath("com.fasterxml.jackson.core:jackson-databind:2.21.8")
            classpath("org.apache.commons:commons-lang3:3.18.0")
            classpath("org.apache.httpcomponents.client5:httpclient5:5.6.3")
            classpath("org.apache.httpcomponents.core5:httpcore5:5.4.3")
            classpath("org.apache.httpcomponents.core5:httpcore5-h2:5.4.3")
        }
    }
}

plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
}

group = "com.restaurant"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

// Parches de seguridad sobre las versiones que gestiona Spring Boot 3.5.16 (la
// ultima 3.5.x publicada), dentro de la misma rama de cada libreria:
// Tomcat 10.1.55 (GHSA-9xv2-5v5q-p794, GHSA-gcx9-497g-6cp6, GHSA-h3x4-894j-xpx5),
// Jackson 2.21.4 (varios GHSA de jackson-core/databind) y Log4j API 2.24.3
// (GHSA-qv9r-c865-cp47). Quitar al subir de Spring Boot si ya las incluye.
extra["tomcat.version"] = "10.1.61"
extra["jackson-bom.version"] = "2.21.8"
extra["log4j2.version"] = "2.25.5"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Migraciones versionadas del esquema. Desde Flyway 10 el soporte de PostgreSQL va en modulo aparte.
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    // Version gestionada por Spring Boot (42.7.1 tenia CVE-2024-1597, corregido en 42.7.2).
    runtimeOnly("org.postgresql:postgresql")
    implementation("io.jsonwebtoken:jjwt-api:0.12.7")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.7")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.7")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Base de datos en memoria (modo PostgreSQL) solo para las pruebas de integracion.
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // Produccion corre sobre JRE 21 (Dockerfile): --release impide usar APIs de un JDK
    // mas nuevo aunque Gradle se ejecute localmente con otro (p. ej. JDK 25).
    options.release = 21
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    // 0.8.11 es la primera version con soporte oficial para bytecode de Java 21.
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
