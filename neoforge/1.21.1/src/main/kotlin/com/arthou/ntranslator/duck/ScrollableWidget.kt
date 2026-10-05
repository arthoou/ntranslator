package com.arthou.ntranslator.duck

interface ScrollableWidget {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("ntranslator\$getInitialX")
    val initialX: Int

    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("ntranslator\$getInitialY")
    val initialY: Int

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("ntranslator\$updateInitialPosition")
    fun updateInitialPosition()
}
