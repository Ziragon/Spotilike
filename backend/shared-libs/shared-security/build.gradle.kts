plugins {
    `java-library`
}

dependencies {
    api(libs.bundles.shared.security)

    compileOnly(libs.jakarta.servlet)
    compileOnly(libs.lombok)
    compileOnly(libs.jspecify)
    annotationProcessor(libs.lombok)

    testImplementation(libs.boot.starter.test)
    testImplementation(libs.jakarta.servlet)
}