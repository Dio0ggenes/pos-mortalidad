package com.adrian.posmortal;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Objeto con brillo de encantado (el sándwich de Spectralium). */
public class GlowingItem extends Item {
    public GlowingItem(Properties p) {
        super(p);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
