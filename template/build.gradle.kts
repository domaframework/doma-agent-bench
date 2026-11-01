plugins {
    java
    id("org.domaframework.doma.compile")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val domaVersion: String by project

dependencies {
    implementation("org.seasar.doma:doma-core:$domaVersion")
    annotationProcessor("org.seasar.doma:doma-processor:$domaVersion")
    runtimeOnly("com.h2database:h2:2.5.252")
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2:2.5.252")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}
