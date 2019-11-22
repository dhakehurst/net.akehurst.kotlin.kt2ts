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

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import kotlin.reflect.*
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.valueParameters

data class Module(
        val group: String,
        val name: String
) {
    companion object {
        val all = mutableSetOf<Module>()
        fun fetchOrCreate(group: String, name: String): Module {
            var m = all.firstOrNull { it.group == group && it.name == name }
            if (null == m) {
                m = Module(group, name)
                all.add(m)
            }
            return m
        }
    }

    val alias: String
        get() {
            val moduleName = name //if (this == GenerateDeclarationsTask.KOTLIN_STDLIB_MODULE) name else name.substringBeforeLast('-')
            val a = moduleName.replace('-', '_')
            return "$${a}"
        }
    private val _namespaces = mutableMapOf<String, Namespace>()
    val namespaces: Map<String, Namespace> = _namespaces

    val dependencies: Set<Module>
        get() {
            return namespaces.values.flatMap { it.usedModules }.toSet()
        }

    fun fetchOrCreateNamespace(qualifiedName: String): Namespace {
        var ns = namespaces[qualifiedName]
        if (null == ns) {
            ns = Namespace(this, qualifiedName)
            _namespaces[qualifiedName] = ns
        }
        return ns
    }
}

data class Namespace(
        val module: Module,
        val qualifiedName: String
) {
    val usedModules = mutableSetOf<Module>()
    lateinit var content: List<KClass<*>>
}

open class GenerateDeclarationsTask : DefaultTask() {

    companion object {
        val INDENT = "  "
        val NAME = "generateTypescriptDefinitionFile"
        val KOTLIN_STDLIB_MODULE = Module.fetchOrCreate("", "kotlin")
        private val LOGGER = LoggerFactory.getLogger(GenerateDeclarationsTask::class.java)
    }

    /**
     * group name of the module to generate for [default project.group]
     */
    @get:Input
    var moduleGroup = project.objects.property(String::class.java)

    /**
     * name of the module to generate for [default project.name]
     */
    @get:Input
    var moduleName = project.objects.property(String::class.java)

    @get:Input
    var overwrite = project.objects.property(Boolean::class.java)

    @get:Input
    var localOnly = project.objects.property(Boolean::class.java)

    @get:Input
    var moduleOnly = project.objects.listProperty(String::class.java)

    @get:Input
    var localJvmName = project.objects.property(String::class.java)

    @get:Input
    var modulesConfigurationName = project.objects.property(String::class.java)

    @get:OutputDirectory
    var outputDirectory = project.objects.directoryProperty()

    @get:OutputFile
    var declarationsFile = project.objects.fileProperty()

    @get:Input
    var classPatterns = project.objects.listProperty(String::class.java)

    @get:Input
    @get:Optional
    var typeMapping = project.objects.mapProperty(String::class.java, String::class.java)

    //@get:Input
    //@get:Optional
    // var dependencies = project.objects.listProperty(String::class.java)

    @get:Input
    @get:Optional
    var moduleNameMap = project.objects.mapProperty(String::class.java, String::class.java)

    private var classModuleMap: MutableMap<String, Module> = mutableMapOf()

    init {
        this.group = "generate"
        this.description = "Generate typescript declaration file (*.d.ts) from kotlin classes"

        this.moduleGroup.convention(project.group as String)
        this.moduleName.convention(project.name)
        this.overwrite.convention(true)
        this.localOnly.convention(true)
        this.localJvmName.convention("jvm")
        this.modulesConfigurationName.convention("jvmRuntimeClasspath")
        val outDir = project.layout.buildDirectory.dir("tmp/jsJar/ts")
        this.outputDirectory.convention(outDir)
        this.declarationsFile.convention(outDir.get().file("${project.group}-${project.name}.d.ts"))
        this.typeMapping.convention(mapOf(
                "kotlin.reflect.KClass" to "any", //not sure what else to use!
                "kotlin.Unit" to "void",
                "kotlin.Any" to "any",
                "kotlin.Array" to "Array",
                "kotlin.CharSequence" to "string",
                "kotlin.Char" to "number",
                "kotlin.String" to "string",
                "kotlin.Number" to "number",
                "kotlin.Byte" to "number",
                "kotlin.Short" to "number",
                "kotlin.Int" to "number",
                "kotlin.Float" to "number",
                "kotlin.Double" to "number",
                "kotlin.Boolean" to "boolean",
                "kotlin.Throwable" to "Error",
                "kotlin.Exception" to "Error",
                "kotlin.RuntimeException" to "Error",
                "java.lang.Exception" to "Error",
                "java.lang.RuntimeException" to "Error"
        ))
    }

