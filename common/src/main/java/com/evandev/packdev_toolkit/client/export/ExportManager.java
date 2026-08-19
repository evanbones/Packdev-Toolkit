package com.evandev.packdev_toolkit.client.export;

import com.evandev.packdev_toolkit.Constants;
import com.evandev.packdev_toolkit.client.compat.emi.EmiExportSupport;
import com.evandev.packdev_toolkit.config.ModConfig;
import com.evandev.packdev_toolkit.platform.Services;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class ExportManager {

    public static void handleExportKeyPress(Minecraft mc, boolean shiftDown) {
        if (Services.PLATFORM.isModLoaded("emi")) {
            if (tryExportHoveredRecipe(mc)) {
                return;
            }

            ItemStack hovered = EmiExportSupport.getHoveredStack(mc);
            if (!hovered.isEmpty()) {
                exportItem(mc, hovered, shiftDown);
                return;
            }
        }

        if (mc.screen == null && mc.level != null) {
            HitResult hit = mc.hitResult;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
                exportBlock(mc, blockHit.getBlockPos(), shiftDown);
                return;
            } else if (hit != null && hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
                exportEntity(mc, entityHit.getEntity());
                return;
            }
        }

        if (mc.screen == null && mc.player != null) {
            ItemStack held = mc.player.getMainHandItem();
            if (!held.isEmpty()) {
                exportItem(mc, held, shiftDown);
            }
        }
    }

    private static boolean tryExportHoveredRecipe(Minecraft mc) {
        EmiExportSupport.HoveredRecipeResult result = EmiExportSupport.getHoveredRecipe(mc);
        if (!result.hovered()) {
            return false;
        }

        if (result.recipeId() == null) {
            sendMessage(mc, "Hovered recipe lacks a backing JSON.");
            return true;
        }

        exportRecipe(mc, result.recipeId());
        return true;
    }

    private static void exportBlock(Minecraft mc, BlockPos pos, boolean shiftDown) {
        BlockState state = mc.level.getBlockState(pos);
        Set<ResourceLocation> assetsToCopy = new HashSet<>();

        if (shiftDown) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            assetsToCopy.add(ResourceLocation.fromNamespaceAndPath(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json"));
            assetsToCopy.add(ResourceLocation.fromNamespaceAndPath(blockId.getNamespace(), "models/block/" + blockId.getPath() + ".json"));
        } else {
            BakedModel model = mc.getBlockRenderer().getBlockModel(state);
            assetsToCopy.addAll(getSpritesFromModel(model, state));

            BlockEntity blockEntity = mc.level.getBlockEntity(pos);
            if (blockEntity != null) {
                assetsToCopy.addAll(captureBlockEntityTextures(mc, blockEntity));
            }
        }

        mc.setScreen(new ExportSelectionScreen(assetsToCopy));
    }

    private static Set<ResourceLocation> captureBlockEntityTextures(Minecraft mc, BlockEntity blockEntity) {
        BlockEntityRenderer<BlockEntity> renderer = mc.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
        if (renderer == null) {
            return Set.of();
        }

        TextureCatcher.start();
        try {
            PoseStack poseStack = new PoseStack();
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

            renderer.render(blockEntity, mc.getTimer().getGameTimeDeltaTicks(), poseStack, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            bufferSource.endBatch();
        } catch (Exception e) {
            Constants.LOG.error("Failed to capture block entity renderer textures for {}", blockEntity.getType(), e);
        }

        Set<ResourceLocation> normalized = new HashSet<>();
        for (ResourceLocation loc : TextureCatcher.stop()) {
            normalized.add(normalizeTextureLocation(loc));
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static void exportEntity(Minecraft mc, Entity entity) {
        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) mc.getEntityRenderDispatcher().getRenderer(entity);
        Set<ResourceLocation> assetsToCopy = new HashSet<>();
        assetsToCopy.add(normalizeTextureLocation(renderer.getTextureLocation(entity)));
        mc.setScreen(new ExportSelectionScreen(assetsToCopy));
    }

    private static void exportItem(Minecraft mc, ItemStack stack, boolean shiftDown) {
        Set<ResourceLocation> assetsToCopy = new HashSet<>();

        if (shiftDown) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            assetsToCopy.add(ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), "models/item/" + itemId.getPath() + ".json"));
        } else {
            BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
            assetsToCopy.addAll(getSpritesFromModel(model, null));
        }

        mc.setScreen(new ExportSelectionScreen(assetsToCopy));
    }

    private static void exportRecipe(Minecraft mc, ResourceLocation recipeId) {
        try {
            RecipeHolder<?> serverRecipe;
            var ops = getRegistryOps(mc);

            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                serverRecipe = mc.getSingleplayerServer().getRecipeManager().byKey(recipeId).orElse(null);
            } else {
                sendMessage(mc, "Warning: Exporting shaped recipes requires Singleplayer!");
                serverRecipe = mc.level.getRecipeManager().byKey(recipeId).orElse(null);
            }

            if (serverRecipe == null) {
                sendMessage(mc, "Hovered recipe lacks a backing JSON. ID: " + recipeId);
                return;
            }

            Codec<Recipe<?>> recipeCodec = BuiltInRegistries.RECIPE_SERIALIZER.byNameCodec()
                    .dispatch(Recipe::getSerializer, RecipeSerializer::codec);

            JsonElement json = recipeCodec.encodeStart(ops, serverRecipe.value()).getOrThrow();
            String jsonStr = new GsonBuilder().setPrettyPrinting().create().toJson(json);

            Path dataRoot = ExportPaths.dataRoot(mc.gameDirectory.toPath());
            Path recipeFile = dataRoot.resolve(recipeId.getNamespace()).resolve("recipe").resolve(recipeId.getPath() + ".json");

            Files.createDirectories(recipeFile.getParent());
            Files.writeString(recipeFile, jsonStr);

            sendMessage(mc, "Exported recipe to: " + recipeFile);
            if (ModConfig.get().openFolderOnExport) {
                Util.getPlatform().openUri(recipeFile.getParent().toUri());
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to serialize or save recipe for export", e);
            sendMessage(mc, "Export Failed: " + e.getMessage());
        }
    }

    private static RegistryOps<JsonElement> getRegistryOps(Minecraft mc) {
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        }
        return mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    private static void sendMessage(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }

    private static Set<ResourceLocation> getSpritesFromModel(BakedModel model, @Nullable BlockState state) {
        Set<ResourceLocation> sprites = new HashSet<>();
        sprites.add(normalizeTextureLocation(ResourceLocation.parse(model.getParticleIcon().contents().name().toString())));

        RandomSource random = RandomSource.create();

        for (Direction dir : Direction.values()) {
            for (BakedQuad quad : model.getQuads(state, dir, random)) {
                sprites.add(normalizeTextureLocation(ResourceLocation.parse(quad.getSprite().contents().name().toString())));
            }
        }

        for (BakedQuad quad : model.getQuads(state, null, random)) {
            sprites.add(normalizeTextureLocation(ResourceLocation.parse(quad.getSprite().contents().name().toString())));
        }

        return sprites;
    }

    private static ResourceLocation normalizeTextureLocation(ResourceLocation rawLoc) {
        if (rawLoc.getPath().endsWith(".png")) {
            return rawLoc;
        }
        return ResourceLocation.fromNamespaceAndPath(rawLoc.getNamespace(), "textures/" + rawLoc.getPath() + ".png");
    }
}
