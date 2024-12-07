plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.5.31"
}
repositories {
    mavenCentral()
}

val jacksonVersion = "2.13.4"

dependencies {
    implementation(gradleApi())
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.31")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    //implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    //implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    //implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.jr:jackson-jr-objects:$jacksonVersion")
    implementation("com.fasterxml.jackson.jr:jackson-jr-stree:$jacksonVersion")
    implementation("com.electronwill.night-config:toml:3.6.3")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))

sourceSets {
    main.get().java.srcDirs("src/main/kotlin")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

gradlePlugin {
    plugins {
        create("mcgenPlugin") {
            id = "szewek.mcgen.mcgen-plugin"
            implementationClass = "szewek.mcgen.MCGenPlugin"
        }
    }
}
