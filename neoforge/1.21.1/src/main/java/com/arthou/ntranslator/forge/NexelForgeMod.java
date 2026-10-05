package com.arthou.ntranslator.forge;

import com.arthou.ntranslator.NTranslator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;

@Mod(NTranslator.MOD_ID)
public final class NexelForgeMod {
    public static NTranslator INSTANCE;

    public NexelForgeMod() {
        INSTANCE = new NTranslator();
        if (FMLLoader.getDist() == Dist.CLIENT) {
            ForgeClientBootstrap.init();
        }
    }
}
