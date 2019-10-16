import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    `java-gradle-plugin`
}


dependencies {
    implementation(gradleApi())
    implementation(localGroovy())

    implementation("com.github.jknack:handlebars:4.1.0")
    implementation("io.github.classgraph:classgraph:4.8.47")
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

