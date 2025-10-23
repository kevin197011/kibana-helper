plugins {
    id("java")
    id("application")
}

group = "io.github.devops"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("io.github.devops.Main")
}

repositories {
    mavenCentral()
}

dependencies {
    // Jackson for JSON and YAML processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}