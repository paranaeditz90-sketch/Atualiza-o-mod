package com.becomelm.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Teclas (aparecem em Controles > "BecomeLM mode"). No celular o Zalith deixa pôr um
 * botão na tela que manda qualquer tecla: dá pra criar um botão "R" pra roda e "V" pra
 * câmera - ou só usar os dois toques no agachar.
 */
final class LmKeys {

    private static final String CATEGORY = "key.categories.becomelm";

    static final KeyMapping WHEEL = new KeyMapping("key.becomelm.wheel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    static final KeyMapping CAMERA = new KeyMapping("key.becomelm.camera", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    private LmKeys() {
    }
}
