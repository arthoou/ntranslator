package com.arthou.ntranslator.config

@Retention(AnnotationRetention.RUNTIME)
annotation class FloatRange(
    val from: Float,
    val to: Float,
    val increment: Float
)
