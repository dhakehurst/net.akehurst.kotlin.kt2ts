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

import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import com.github.jknack.handlebars.io.FileTemplateLoader
import io.github.classgraph.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.lang.RuntimeException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import kotlin.Comparator


open class GenerateTask : DefaultTask() {

    companion object {
        val NAME = "generateTypescriptDefinitionFile"
        private val LOGGER = LoggerFactory.getLogger(GenerateTask::class.java)
    }

    @get:Input
    var configurationName = "commonMainImplementation"

    @get:Input
    var templateFileName = "template.hbs"

    @get:InputDirectory
    @get:Optional
    var templateDir : File? = null

    @get:OutputFile
    @get:Optional
    var outputFile : File? = null

    @get:Input
    var classPatterns = listOf<String>()

    @get:Input
    var typeMapping = mapOf<String, String>()

    init {
        this.group = "build"
        this.description = "Generate .d.ts definitions file from kotlin classes"
    }

    @TaskAction
    internal fun exec() {
        LOGGER.info("Executing " + GenerateTask.NAME)

        val model = this.createModel(this.classPatterns)

        this.generate(model, this.templateDir, this.outputFile)

    }

    private fun generate(model: Map<String, Any>, templateDir: File?, outputFile: File?) {
        super.getLogger().info("Generating ", outputFile)
        // final DefaultMustacheFactory mf = new DefaultMustacheFactory();


        val ftl = when {
            null!=templateDir -> {
                LOGGER.info("Using template directory " + templateDir)
                FileTemplateLoader(templateDir, "")
            }
            else -> {
                LOGGER.info("Using default template")
                ClassPathTemplateLoader("handlebars")
            }
        }
        val hb = Handlebars(ftl)

        try {
            LOGGER.info("Compiling template " + templateFileName)
            val t = hb.compile(templateFileName)

            LOGGER.info("Generating output")
            val writer = FileWriter(outputFile!!)

            val ctx = Context.newBuilder(model).resolver(MapValueResolver.INSTANCE).build()

            t.apply(ctx, writer)
            writer.close()
        } catch (e: Exception) {
            LOGGER.error("Unable to generate", e)
        }

    }

    private fun createModel(classPatterns: List<String>): Map<String, Any> {
        val model = mutableMapOf<String, Any>()
        val datatypes = mutableListOf<Map<String, Any>>()
        model["datatype"] = datatypes

        LOGGER.info("Scanning classpath")
        val cl = this.createClassLoader()
        val scan = ClassGraph()//
                .addClassLoader(cl)//
                .enableAllInfo()//
                // .verbose()//
                .enableExternalClasses() //
                .enableAnnotationInfo() //
                //.enableSystemPackages()//
                .whitelistPackages(*classPatterns.toTypedArray())//
                .scan()

        LOGGER.debug("Found " + scan.allClasses.size)
        LOGGER.info("Building Datatype model")
        val classInfo = scan.allClasses

        val comparator = Comparator { c1: ClassInfo, c2: ClassInfo ->
            when {
                c1.getSuperclasses().contains(c2) -> 1
                c2.getSuperclasses().contains(c1) -> -1
                else -> 0
            }
        }
        val sorted = classInfo.sortedWith(comparator)

        for (cls in sorted) {
            GenerateTask.LOGGER.debug("Adding Class " + cls.getName())
            val dtModel = mutableMapOf<String, Any>()
            datatypes.add(dtModel)
            dtModel["name"] = cls.getName().substring(cls.getName().lastIndexOf(".") + 1)
            dtModel["fullName"] = cls.getName()
            dtModel["isInterface"] = cls.isInterface()
            dtModel["isAbstract"] = cls.isAbstract()

            val extends_ = ArrayList<Map<String, Any>>()
            dtModel["extends"] = extends_
            if (null != cls.getSuperclass() ) {
                extends_.add(this.createPropertyType(cls.getSuperclass(), ArrayList<TypeArgument>(), false))
            }
            val implements_ = ArrayList<Map<String, Any>>()
            dtModel["implements"] = implements_
            for (i in cls.getInterfaces()) {
                //if (i.hasAnnotation(Datatype::class.java!!.getName())) {
                    implements_.add(this.createPropertyType(i, ArrayList<TypeArgument>(), false))
                //}
            }

            val property = mutableListOf<Map<String, Any>>()
            dtModel["property"] = property
            for (mi in cls.getMethodInfo()) {
                    if (mi.parameterInfo.size === 0 && mi.getName().startsWith("get")) {
                        val meth = HashMap<String, Any>()
                        meth["name"] = this.createMemberName(mi.getName())
                        val propertyTypeSig = mi.getTypeSignatureOrTypeDescriptor().getResultType()
                        meth["type"] = this.createPropertyType(propertyTypeSig, true)
                        property.add(meth)
                    }
            }
        }

        return model
    }

