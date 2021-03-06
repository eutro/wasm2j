plugins {
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
    mavenLocal()
}

group = "io.github.eutro.jwasm"
version = "${properties["ver_major"]}.${properties["ver_minor"]}.${properties["ver_patch"]}"
val phase = properties["ver_phase"]
if (phase != null) version = "$version-$phase"

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

tasks.register<Jar>("sourceJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

tasks.javadoc {
    setDestinationDir(file("docs"))
    (options as StandardJavadocDocletOptions).run {
        locale("en")
        links(
            "https://docs.oracle.com/javase/8/docs/api",
            "https://asm.ow2.io/javadoc/",
            "https://eutro.github.io/jwasm",
        )
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components.named("java").get())
            artifact(tasks["sourceJar"])
        }
    }
}
