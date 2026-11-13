package com.adrian.posmortal;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Mesa 3x3 llena: cada hueco es un tótem o un diamante, con al menos 1 tótem. Usos = nº de tótems. */
public class TotemRecipe extends CustomRecipe {
    public TotemRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    private int count(CraftingContainer c) {
        if (c.getWidth() != 3 || c.getHeight() != 3) return -1;
        int totems = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.is(Items.TOTEM_OF_UNDYING)) totems++;
            else if (!s.is(Items.DIAMOND)) return -1;
        }
        return totems;
    }

    @Override
    public boolean matches(CraftingContainer c, Level level) {
        return count(c) >= 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer c, RegistryAccess access) {
        ItemStack r = new ItemStack(Posmortal.TOTEM.get());
        r.getOrCreateTag().putInt("Uses", Math.max(1, count(c)));
        return r;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w >= 3 && h >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Posmortal.TOTEM_RECIPE.get();
    }
}
