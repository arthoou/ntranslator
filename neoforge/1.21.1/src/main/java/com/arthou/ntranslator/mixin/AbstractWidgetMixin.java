package com.arthou.ntranslator.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.arthou.ntranslator.duck.ScrollableWidget;

@Mixin(AbstractWidget.class)
public class AbstractWidgetMixin implements ScrollableWidget {
    @Unique private int initialX;
    @Unique private int initialY;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ntranslator$setInitialPositions(int x, int y, int width, int height, Component message, CallbackInfo ci) {
        this.initialX = x;
        this.initialY = y;
    }


    @Override
    public int ntranslator$getInitialX() {
        return this.initialX;
    }

    @Override
    public int ntranslator$getInitialY() {
        return this.initialY;
    }

    @Override
    public void ntranslator$updateInitialPosition() {
        AbstractWidget widget = (AbstractWidget) (Object) this;
        this.initialX = widget.getX();
        this.initialY = widget.getY();
    }
}
