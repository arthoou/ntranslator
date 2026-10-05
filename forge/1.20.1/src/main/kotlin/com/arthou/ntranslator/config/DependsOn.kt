package com.arthou.ntranslator.config

@Retention(AnnotationRetention.RUNTIME)
annotation class DependsOn(
    val configName: String
)
