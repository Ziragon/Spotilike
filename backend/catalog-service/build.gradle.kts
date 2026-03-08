plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.boot.starter)
    implementation(libs.bundles.database)
    implementation(libs.bundles.security)

    testImplementation(libs.boot.starter.test)
}

description = "catalog-service"