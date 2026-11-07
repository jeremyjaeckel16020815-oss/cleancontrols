package de.cleancontrols.mixin;

import de.cleancontrols.CleanControlsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen cleancontrols$replace(Screen screen) {
        if (screen instanceof ControlsOptionsScreen) {
            MinecraftClient mc = (MinecraftClient) (Object) this;
            return new CleanControlsScreen(mc.currentScreen, mc.options);
        }
        return screen;
    }
}
