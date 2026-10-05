package com.arthou.ntranslator.config

@Retention(AnnotationRetention.RUNTIME)
annotation class IntRange(
    val from: Int,
    val to: Int,
    val increment: Int
)
