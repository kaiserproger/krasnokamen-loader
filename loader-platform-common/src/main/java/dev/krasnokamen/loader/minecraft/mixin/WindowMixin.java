package dev.krasnokamen.loader.minecraft.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.krasnokamen.loader.minecraft.LoaderMinecraftInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Release held bindings from the game window's focus callback, before any later
 * key-up. Read the resulting window state rather than the version-specific callback arguments.
 */
@Mixin(Window.class)
abstract class WindowMixin {
    @Inject(method = "onFocus", at = @At("RETURN"))
    private void krasnokamen$windowFocus(CallbackInfo callback) {
        if (!((Window) (Object) this).isFocused()) {
            LoaderMinecraftInput.focusLost();
        }
    }
}
