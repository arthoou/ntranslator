package com.arthou.ntranslator.forge;

import com.arthou.ntranslator.client.books.BookTranslationManager;
import com.arthou.ntranslator.client.signs.SignTranslationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SignBlock;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ScreenOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;


public final class ForgeClientBootstrap {
    private static boolean initialized;

    private ForgeClientBootstrap() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        MinecraftForge.EVENT_BUS.addListener(ForgeClientBootstrap::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientBootstrap::onScreenInit);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientBootstrap::onScreenOpen);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientBootstrap::onKeyPressed);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientBootstrap::onCharacterTyped);
    }

    private static boolean isBookScreen(Screen screen) {
        return screen instanceof BookViewScreen || screen instanceof BookEditScreen;
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getWorld().isClientSide()) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!(event.getWorld().getBlockState(event.getPos()).getBlock() instanceof SignBlock)) {
            return;
        }

        if (event.getPlayer().isShiftKeyDown()) {
            SignTranslationManager.INSTANCE.allowVanillaEdit();
            return;
        }

        if (event.getItemStack().getItem() instanceof BlockItem
            && ((BlockItem) event.getItemStack().getItem()).getBlock() instanceof SignBlock) {
            SignTranslationManager.INSTANCE.allowVanillaEdit();
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        SignTranslationManager.INSTANCE.openTranslatedSign(event.getPos());
    }

    private static void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
        Screen screen = event.getScreen();

        if (SignTranslationManager.INSTANCE.shouldReplaceSignScreen(screen)) {
            SignTranslationManager.INSTANCE.reopenPendingTranslation();
            return;
        }

        if (screen instanceof BookViewScreen) {
            BookTranslationManager.INSTANCE.installButton((BookViewScreen) screen, event::addListener);
            return;
        }

        if (screen instanceof BookEditScreen) {
            BookTranslationManager.INSTANCE.installWritableButton((BookEditScreen) screen, event::addListener);
        }
    }

    private static void onScreenOpen(ScreenOpenEvent event) {
        Screen current = Minecraft.getInstance().screen;
        if (isBookScreen(current) && !isBookScreen(event.getScreen())) {
            BookTranslationManager.INSTANCE.stopTts();
        }
    }

    private static void onKeyPressed(ScreenEvent.KeyboardKeyPressedEvent.Pre event) {
        Screen screen = event.getScreen();

        if (isBookScreen(screen) && event.getKeyCode() == 256) {
            BookTranslationManager.INSTANCE.stopTts();
            return;
        }

        if (screen instanceof BookEditScreen
            && BookTranslationManager.INSTANCE.isWritableTranslated((BookEditScreen) screen)) {
            event.setCanceled(true);
        }
    }

    private static void onCharacterTyped(ScreenEvent.KeyboardCharTypedEvent.Pre event) {
        Screen screen = event.getScreen();

        if (screen instanceof BookEditScreen
            && BookTranslationManager.INSTANCE.isWritableTranslated((BookEditScreen) screen)) {
            event.setCanceled(true);
        }
    }
}
