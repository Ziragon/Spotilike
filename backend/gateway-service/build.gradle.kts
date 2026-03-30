plugins {
    alias(libs.plugins.spring.boot)
}

dependencyManagement {
    imports {
        mavenBom(libs.cloud.dependencies.get().toString())
    }
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.boot.starter)
    implementation(libs.bundles.gateway)

    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    testImplementation(libs.bundles.test.gateway)
}

description = "gateway-service"