plugins {
    java
}

group = "dev.nandi0813"
version = "1.1.0"

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.zonepractice.api)
    compileOnly(libs.json)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testImplementation(libs.paper.api)
    testImplementation(libs.zonepractice.api)
    testImplementation(libs.json)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest.attributes["paperweight-mappings-namespace"] = "mojang"
}

tasks.processResources {
    inputs.property("version", project.version.toString())
    filesMatching("plugin.yml") { expand("version" to project.version.toString()) }
}

tasks.test {
    useJUnitPlatform()
    exclude("**/smoke/**")
    failOnNoDiscoveredTests = false
}

tasks.register<Jar>("botSmokeJar") {
    dependsOn(tasks.testClasses)
    from(sourceSets.test.get().output)
    archiveFileName.set("ZPPBotsSmoke.jar")
}
