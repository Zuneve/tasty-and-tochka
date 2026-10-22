plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("java")
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.data:spring-data-r2dbc")
    implementation("org.springframework:spring-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(project(":catalog"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
