import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    `java-gradle-plugin`
    `maven-publish`
}


dependencies {
    implementation(gradleApi())
    //implementation(localGroovy())

    //implementation("com.google.code.gson:gson:2.8.6")
    implementation("io.github.classgraph:classgraph:4.8.47")
    // implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.13.0")
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
            id = "net.akehurst.kotlin.kt2ts-plugin"
            implementationClass = "net.akehurst.kotlin.kt2ts.plugin.gradle.GeneratorPlugin"
        }
    }
}

