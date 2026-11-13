package com.adrian.posmortal;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(Posmortal.MODID)
public class Posmortal {
    public static final String MODID = "posmortal";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);

    public static final RegistryObject<Item> TOTEM = ITEMS.register("totem_posmortalidad",
            () -> new TotemItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<RecipeSerializer<TotemRecipe>> TOTEM_RECIPE =
            RECIPES.register("totem_craft", () -> new SimpleCraftingRecipeSerializer<>(TotemRecipe::new));

    public Posmortal() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        RECIPES.register(bus);
        bus.addListener(this::common);
        bus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(new PossessionEvents());
    }

    private void common(FMLCommonSetupEvent e) {
        e.enqueueWork(Net::init);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(TOTEM);
        }
    }
}
