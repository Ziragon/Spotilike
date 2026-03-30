plugins {
    `java-library`
}

dependencies {
    api(libs.bundles.shared.exception)

    api(libs.jackson.annotations)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    compileOnly(libs.jspecify)
}