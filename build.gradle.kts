plugins {
    `java-library`
}

repositories {
    mavenCentral()
    mavenLocal()
}

group = "io.github.eutro.jwasm"
version = "${properties["ver_major"]}.${properties["ver_minor"]}.${properties["ver_patch"]}"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("org.ow2.asm:asm:9.1")
    implementation("org.ow2.asm:asm-commons:9.1")
    implementation("org.ow2.asm:asm-analysis:9.1")
    implementation("org.ow2.asm:asm-util:9.1")

    implementation("org.jetbrains:annotations:20.1.0")

    implementation("io.github.eutro.jwasm:jwasm:0.1.0")
    implementation("io.github.eutro.jwasm:jwasm-tree:0.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    create("runtime")
}