    @TaskAction
    internal fun exec() {
        LOGGER.info("Executing $NAME")
        val thisModule = Module.fetchOrCreate(this.moduleGroup.get(), this.moduleName.get())

        //val imports = listOf(KOTLIN_STDLIB_MODULE) + dependencies.get().map {
        //    val split = it.split(":")
        //    Module.fetchOrCreate(split[0], split[1])
        //}
        buildNamespaces(thisModule, this.classPatterns.get())
        val content = generateFileContent(thisModule)
        val of = this.declarationsFile.get().asFile
        this.generate(content, of)
    }

    private fun buildNamespaces(thisModule: Module, classPatterns: List<String>) {
        LOGGER.info("Scanning classpath")
        LOGGER.info("Including:")
        classPatterns.forEach {
            LOGGER.info("  ${it}")
        }

        createClassModuleMapping(thisModule) //TODO: this is not very efficient, stuff is scanned more than once, here and further on

        val clToGenerate = this.createClassLoaderForDecls()
        val clForAll = this.createClassLoaderForAll()
        val packages = mutableListOf<String>()
        val classes = mutableListOf<String>()
        classPatterns.forEach { pat ->
            if (pat.endsWith(".*")) {
                packages.add(pat.substringBeforeLast(".*"))
            } else {
                classes.add(pat)
            }
        }
        val cg = ClassGraph()//
                .ignoreParentClassLoaders()
                .overrideClassLoaders(clToGenerate)//
                .enableClassInfo()//
                //.verbose()//
                //.enableExternalClasses() //
                //.enableAnnotationInfo() //
                //.enableSystemPackages()//
                .whitelistPackages(*packages.toTypedArray())
                .whitelistClasses(*classes.toTypedArray())

        val scan = cg.scan()
        val classInfo = scan.allClasses.flatMap {
            listOf(it) + it.innerClasses
        }.toSet()

        LOGGER.info("Building Datatype model for ${classInfo.size} types")

        val comparator = Comparator { c1: ClassInfo, c2: ClassInfo ->
            when {
                c1 == c2 -> 0
                c1.getSuperclasses().contains(c2) -> 1
                c2.getSuperclasses().contains(c1) -> -1
                else -> 0
            }
        }
        val filtered = classInfo.filter {
            try {
                val kclass: KClass<*> = clForAll.loadClass(it.name).kotlin
                kclass.isAbstract //this will throw exception if kotlin reflection not possible
                when {
                    kclass.isCompanion -> false
                    else -> true

                }
            } catch (ex: Throwable) {
                LOGGER.info("Skipping Class ${it.name} because kotlin reflection not supported on it")
                false
            }
        }

        val sorted = filtered.sortedWith(comparator)
        val namespaceGroups = sorted.groupBy {
            if (it.isInnerClass) {
                it.name.substringBeforeLast("$") //TODO: handle more than one level of nesting (maybe just replace $ with . here
            } else {
                it.packageName
            }
        }
        namespaceGroups.map { (nsn, classes) ->
            val ns = thisModule.fetchOrCreateNamespace(nsn)
            ns.content = classes.map { clForAll.loadClass(it.name).kotlin }
            ns
        }
    }

