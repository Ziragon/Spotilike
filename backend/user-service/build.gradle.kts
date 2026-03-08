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

    testImplementation(libs.boot.starter.test)
    testImplementation(libs.bundles.test.infrastructure)
}

description = "user-service"