plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.boot.starter)
    implementation(libs.jspecify)

    implementation(project(":shared-libs:shared-exception"))
    implementation(project(":shared-libs:shared-security"))

    implementation(libs.bundles.web)
    implementation(libs.bundles.database)
    implementation(libs.bundles.jwt)
    implementation(libs.boot.starter.security)

    testImplementation(libs.bundles.test.web)
    testImplementation(libs.bundles.test.infrastructure)
}

description = "user-service"