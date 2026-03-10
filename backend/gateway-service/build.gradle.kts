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

    implementation(libs.jackson.core)
    implementation(libs.cloud.starter.gateway)
    implementation(libs.boot.starter.security)
    implementation(libs.springdoc.webflux.ui)

    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    implementation(libs.boot.starter)

    testImplementation(libs.boot.starter.test)
}

description = "gateway-service"