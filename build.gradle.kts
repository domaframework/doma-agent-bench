plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 17
}

application {
    mainClass = "org.domaframework.bench.Main"
}

tasks.named<JavaExec>("run") {
    // Paths such as tasks/ and results/ are resolved against the repository root
    workingDir = rootDir
}

tasks.test {
    useJUnitPlatform()
}