    private fun generate(fileContent: String, outputFile: File) {
        LOGGER.info("Generating ", outputFile)
        // final DefaultMustacheFactory mf = new DefaultMustacheFactory();
        if (outputFile.exists() && overwrite.get().not()) {
            LOGGER.info("Skipping $outputFile as it exists, set overwrite=true to overwrite the file")
        } else {
            if (outputFile.exists()) {
                LOGGER.info("Overwriting existing $outputFile, set overwrite=false to keep the orginal")
            }
            try {
                LOGGER.info("Generating output")
                FileWriter(outputFile).use {
                    it.write(fileContent)
                }
            } catch (e: Exception) {
                LOGGER.error("Unable to generate", e)
            }
        }
    }

    private fun generateFileContent(thisModule: Module): String {
        val namespaceStr = thisModule.namespaces.values.map { generateNamespace(it) }.joinToString("\n")
        //do imports second, because generating the types in namespaces adds the usedModules/dependencies
        val importStr = thisModule.dependencies.map { generateImport(it) }.joinToString("\n")

        return "$importStr\n\n$namespaceStr"
    }

    private fun generateImport(module: Module): String {
        val alias = module.alias
        val group = module.group
        val name = module.name
        val file = if (group.isEmpty()) "$name" else "$group-$name"
        return "import * as $alias from '$file';"
    }

    private fun generateNamespace(namespace: Namespace): String {
        val name = namespace.qualifiedName
        val declarations = namespace.content.map { generateKClass(it) }.joinToString("\n").prependIndent(INDENT)
        return "declare namespace $name {\n$declarations\n}"
    }

    private fun generateKClass(kclass: KClass<*>): String {
        return when {
            kclass.java.isEnum -> generateEnum(kclass)
            else -> generateKClass1(kclass)
        }
    }

