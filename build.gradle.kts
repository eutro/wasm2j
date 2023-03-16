plugins {
    `java-library`
    `maven-publish`
}

allprojects {
    apply<JavaLibraryPlugin>()
    apply<MavenPublishPlugin>()

    group = "io.github.eutro.jwasm"
    version = "${properties["ver_major"]}.${properties["ver_minor"]}.${properties["ver_patch"]}"
    val phase = properties["ver_phase"]
    if (phase != null) version = "$version-$phase"

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

val jwasmVer = properties["jwasm_version"]

allprojects {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.eutro.dev/releases")
        }
        mavenLocal()
    }

    dependencies {
        implementation("org.jetbrains:annotations:23.1.0")

        testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

        testImplementation("io.github.eutro.jwasm:jwasm-sexp:$jwasmVer")
        testImplementation("io.github.eutro.jwasm:jwasm-test:$jwasmVer")
    }
}

project(":wasm2j-api") {
    dependencies {
        implementation("org.ow2.asm:asm:9.4")
        implementation("org.ow2.asm:asm-tree:9.4")
        testImplementation("org.ow2.asm:asm-util:9.4")

        implementation("io.github.eutro.jwasm:jwasm:$jwasmVer")
        implementation("io.github.eutro.jwasm:jwasm-tree:$jwasmVer")
        implementation("io.github.eutro.jwasm:jwasm-analysis:$jwasmVer")
        implementation("io.github.eutro.jwasm:jwasm-sexp:$jwasmVer")
        implementation(project(":wasm2j-core"))
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
        implementation(project(":wasm2j-core"))
        implementation(project(":wasm2j-api"))
    }
}

project(":wasm2j-core") {
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
}

project(":wasm2j-wasi") {
    dependencies {
        implementation("org.ow2.asm:asm:9.4")
        implementation("org.ow2.asm:asm-tree:9.4")
        testImplementation("org.ow2.asm:asm-util:9.4")

        implementation("io.github.eutro.jwasm:jwasm-tree:$jwasmVer")
        implementation(project(":wasm2j-api"))
    }
}

val javadocModules = listOf(":wasm2j-core", ":wasm2j-embed", ":wasm2j-api", ":wasm2j-wasi")

tasks.javadoc {
    setDestinationDir(file("docs"))
    val javadocTasks = javadocModules.map { project(it).tasks.javadoc.get() }
    source = files(*javadocTasks.flatMap { it.source }.toTypedArray()).asFileTree
    classpath = files(*javadocTasks.flatMap { it.classpath }.toTypedArray())
    (options as StandardJavadocDocletOptions).run {
        locale("en")
        links(
            "https://docs.oracle.com/javase/8/docs/api",
            "https://asm.ow2.io/javadoc/",
            "https://eutro.github.io/jwasm",
        )
        group("Core", "io.github.eutro.wasm2j.core*")
        group("API", "io.github.eutro.wasm2j.api*")
        group("Embedding", "io.github.eutro.wasm2j.embed*")
    }
}

defaultTasks("build", "javadoc")

allprojects {
    tasks.test {
        useJUnitPlatform()
    }

    tasks.register<Jar>("sourceJar") {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    publishing {
        repositories {
            maven {
                name = "eutroDev"
                val repo = if (properties["ver_phase"] == "SNAPSHOT") "snapshots" else "release"
                url = uri("https://maven.eutro.dev/${repo}")
                credentials(PasswordCredentials::class)
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
        publications {
            register<MavenPublication>("maven") {
                from(components.named("java").get())
                artifact(tasks["sourceJar"])
            }
        }
    }
}
