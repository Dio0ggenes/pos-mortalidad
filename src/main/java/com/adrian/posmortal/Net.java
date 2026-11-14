package com.adrian.posmortal;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

public class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Posmortal.MODID, "main"), () -> V, V::equals, V::equals);

    public static void init() {
        CH.registerMessage(0, ModelMsg.class, ModelMsg::enc, ModelMsg::dec, ModelMsg::handle);
        CH.registerMessage(1, AbilityMsg.class, AbilityMsg::enc, AbilityMsg::dec, AbilityMsg::handle);
        CH.registerMessage(2, ExitMsg.class, ExitMsg::enc, ExitMsg::dec, ExitMsg::handle);
    }

    /** Servidor -> cliente: qué modelo lleva un jugador ("" = humano, "ghost", o id de entidad). */
    public static class ModelMsg {
        final UUID id;
        final String model;

        public ModelMsg(UUID id, String model) {
            this.id = id;
            this.model = model;
        }

        static void enc(ModelMsg m, FriendlyByteBuf b) {
            b.writeUUID(m.id);
            b.writeUtf(m.model);
        }

        static ModelMsg dec(FriendlyByteBuf b) {
            return new ModelMsg(b.readUUID(), b.readUtf());
        }

        static void handle(ModelMsg m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.setModel(m.id, m.model)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: tecla de habilidad. */
    public static class AbilityMsg {
        public AbilityMsg() {
        }

        static void enc(AbilityMsg m, FriendlyByteBuf b) {
        }

        static AbilityMsg dec(FriendlyByteBuf b) {
            return new AbilityMsg();
        }

        static void handle(AbilityMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp != null) PossessionEvents.useAbility(sp);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: botón "salir del cuerpo" del inventario. */
    public static class ExitMsg {
        public ExitMsg() {
        }

        static void enc(ExitMsg m, FriendlyByteBuf b) {
        }

        static ExitMsg dec(FriendlyByteBuf b) {
            return new ExitMsg();
        }

        static void handle(ExitMsg m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sp != null) PossessionEvents.exitPossession(sp, true);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
