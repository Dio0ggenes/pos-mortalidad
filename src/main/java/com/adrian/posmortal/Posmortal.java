package com.adrian.posmortal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
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

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<EntityType<MilkBeam>> MILK_BEAM = ENTITIES.register("milk_beam",
            () -> EntityType.Builder.<MilkBeam>of(MilkBeam::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1).build("milk_beam"));

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<SoundEvent> TOKEN_SOUND = SOUNDS.register("token_use",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "token_use")));

    public static final RegistryObject<EntityType<Doppelganger>> DOPPELGANGER = ENTITIES.register("doppelganger",
            () -> EntityType.Builder.<Doppelganger>of(Doppelganger::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).build("doppelganger"));

    public static final RegistryObject<Item> FORNITE_TOKEN = ITEMS.register("fornite_token",
            () -> new ForniteTokenItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> TOTEM = ITEMS.register("totem_posmortalidad",
            () -> new TotemItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<RecipeSerializer<TotemRecipe>> TOTEM_RECIPE =
            RECIPES.register("totem_craft", () -> new SimpleCraftingRecipeSerializer<>(TotemRecipe::new));

    public Posmortal() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        RECIPES.register(bus);
        ENTITIES.register(bus);
        SOUNDS.register(bus);
        bus.addListener(this::attributes);
        bus.addListener(this::common);
        bus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(new PossessionEvents());
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(DOPPELGANGER.get(), Doppelganger.createAttributes().build());
    }

    private void common(FMLCommonSetupEvent e) {
        e.enqueueWork(Net::init);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(TOTEM);
            e.accept(FORNITE_TOKEN);
        }
    }
}
