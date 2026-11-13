package com.adrian.posmortal;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TotemItem extends Item {
    public TotemItem(Properties p) {
        super(p);
    }

    public static int uses(ItemStack s) {
        if (s.hasTag() && s.getTag().contains("Uses")) return Math.max(1, s.getTag().getInt("Uses"));
        return 1;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Usos: " + uses(stack)).withStyle(ChatFormatting.GRAY));
    }
}
