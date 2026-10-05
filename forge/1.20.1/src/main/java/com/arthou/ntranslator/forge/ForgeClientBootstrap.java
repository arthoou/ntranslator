package com.arthou.ntranslator.forge;

import com.arthou.ntranslator.client.books.BookTranslationManager;
import com.arthou.ntranslator.client.signs.SignTranslationManager;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SignBlock;
import net.minecraftforge.client.event.ScreenEvent;
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
        ClientTickEvent.CLIENT_POST.register(minecraft -> {
            BookTranslationManager.INSTANCE.tick(minecraft);
            SignTranslationManager.INSTANCE.tick();
        });
        MinecraftForge.EVENT_BUS.addListener(ForgeClientBootstrap::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientBootstrap::onScreenInit);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientBootstrap::onScreenOpening);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientBootstrap::onScreenClosing);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientBootstrap::onKeyPressed);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientBootstrap::onCharacterTyped);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof SignBlock)) {
            return;
        }

        if (event.getEntity().isShiftKeyDown()) {
            SignTranslationManager.INSTANCE.allowVanillaEdit();
            return;
        }

        if (event.getItemStack().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SignBlock) {
            SignTranslationManager.INSTANCE.allowVanillaEdit();
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        SignTranslationManager.INSTANCE.openTranslatedSign(event.getPos());
    }

    private static void onScreenOpening(ScreenEvent.Opening event) {
        // Bloqueia a edicao de placa do jogo ANTES dela abrir. Tratar so no Init
        // deixava a tela do jogo aparecer por um frame antes de ser trocada.
        if (SignTranslationManager.INSTANCE.shouldReplaceSignScreen(event.getNewScreen())) {
            Screen ready = SignTranslationManager.INSTANCE.readyTranslatedScreen();
            if (ready != null) {
                event.setNewScreen(ready);
            } else {
                event.setNewScreen(event.getCurrentScreen());
            }
            return;
        }

    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (SignTranslationManager.INSTANCE.shouldReplaceSignScreen(event.getScreen())) {
            SignTranslationManager.INSTANCE.reopenPendingTranslation();
            return;
        }

        if (event.getScreen() instanceof BookViewScreen bookViewScreen) {
            BookTranslationManager.INSTANCE.installButton(bookViewScreen, event::addListener);
            return;
        }

        if (event.getScreen() instanceof BookEditScreen bookEditScreen) {
            BookTranslationManager.INSTANCE.installWritableButton(bookEditScreen, event::addListener);
        }
    }

    private static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof BookViewScreen || event.getScreen() instanceof BookEditScreen) {
            BookTranslationManager.INSTANCE.stopTts();
        }
    }

    private static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if ((event.getScreen() instanceof BookViewScreen || event.getScreen() instanceof BookEditScreen)
            && event.getKeyCode() == 256) {
            BookTranslationManager.INSTANCE.stopTts();
            return;
        }

        if (event.getScreen() instanceof BookEditScreen bookEditScreen
            && BookTranslationManager.INSTANCE.isWritableTranslated(bookEditScreen)) {
            event.setCanceled(true);
        }
    }

    private static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (event.getScreen() instanceof BookEditScreen bookEditScreen
            && BookTranslationManager.INSTANCE.isWritableTranslated(bookEditScreen)) {
            event.setCanceled(true);
        }
    }
}
