package com.adrian.posmortal;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Token del Fornite: te deja convertirte en fantasma (y volver a humano) cuando quieras. No se gasta. */
public class ForniteTokenItem extends Item {
    public ForniteTokenItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (PossessionEvents.toggleGhost(sp)) {
                level.playSound(null, sp.blockPosition(), Posmortal.TOKEN_SOUND.get(), SoundSource.PLAYERS, 1f, 1f);
            }
        }
        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
