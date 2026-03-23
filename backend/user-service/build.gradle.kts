plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.boot.starter)

    implementation(libs.bundles.web)
    implementation(libs.bundles.database)
    implementation(libs.bundles.security)
    implementation("org.jspecify:jspecify:1.0.0")

    testImplementation(libs.boot.starter.test)
    testImplementation(libs.bundles.test.infrastructure)
    testImplementation(libs.boot.starter.webmvc.test)
    testImplementation("org.springframework.security:spring-security-test")
}

description = "user-service"