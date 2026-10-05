package com.arthou.ntranslator.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(BookEditScreen.class)
public interface BookEditScreenAccessor {
    @Accessor("pages")
    List<String> nexelGetPages();

    @Accessor("doneButton")
    Button nexelGetDoneButton();

    @Accessor("signButton")
    Button nexelGetSignButton();

    @Accessor("pageEdit")
    TextFieldHelper nexelGetPageEdit();

    @Accessor("currentPage")
    int nexelGetCurrentPage();

    @Invoker("clearDisplayCache")
    void nexelClearDisplayCache();
}
