package com.evandev.packdev_toolkit.mixin.client;

import com.evandev.packdev_toolkit.client.export.TextureCatcher;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class RenderSystemMixin {

    @Inject(method = "setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V", at = @At("HEAD"))
    private static void packdev_toolkit$captureShaderTexture(int textureUnit, ResourceLocation location, CallbackInfo ci) {
        TextureCatcher.addTexture(location);
    }
}
