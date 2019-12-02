/**
 * Copyright (C) 2018 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.kotlin.kt2ts.plugin.gradle

import org.gradle.api.Plugin
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsSetupTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnSetupTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnSimple

class GeneratorPlugin : Plugin<ProjectInternal> {

    override fun apply(project: ProjectInternal) {
        project.pluginManager.apply(BasePlugin::class.java)
        val ext = project.extensions.create<GeneratorPluginExtension>(GeneratorPluginExtension.NAME, GeneratorPluginExtension::class.java, project)

        val ngKotlinConfig = project.configurations.create(ext.ngConfigurationName.get()) {
            it.attributes {
                it.attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
                it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, KotlinUsages.KOTLIN_RUNTIME))
            }
        }

        project.gradle.projectsEvaluated {
            if (ext.ngSrcDirectory.isPresent) {
                val ngSrcDir = project.file(ext.ngSrcDirectory.get())
                project.tasks.create(NodeJsSetupTask.NAME, NodeJsSetupTask::class.java) {
                    it.group = "nodejs"
                }
                project.tasks.create(YarnSetupTask.NAME, YarnSetupTask::class.java) {
                    it.group = "nodejs"
                    if (it.destination.exists()) {
                        // assume that already downloaded
                    } else {
                        it.setup()
                    }
                }
                // use yarn to install the node_modules required by the angular code
                project.tasks.create("yarn_install") {
                    it.group = "angular"
                    it.dependsOn(NodeJsSetupTask.NAME, YarnSetupTask.NAME)
                    it.doLast {
                        YarnSimple.yarnExec(project.rootProject, ngSrcDir, "yarn_install", "install", "--cwd", "--no-bin-links")
                    }
                }
                project.tasks.create(UnpackJsModulesTask.NAME, UnpackJsModulesTask::class.java) { tsk ->
                    tsk.dependsOn(ngKotlinConfig, "yarn_install")
                    tsk.moduleNameMap.set(ext.moduleNameMap)
                    tsk.nodeModulesDirectory.set(ext.nodeModulesDirectory)
                    tsk.unpackConfigurationName.set(ext.ngConfigurationName)
                    tsk.excludeModules.set(ext.excludeModules)
                }
                project.tasks.create(AddKotlinStdlibDeclarationsTask.NAME, AddKotlinStdlibDeclarationsTask::class.java) { tsk ->
                    tsk.outputDirectory.set(ext.kotlinStdlibJsDirectory)
                }
                project.tasks.create("ng_build") {
                    it.group = "angular"
                    it.dependsOn("unpack_kotlin_js")
                    it.dependsOn("addKotlinStdlibDeclarations")
                    it.doLast {
                        val additionalArgs = ext.ngBuildAdditionalArguments.get().toTypedArray()
                        YarnSimple.yarnExec(project.rootProject, project.file(ext.ngSrcDirectory.get()), "ng_build", "run", "ng", "build", "--outputPath=${ext.ngOutDirectory.get()}/dist", *additionalArgs)
                    }
                }

                project.tasks.getByName("jsProcessResources").dependsOn("ng_build")

                if (ext.dynamicImport.isPresent && ext.dynamicImport.get().isNotEmpty()) {
                    project.tasks.create("generate_function_for_dynamic_require") {
                        it.group = "generate"
                        it.dependsOn(UnpackJsModulesTask.NAME)
                        it.doLast {
                            val options = ext.dynamicImport.get().map { gav ->
                                val split = gav.split(':')
                                val group = split[0]
                                val name = split[1]
                                "case '$group:$name': return require('$group-$name')"
                            }.joinToString("\n")
                            val outFile = ext.nodeModulesDirectory.get().dir("net.akehurst.kotlinx-kotlinx-reflect").file("generatedRequire.js").asFile
                            outFile.printWriter().use { out ->
                                val js = """
                                    "use strict";
                                    
                                    function generatedRequire(moduleName) {
                                        switch(moduleName) {
                                            $options
                                        }
                                    }
                                    
                                    module.exports = generatedRequire;
                                """.trimIndent()
                                out.println(js)
                            }
                        }
                    }
                    project.tasks.getByName("ng_build").dependsOn("generate_function_for_dynamic_require")
                }
            }
            ext.generateThirdPartyModules.forEach { cfg ->
                //TODO: '${cfg.moduleName.get()}' might not be unque!
                project.tasks.create("generateDeclarationsFor_${cfg.moduleName.get()}", GenerateDeclarationsTask::class.java) { tsk ->
                    tsk.group = "generate"
                    tsk.dependsOn("unpack_kotlin_js")
                    tsk.localJvmName.set(ext.localJvmName)
                    tsk.moduleGroup.set(cfg.moduleGroup)
                    tsk.moduleName.set(cfg.moduleName)
                    tsk.classPatterns.set(cfg.classPatterns)
                    //overwrite.set(false)
                    tsk.localOnly.set(false)
                    tsk.moduleOnly.set(cfg.includeOnly)
                    //modulesConfigurationName.set("jvm8RuntimeClasspath")
                    //tsk.declarationsFile.set(file("${ngSrcDir}/node_modules/${tgtName}/${dep.moduleVersion.id.group}-${dn}-js.d.ts"))
                    tsk.declarationsFile.set(ext.nodeModulesDirectory.flatMap {
                        it.dir(cfg.tgtName).map { it.file("${tsk.moduleGroup.get()}-${tsk.moduleName.get()}-js.d.ts") }
                    })
                }
                project.tasks.create("ensurePackageJsonFor_${cfg.moduleName.get()}", GeneratePackageJsonTask::class.java) { tsk ->
                    tsk.group = "generate"
                    tsk.dependsOn("generateDeclarationsFor_${cfg.moduleName.get()}")
                    tsk.packageJsonDir.set(ext.nodeModulesDirectory.flatMap {
                        it.dir(cfg.tgtName)
                    })
                    tsk.moduleGAV.set(cfg.name)
                    tsk.mainFileName.set(cfg.mainFileName)
                }
            }
        }
        // generate own .d.ts
        project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.sourceSets?.findByName(ext.jsSourceSetName.get())?.resources?.srcDir(ext.tsdOutputDirectory)

        project.tasks.create(GeneratePackageJsonTask.NAME, GeneratePackageJsonTask::class.java) { tsk ->
            tsk.packageJsonDir.set(ext.tsdOutputDirectory)
        }
        project.tasks.create(GenerateDeclarationsTask.NAME, GenerateDeclarationsTask::class.java) { gt ->
            //TODO: how to set dependsOn to "${ext.jvmName}MainClasses" ?
            val jvmMainClasses = ext.localJvmName.map { it + "MainClasses" }
            gt.dependsOn(GeneratePackageJsonTask.NAME, jvmMainClasses)
            gt.overwrite.set(ext.overwrite)
            gt.localOnly.set(ext.localOnly)
            gt.moduleOnly.set(ext.includeOnly)
            gt.declarationsFile.set(ext.declarationsFile)
            gt.localJvmName.set(ext.localJvmName)
            gt.classPatterns.set(ext.classPatterns)
            gt.typeMapping.set(ext.typeMapping)
            gt.outputDirectory.set(ext.tsdOutputDirectory)
            gt.declarationsFile.set(ext.declarationsFile)
            //gt.dependencies = ext.dependencies
            gt.moduleNameMap.set(ext.moduleNameMap)
        }


        project.tasks.getByName("jsJar").dependsOn(GenerateDeclarationsTask.NAME)
    }

}
