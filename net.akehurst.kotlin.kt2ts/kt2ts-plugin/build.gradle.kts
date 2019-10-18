import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    `java-gradle-plugin`
}


dependencies {
    implementation(gradleApi())
    implementation(localGroovy())

    implementation("com.github.jknack:handlebars:4.1.0")
    implementation("io.github.classgraph:classgraph:4.8.47")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.13.0")
}

sourceSets["main"].withConvention(KotlinSourceSet::class) {
    kotlin.srcDir("$buildDir/generated/kotlin")
}



gradlePlugin {
    plugins {
        create("kt2ts") {
            id = "net.akehurst.kotlin.kt2ts-plugin"
            implementationClass = "net.akehurst.kotlin.kt2ts.plugin.gradle.GeneratorPlugin"
        }
    }
}