    private fun createClassLoader(): URLClassLoader {
        val urls = mutableListOf<URL>()

        val c = this.project.configurations.findByName(configurationName) ?: throw RuntimeException("Cannot find $configurationName configuration")
        c.isCanBeResolved = true
        for (file in c.resolve()) {
            try {
                LOGGER.debug("Adding url for $file")
                urls.add(file.toURI().toURL())
            } catch (e: MalformedURLException) {
                LOGGER.error("Unable to create url for $file", e)
            }

        }

        return URLClassLoader(urls.toTypedArray(), Thread.currentThread().contextClassLoader)
    }

    private fun createMemberName(methodName: String): String {
        return methodName.substring(3, 4).toLowerCase() + methodName.substring(4)
    }

    private fun createPropertyType(ci: ClassInfo, tArgs: List<TypeArgument>, isRef: Boolean): MutableMap<String, Any> {
        val mType = mutableMapOf<String, Any>()
        val fullName = ci.getName()
        mType["fullName"] = fullName
        mType["name"] = fullName.substring(fullName.lastIndexOf('.') + 1)
        if (ci.implementsInterface("java.util.Collection")) {
            mType["isCollection"] = true
            mType["isOrdered"] = "java.util.List" == ci.getName()

            val et = this.createPropertyType(tArgs[0].getTypeSignature(), false) // what is isRef here!
            LOGGER.info("Collection with elementType " + tArgs[0].getTypeSignature())
            mType["elementType"] = et

        } else if (ci.isEnum()) {
            mType["isEnum"] = true
        }
        mType["isReference"] = isRef
        return mType
    }

    private fun createPropertyType(typeSig: TypeSignature, isRef: Boolean): Map<String, Any> {
        var mType: MutableMap<String, Any> = mutableMapOf()
        if (typeSig is BaseTypeSignature) {
            val bts = typeSig as BaseTypeSignature
            mType["name"] = bts.getTypeStr()
            mType["fullName"] = bts.getTypeStr()
        } else if (typeSig is ArrayTypeSignature) {
            val rts = typeSig as ArrayTypeSignature
            mType["name"] = "array"
            mType["fullName"] = "array"
            mType["isCollection"] = true
            mType["elementType"] = this.createPropertyType(rts.getElementTypeSignature(), isRef)
        } else if (typeSig is ClassRefTypeSignature) {
            val cts = typeSig as ClassRefTypeSignature
            if (null == cts.getClassInfo()) {
                val fullName = cts.getFullyQualifiedClassName()
                mType["fullName"] = fullName
                mType["name"] = fullName.substring(fullName.lastIndexOf('.') + 1)
                LOGGER.error("Unable to find class info for " + cts.getFullyQualifiedClassName())
            } else {
                mType = this.createPropertyType(cts.getClassInfo(), cts.getTypeArguments(), isRef)
            }
        } else {
        }

        val mappedName = this.typeMapping[mType["fullName"]]
        if (null == mappedName) {
            return mType
        } else {
            LOGGER.debug("Mapping " + mType["name"] + " to " + mappedName)
            mType["name"] = mappedName
            return mType
        }

    }

}
