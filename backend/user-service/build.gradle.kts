plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.boot.starter)
    implementation(libs.bundles.web)               // boot-starter-web + jakarta-validation + springdoc
    implementation(libs.bundles.database)          // boot-starter-data-jpa + postgresql + flyway-core + flyway-postgres
    implementation(libs.bundles.security)          // boot-starter-security + jjwt-api + jjwt-impl + jjwt-jackson

    testImplementation(libs.boot.starter.test)
    testImplementation(libs.bundles.test.infrastructure)  // testcontainers-postgresql + testcontainers-junit
}

description = "user-service"