package com.arthou.ntranslator.network.payloads

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation

interface LegacyPayload {
    val id: ResourceLocation
    fun write(buf: FriendlyByteBuf)
}
