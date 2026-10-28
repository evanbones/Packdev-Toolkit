package com.evandev.packdev_toolkit.mixin.client;

import com.evandev.packdev_toolkit.client.export.TextureCatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Material.class)
public abstract class MaterialMixin {

    @Inject(method = "sprite", at = @At("HEAD"))
    private void packdev_toolkit$captureSprite(CallbackInfoReturnable<TextureAtlasSprite> cir) {
        TextureCatcher.addSprite(((Material) (Object) this).texture());
    }
}
