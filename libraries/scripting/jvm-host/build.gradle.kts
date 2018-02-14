/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */


import org.jetbrains.kotlin.gradle.dsl.Coroutines

apply {
    plugin("kotlin")
}

dependencies {
    compile(project(":kotlin-script-runtime"))
    compile(project(":kotlin-stdlib"))
    compile(project(":kotlin-scripting-common"))
    compile(project(":kotlin-scripting-jvm"))
    compileOnly(project(":compiler:cli"))
    compile(intellijCoreDep())
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

kotlin.experimental.coroutines = Coroutines.ENABLE
