package com.arthou.ntranslator.forge;

import com.arthou.ntranslator.NTranslator;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(NTranslator.MOD_ID)
public final class NexelForgeMod {
    public static NTranslator INSTANCE;

    public NexelForgeMod() {
        INSTANCE = new NTranslator();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ForgeClientBootstrap::init);
    }
}
