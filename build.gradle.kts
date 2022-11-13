plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.eutro.jwasm"
version = "${properties["ver_major"]}.${properties["ver_minor"]}.${properties["ver_patch"]}"
val phase = properties["ver_phase"]
if (phase != null) version = "$version-$phase"

allprojects {
    apply<JavaLibraryPlugin>()
    apply<MavenPublishPlugin>()

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

val jwasmVer = properties["jwasm_version"]

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }

    dependencies {
        implementation("org.jetbrains:annotations:23.0.0")

        testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

        testImplementation("io.github.eutro.jwasm:jwasm-sexp:$jwasmVer")
        testImplementation("io.github.eutro.jwasm:jwasm-test:$jwasmVer")
    }
}

project(":wasm2j-embed") {
    dependencies {
        implementation("org.ow2.asm:asm:9.4")
        implementation("org.ow2.asm:asm-tree:9.4")

        implementation("io.github.eutro.jwasm:jwasm:$jwasmVer")
        implementation("io.github.eutro.jwasm:jwasm-tree:$jwasmVer")
        implementation("io.github.eutro.jwasm:jwasm-analysis:$jwasmVer")
        implementation("io.github.eutro.jwasm:jwasm-attrs:$jwasmVer")
        implementation("io.github.eutro.jwasm:jwasm-sexp:$jwasmVer")
        implementation(rootProject)
    }
}

dependencies {
    implementation("org.ow2.asm:asm:9.4")
    implementation("org.ow2.asm:asm-commons:9.4")
    implementation("org.ow2.asm:asm-analysis:9.4")
    implementation("org.ow2.asm:asm-util:9.4")

    implementation("io.github.eutro.jwasm:jwasm:$jwasmVer")
    implementation("io.github.eutro.jwasm:jwasm-tree:$jwasmVer")
    implementation("io.github.eutro.jwasm:jwasm-analysis:$jwasmVer")
    implementation("io.github.eutro.jwasm:jwasm-attrs:$jwasmVer")
}

sourceSets {
    create("runtime")
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

allprojects {
    tasks.test {
        useJUnitPlatform()
    }

    tasks.register<Jar>("sourceJar") {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    publishing {
        publications {
            register<MavenPublication>("maven") {
                from(components.named("java").get())
                artifact(tasks["sourceJar"])
            }
        }
    }
}
