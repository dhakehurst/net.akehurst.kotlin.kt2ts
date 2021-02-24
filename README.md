[![Build Status](https://travis-ci.com/dhakehurst/net.akehurst.kotlin.kt2ts.svg?branch=master)](https://travis-ci.com/dhakehurst/net.akehurst.kotlin.kt2ts)
![Bintray](https://img.shields.io/bintray/v/dhakehurst/maven/net.akehurst.kotlin.kt2ts.svg)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/net/akehurst/kotlin/kt2ts/net.akehurst.kotlin.kt2ts.gradle.plugin/maven-metadata.xml.svg?colorB=007ec6&label=gradle%20plugin)](https://plugins.gradle.org/plugin/net.akehurst.kotlin.kt2ts)

# net.akehurst.kotlin.kt2ts
Typescript definition file generator for kotlin (js) classes

Kotlin is great. However, if you want to integrate it into an angular application (or other typescript) we need to generate the typescript declaration file and package.json. Neither of these is currently created by the kotlin JS compiler.

This plugin is intended for use in a kotlin multiplatform development. It uses JVM reflection over the kotlin-JVM module to generate the .d.ts file that is intended for use with the kotlin-js module.

The plugin addresses use cases such as:
1. generating .d.ts file for your own kotlin module
2. generating .d.ts file for a third-party module
3. building an angular application and integrating kotlin generated modules.

The full documentation is available here: https://www.itemis.com//en/kotlin-typescript-integration/

A blog post that discusses the issues this plugin tries to solve can be found here [https://medium.com/@dr.david.h.akehurst/building-applications-with-kotlin-and-typescript-8a165e76252c].

## Add the plugin

```
plugins {
    id("net.akehurst.kotlin.kt2ts") version("$version")
}
```

## configure the plugin for your own modules

```
// configure the kt2ts plugin
kt2ts {
    classPatterns.set(listOf(
            "com.example.my.module.common.*"
    ))
}
```

# Sponsors
This project is sponsored by [itemis](https://www.itemis.com/) – thank you!
