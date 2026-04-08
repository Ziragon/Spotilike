plugins {
    `java-library`
}

dependencies {
    api(libs.bundles.shared.exception)
    api(libs.jackson.annotations)

    compileOnly(libs.lombok)
    compileOnly(libs.jspecify)
    annotationProcessor(libs.lombok)

    testImplementation(libs.boot.starter.test)
}