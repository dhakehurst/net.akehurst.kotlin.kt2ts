package net.akehurst.kotlin.kt2ts.plugin.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

open class GenerateDynamicRequire : DefaultTask() {

    companion object {
        val NAME = "generateFunctionForDynamicRequire"
    }

    @get:InputDirectory
    @Optional
    val nodeModulesDirectory = project.objects.directoryProperty()

    /**
     * list of modules that are imported/required dynamically.
     * i.e. those that have been registered for import vi 'net.akehurst.kotlinx.reflect.ModuleRegistry.register'
     */
    @get:Input
    val dynamicImport = project.objects.listProperty(String::class.java)

    @get:InputDirectory
    @Optional
    val tgtDirectory = project.objects.directoryProperty()

    init {
        tgtDirectory.convention(this.nodeModulesDirectory.dir("net.akehurst.kotlinx-kotlinx-reflect"))
    }

    @TaskAction
    internal fun exec() {
        val cases = this.dynamicImport.get().map { gav ->
            val split = gav.split(':')
            val group = split[0]
            val name = split[1]
            "case '$group:$name': return require('$group-$name')"
        }.joinToString("\n")
        val outFile = this.tgtDirectory.get().file("generatedRequire.js").asFile
        outFile.printWriter().use { out ->
            val js = """
"use strict";

function generatedRequire(moduleName) {
    switch(moduleName) {
        $cases
    }
}

module.exports = generatedRequire;
            """.trimIndent()
            out.println(js)
        }
    }

}