    private fun generateKClass1(kclass: KClass<*>): String {
        if (null == kclass.qualifiedName) {
            LOGGER.error("${kclass} has no qualified name, cannot generate declaration")
            return ""
        } else {
            val pkgName = kclass.qualifiedName!!.substringBeforeLast('.')
            val module = this.classModuleMap[kclass.qualifiedName!!] ?: Module.fetchOrCreate("unknown", kclass.qualifiedName!!)
            val namespace = module.fetchOrCreateNamespace(pkgName)
            val name = kclass.simpleName!!
            val properties = kclass.declaredMemberProperties.filter { it.visibility == KVisibility.PUBLIC }.map { generateProperty(it, namespace) }.joinToString("\n").prependIndent(INDENT)
            val methods = kclass.declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC }.map { generateMethod(it, namespace) }.joinToString("\n").prependIndent(INDENT)
            val typeParams = if (kclass.typeParameters.isEmpty()) {
                ""
            } else {
                val tp = kclass.typeParameters.map { it.name }.joinToString(",")
                "<$tp>"
            }
            val prefix = when {
                null != kclass.objectInstance -> "class ${name}_class"
                kclass.java.isInterface -> "interface $name$typeParams"
                kclass.isAbstract -> "abstract class $name$typeParams"
                else -> "class $name$typeParams"
            }
            val instance = if (null != kclass.objectInstance) {
                "let $name: ${name}_class"
            } else {
                ""
            }
            val extends = mutableListOf<String>()
            val implements = mutableListOf<String>()
            kclass.supertypes.forEach { stype ->
                val sclass = stype.classifier as KClass<*>
                if (sclass == Any::class) {
                    //donothing
                } else {
                    val type = this.generateType(stype, namespace)
                    when {
                        kclass.java.isInterface -> extends.add(type)
                        sclass.java.isInterface -> implements.add(type)
                        else -> extends.add(type)
                    }
                }
            }
            val extendsStr = if (extends.isEmpty()) "" else "extends ${extends.joinToString(",")}"
            val implementsStr = if (implements.isEmpty()) "" else "implements ${implements.joinToString(",")}"
            val res = "$prefix $extendsStr $implementsStr {\n$properties\n\n$methods\n}\n$instance"
            return res
        }
    }

    private fun generateProperty(property: KProperty<*>, owningTypePackage: Namespace): String {
        val name = property.name
        val type = generateType(property.returnType, owningTypePackage)
        return "$name: $type;"
    }

    private fun generateMethod(method: KFunction<*>, owningTypePackage: Namespace): String {
        val name = method.name
        val generic = if (method.typeParameters.isEmpty()) {
            ""
        } else {
            val tps = method.typeParameters.map { it.name }.joinToString(",")
            "<$tps>"
        }
        val parameters = method.valueParameters.mapIndexed { index: Int, it: KParameter -> generateParameter(it, index, owningTypePackage) }.joinToString(", ")
        val returnType = generateType(method.returnType, owningTypePackage)
        return "$name$generic($parameters): $returnType;"
    }

    private fun generateParameter(parameter: KParameter, index: Int, owningTypePackage: Namespace): String {
        val name = parameter.name ?: "arg$index"
        val type = generateType(parameter.type, owningTypePackage)
        return "$name:$type"
    }

    private fun generateType(ktype: KType, owningNamespace: Namespace): String {
        val kclass = ktype.classifier
        var qualifiedName = "unknown!"
        var signature = when (kclass) {
            is KClass<*> -> {
                when {
                    kclass.java.isArray -> {
                        val t = generateType(ktype.arguments.first().type!!, owningNamespace)
                        "$t[]"
                    }
                    else -> {
                        qualifiedName = kclass.qualifiedName!!
                        val pkgName = qualifiedName.substringBeforeLast('.')
                        val name = when {
                            pkgName == owningNamespace.qualifiedName -> kclass.simpleName!!
                            this.classModuleMap[qualifiedName] == owningNamespace.module -> qualifiedName
                            else -> {
                                val module = classModuleMap[qualifiedName] ?: Module.fetchOrCreate("unknown", qualifiedName)
                                owningNamespace.usedModules.add(module)
                                println(" adding $module to $owningNamespace")
                                "${module.alias}.$qualifiedName"
                            }
                        }
                        val typeArgs = if (kclass.typeParameters.isEmpty()) {
                            ""
                        } else {
                            val args = ktype.arguments.map {
                                if (null == it.type) { //if * projection
                                    "any"
                                } else {
                                    generateType(it.type!!, owningNamespace)
                                }
                            }.joinToString(",")
                            "<$args>"
                        }
                        "$name$typeArgs"
                    }
                }

            }
            is KTypeParameter -> {
                kclass.name
            }
            else -> {
                LOGGER.error("cannot handle property type ${ktype} using any")
                "any"
            }
        }

        val mappedName = this.typeMapping.get()[qualifiedName]
        return if (null == mappedName) {
            if (signature.contains("unknown!")) {
                "any /* $signature */"
            } else {
                signature
            }
        } else {
            LOGGER.debug("Mapping " + signature + " to " + mappedName)
            mappedName
        }
    }

    private fun generateEnum(kclass: KClass<*>): String {
        val name = kclass.simpleName!!
        val literals = kclass.java.enumConstants.map {
            (it as Enum<*>).name
        }.joinToString(", ").prependIndent(INDENT)
        return "enum $name {\n$literals\n}"
    }

    private fun createClassLoaderForAll(): URLClassLoader {
        val urls = mutableListOf<URL>()
        val localBuild = project.buildDir.resolve("classes/kotlin/${localJvmName.get()}/main")//project.tasks.getByName("jvm8MainClasses").outputs.files
        val localUrl = localBuild.absoluteFile.toURI().toURL() //URL("file://"+localBuild.absoluteFile+"/")
        urls.add(localUrl)
        val configurationName = modulesConfigurationName.get()
        val c = this.project.configurations.findByName(configurationName) ?: throw RuntimeException("Cannot find $configurationName configuration")
        //TODO: would be better to use the metadata modules and auto find the jvm modules to use from the metadata
        c.resolvedConfiguration.resolvedArtifacts.forEach { dep ->
            val file = dep.file
            try {
                LOGGER.info("For classpath adding url for $file")
                urls.add(file.toURI().toURL())
            } catch (e: MalformedURLException) {
                LOGGER.error("Unable to create url for $file", e)
            }
        }
        return URLClassLoader(urls.toTypedArray(), Thread.currentThread().contextClassLoader)
    }

    private fun createClassLoaderForDecls(): URLClassLoader {
        val urls = mutableListOf<URL>()
        val forModules = moduleOnly.get()
        val localBuild = project.buildDir.resolve("classes/kotlin/${localJvmName.get()}/main")//project.tasks.getByName("jvm8MainClasses").outputs.files
        val localUrl = localBuild.absoluteFile.toURI().toURL() //URL("file://"+localBuild.absoluteFile+"/")
        urls.add(localUrl)
        if (localOnly.get().not()) {
            val configurationName = modulesConfigurationName.get()
            val c = this.project.configurations.findByName(configurationName) ?: throw RuntimeException("Cannot find $configurationName configuration")
            c.resolvedConfiguration.resolvedArtifacts.forEach { dep ->
                if (forModules.isEmpty() || forModules.contains(dep.name)) {
                    val file = dep.file
                    try {
                        LOGGER.info("For declarations adding url for $file")
                        urls.add(file.toURI().toURL())
                    } catch (e: MalformedURLException) {
                        LOGGER.error("Unable to create url for $file", e)
                    }
                }
            }
        }
        return URLClassLoader(urls.toTypedArray(), Thread.currentThread().contextClassLoader)
    }

    private fun createClassModuleMapping(thisModule: Module) {
        //hard code kotlin built-in types because scanning kotlin-stdlib.jar does not find them
        KOTLIN_STDLIB_MODULE.fetchOrCreateNamespace("kotlin")
        this.classModuleMap[Throwable::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Exception::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[RuntimeException::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Function::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        for (n in 1..25) {
            this.classModuleMap["kotlin.Function$n"] = KOTLIN_STDLIB_MODULE
        }
        this.classModuleMap[Any::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        //this.classModuleMap[Array::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Collection::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[List::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Set::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Map::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Comparable::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[String::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[CharSequence::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Char::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Boolean::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Number::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Byte::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Short::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Int::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Long::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Float::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Double::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        //TODO: other built in types ! i.e. CharArray etc

        val localBuild = project.buildDir.resolve("classes/kotlin/${localJvmName.get()}/main")
        val localUrl = localBuild.absoluteFile.toURI().toURL()

        fetchClassesFor(localUrl).forEach {
            this.classModuleMap[it] = thisModule
        }

        val configurationName = modulesConfigurationName.get()
        val c = this.project.configurations.findByName(configurationName) ?: throw RuntimeException("Cannot find $configurationName configuration")
        c.resolvedConfiguration.resolvedArtifacts.forEach { dep ->
            val dn = when {
                dep.name.endsWith(localJvmName.get()) -> dep.name.substringBeforeLast("-")
                else -> dep.name
            }
            //kotlin js needs to find stuff in module 'kotlin' not 'kotlin-stdlib', so adding explicit check,
            val ref = "${dep.moduleVersion.id.group}:${dep.name}"
            println("module for $ref")
            val module = when {
                dep.name.startsWith("kotlin-stdlib") -> {
                    KOTLIN_STDLIB_MODULE
                }
                moduleNameMap.get().containsKey(ref) -> Module.fetchOrCreate("", moduleNameMap.get().get(ref)!!)
                else -> Module.fetchOrCreate(dep.moduleVersion.id.group, dn)
            }
            fetchClassesFor(dep.file.toURI().toURL()).forEach {
                this.classModuleMap[it] = module
            }
        }
    }

    private fun fetchClassesFor(url: URL): List<String> {
        val cl = URLClassLoader(arrayOf(url))
        val scan = ClassGraph()//
                .ignoreParentClassLoaders()
                .overrideClassLoaders(cl)//
                .enableClassInfo()//
                .scan()
        val classInfo = scan.allClasses
        return classInfo.flatMap {
            listOf(it.name) + it.innerClasses.map {
                it.name.replace("$", ".")
            }
        }
    }

}
