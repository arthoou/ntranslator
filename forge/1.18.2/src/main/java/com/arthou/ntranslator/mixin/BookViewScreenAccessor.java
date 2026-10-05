package com.arthou.ntranslator.mixin;

import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BookViewScreen.class)
public interface BookViewScreenAccessor {
    @Accessor("bookAccess")
    BookViewScreen.BookAccess nexelGetBookAccess();

    @Accessor("currentPage")
    int nexelGetCurrentPage();

    @Invoker("setBookAccess")
    void nexelSetBookAccess(BookViewScreen.BookAccess bookAccess);

    @Invoker("setPage")
    boolean nexelSetPage(int page);
}
