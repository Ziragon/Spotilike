plugins {
    id("buildlogic.java-conventions")
}

dependencies {
    api(libs.org.springframework.boot.spring.boot.starter)
    api(libs.org.springframework.boot.spring.boot.starter.data.jpa)
    api(libs.org.postgresql.postgresql)
    api(libs.org.springframework.boot.spring.boot.starter.security)
}

description = "catalog-service"
