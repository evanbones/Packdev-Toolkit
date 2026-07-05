package com.evandev.packdev_toolkit.client.compat.emi;

import com.evandev.packdev_toolkit.Constants;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

public class EmiExportSupport {

    public static boolean isRecipeScreenOpen(Minecraft mc) {
        return mc.screen instanceof RecipeScreen;
    }

    public static @Nullable EmiIngredient getEmiHoveredIngredient(Minecraft mc) {
        if (mc.screen instanceof RecipeScreen recipeScreen) {
            EmiIngredient stack = recipeScreen.getHoveredStack();
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
        }
        EmiStackInteraction interaction = EmiApi.getHoveredStack(true);
        if (interaction != null && !interaction.isEmpty()) {
            return interaction.getStack();
        }
        return null;
    }

    public static ItemStack getHoveredStack(Minecraft mc) {
        EmiIngredient ingredient = getEmiHoveredIngredient(mc);
        if (ingredient == null || ingredient.isEmpty() || ingredient.getEmiStacks().isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = ingredient.getEmiStacks().getFirst().getItemStack();
        return stack != null ? stack : ItemStack.EMPTY;
    }

    public static @Nullable Object getHoveredTagKey(Minecraft mc) {
        EmiIngredient ingredient = getEmiHoveredIngredient(mc);
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }

        if (ingredient instanceof TagEmiIngredient tagIngredient) {
            return tagIngredient.key;
        }

        return getFieldOfType(ingredient, TagKey.class);
    }

    private static Object getFieldOfType(Object obj, Class<?> type) {
        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return f.get(obj);
                    } catch (Exception ignored) {
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static HoveredRecipeResult getHoveredRecipe(Minecraft mc) {
        if (!isRecipeScreenOpen(mc)) {
            return HoveredRecipeResult.NONE;
        }

        try {
            double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();

            Field currentPageField = mc.screen.getClass().getDeclaredField("currentPage");
            currentPageField.setAccessible(true);
            List<?> currentPage = (List<?>) currentPageField.get(mc.screen);
            if (currentPage == null) {
                return HoveredRecipeResult.NONE;
            }

            for (Object group : currentPage) {
                Class<?> groupClass = group.getClass();

                int groupX = (int) groupClass.getMethod("x").invoke(group);
                int groupY = (int) groupClass.getMethod("y").invoke(group);

                int relX = (int) mouseX - groupX;
                int relY = (int) mouseY - groupY;

                List<Widget> widgets = (List<Widget>) groupClass.getField("widgets").get(group);
                for (Widget widget : widgets) {
                    if (widget.getBounds().contains(relX, relY)) {
                        EmiRecipe recipe = (EmiRecipe) groupClass.getField("recipe").get(group);
                        return new HoveredRecipeResult(true, recipe.getId());
                    }
                }
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to extract hovered EMI recipe", e);
        }

        return HoveredRecipeResult.NONE;
    }

    public record HoveredRecipeResult(boolean hovered, @Nullable ResourceLocation recipeId) {
        public static final HoveredRecipeResult NONE = new HoveredRecipeResult(false, null);
    }
}
