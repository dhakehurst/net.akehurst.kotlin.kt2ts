import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.12.0"
}


dependencies {
    implementation(gradleApi())
    implementation(localGroovy())

    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.71") //version must match version used by gradle
    implementation("io.github.classgraph:classgraph:4.8.47")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.13.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

sourceSets["main"].withConvention(KotlinSourceSet::class) {
    kotlin.srcDir("$buildDir/generated/kotlin")
}


val sourcesJar by tasks.creating(Jar::class) {
    dependsOn(JavaPlugin.CLASSES_TASK_NAME)
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

artifacts {
    add("archives", sourcesJar)
}

publishing {
    publications {
        create<MavenPublication>("default") {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
}



gradlePlugin {
    plugins {
        create("kt2ts") {
            id = "net.akehurst.kotlin.kt2ts"
            implementationClass = "net.akehurst.kotlin.kt2ts.plugin.gradle.GeneratorPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/dhakehurst/net.akehurst.kotlin.kt2ts"
    vcsUrl = "https://github.com/dhakehurst/net.akehurst.kotlin.kt2ts.git"

    (plugins) {

        // first plugin
        "kt2ts" {
            // id is captured from java-gradle-plugin configuration
            displayName = "Kotlin to Typescript declarations"
            description = "Generate Typescript declarations for Kotlin modules"
            tags = listOf("kotlin", "typescript", "kt2ts")
        }
    }

}