plugins {
    `java-library`
}

dependencies {
    api(libs.bundles.shared.security)

    compileOnly(libs.jakarta.servlet)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}