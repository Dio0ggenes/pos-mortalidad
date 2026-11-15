package com.adrian.posmortal;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Un tercio de estrella del Nether + un tótem de la posmortalidad de nivel 9 (hecho con 9 tótems) = el amuleto. */
public class AmuletRecipe extends CustomRecipe {
    public AmuletRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer c, Level level) {
        boolean third = false, totem = false;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(Posmortal.STAR_THIRD.get()) && !third) third = true;
            else if (s.is(Posmortal.TOTEM.get()) && TotemItem.uses(s) == 9 && !totem) totem = true;
            else return false;
        }
        return third && totem;
    }

    @Override
    public ItemStack assemble(CraftingContainer c, RegistryAccess access) {
        return new ItemStack(Posmortal.AMULET.get());
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Posmortal.AMULET_RECIPE.get();
    }
}
