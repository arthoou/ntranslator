package com.arthou.ntranslator.forge;

import dev.architectury.event.events.client.ClientTickEvent;
import com.arthou.ntranslator.client.books.BookTranslationManager;
import com.arthou.ntranslator.client.signs.SignTranslationManager;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SignBlock;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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
        NeoForge.EVENT_BUS.addListener(ForgeClientBootstrap::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ForgeClientBootstrap::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ForgeClientBootstrap::onScreenClosing);
        NeoForge.EVENT_BUS.addListener(ForgeClientBootstrap::onKeyPressed);
        NeoForge.EVENT_BUS.addListener(ForgeClientBootstrap::onCharacterTyped);
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

        // Agachado ou com uma placa na mao: e a interacao normal do jogo. Avisamos o
        // gerenciador para ele nao trocar a tela de edicao pela tela traduzida.
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
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        SignTranslationManager.INSTANCE.openTranslatedSign(event.getPos());
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
