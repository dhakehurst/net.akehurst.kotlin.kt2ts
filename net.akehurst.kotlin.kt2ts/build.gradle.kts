/**
 * Copyright (C) 2019 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version ("1.4.32") apply false //version must match version used by gradle, change also in gradle.properties
}

allprojects {

    val version_project: String by project
    val group_project = "${rootProject.name}"

    group = group_project
    version = version_project

    buildDir = File(rootProject.projectDir, ".gradle-build/${project.name}")

}

fun getProjectProperty(s: String) = project.findProperty(s) as String?


subprojects {

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    repositories {
        mavenCentral()
        jcenter()
    }

    val now = Instant.now()
    fun fBbuildStamp(): String {
        return DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC")).format(now)
    }

    fun fBuildDate(): String {
        return DateTimeFormatter.ofPattern("yyyy-MMM-dd").withZone(ZoneId.of("UTC")).format(now)
    }

    fun fBuildTime(): String {
        return DateTimeFormatter.ofPattern("HH:mm:ss z").withZone(ZoneId.of("UTC")).format(now)
    }
    tasks.register<Copy>("generateFromTemplates") {
        val templateContext = mapOf(
                "version" to project.version,
                "buildStamp" to fBbuildStamp(),
                "buildDate" to fBuildDate(),
                "buildTime" to fBuildTime()
        )
        inputs.properties(templateContext) // for gradle up-to-date check
        from("src/template/kotlin")
        into("$buildDir/generated/kotlin")
        expand(templateContext)
    }
    tasks.getByName("compileKotlin") {
        dependsOn("generateFromTemplates")
    }

    dependencies {
        "implementation"(kotlin("test-junit"))
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
}