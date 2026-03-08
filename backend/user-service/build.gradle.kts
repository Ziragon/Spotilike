plugins {
    id("buildlogic.java-conventions")
}

dependencies {
    api(libs.org.springframework.boot.spring.boot.starter)
    api(libs.org.springframework.boot.spring.boot.starter.data.jpa)
    api(libs.org.postgresql.postgresql)
    api(libs.org.springframework.boot.spring.boot.starter.security)
    api(libs.io.jsonwebtoken.jjwt.api)
    api(libs.org.springframework.boot.spring.boot.starter.web)
    api(libs.jakarta.validation.jakarta.validation.api)
    api(libs.org.springdoc.springdoc.openapi.starter.webmvc.ui)
    api(libs.org.flywaydb.flyway.core)
    api(libs.org.flywaydb.flyway.database.postgresql)
    runtimeOnly(libs.io.jsonwebtoken.jjwt.impl)
    runtimeOnly(libs.io.jsonwebtoken.jjwt.jackson)
    testImplementation(libs.org.testcontainers.postgresql)
    testImplementation(libs.org.testcontainers.junit.jupiter)
}

description = "user-service"
