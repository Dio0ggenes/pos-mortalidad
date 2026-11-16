package com.adrian.posmortal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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

import java.util.function.Supplier;

@Mod(Posmortal.MODID)
public class Posmortal {
    public static final String MODID = "posmortal";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    // ---------------------------------------------------------------- bloques de la arena (todos irrompibles)

    private static BlockBehaviour.Properties unbreakable() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                .strength(-1.0f, 3600000.0f).sound(SoundType.DEEPSLATE).noLootTable();
    }

    private static RegistryObject<Block> block(String name, Supplier<Block> sup) {
        RegistryObject<Block> b = BLOCKS.register(name, sup);
        ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }

    public static final RegistryObject<Block> SPECTRAL_FLOOR = block("spectral_floor", () -> new Block(unbreakable()));
    public static final RegistryObject<Block> SPECTRAL_STONE = block("spectral_stone", () -> new Block(unbreakable()));
    public static final RegistryObject<Block> SPECTRAL_BRICKS = block("spectral_bricks", () -> new Block(unbreakable()));
    public static final RegistryObject<Block> SPECTRAL_PILLAR = block("spectral_pillar", () -> new RotatedPillarBlock(unbreakable()));
    // limbo: blanco y dorado (también irrompibles)
    private static BlockBehaviour.Properties holy(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3600000.0f).sound(sound).noLootTable();
    }

    public static final RegistryObject<Block> HALO_MARBLE = block("halo_marble", () -> new Block(holy(MapColor.QUARTZ, SoundType.CALCITE)));
    public static final RegistryObject<Block> HALO_BRICKS = block("halo_bricks", () -> new Block(holy(MapColor.QUARTZ, SoundType.CALCITE)));
    public static final RegistryObject<Block> HALO_PILLAR = block("halo_pillar", () -> new RotatedPillarBlock(holy(MapColor.QUARTZ, SoundType.CALCITE)));
    public static final RegistryObject<Block> HALO_LIGHT = block("halo_light",
            () -> new Block(holy(MapColor.GOLD, SoundType.AMETHYST).lightLevel(s -> 15)));
    public static final RegistryObject<Block> CLOUD_BLOCK = block("cloud_block", () -> new Block(holy(MapColor.SNOW, SoundType.WOOL)));

    public static final RegistryObject<Block> SOUL_CRYSTAL = block("soul_crystal",
            () -> new Block(unbreakable().lightLevel(s -> 13)));

    // ---------------------------------------------------------------- entidades, sonido, objetos

    public static final RegistryObject<EntityType<MilkBeam>> MILK_BEAM = ENTITIES.register("milk_beam",
            () -> EntityType.Builder.<MilkBeam>of(MilkBeam::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1).build("milk_beam"));

    public static final RegistryObject<EntityType<Doppelganger>> DOPPELGANGER = ENTITIES.register("doppelganger",
            () -> EntityType.Builder.<Doppelganger>of(Doppelganger::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).build("doppelganger"));

    public static final RegistryObject<EntityType<LimboGhost>> LIMBO_GHOST = ENTITIES.register("limbo_ghost",
            () -> EntityType.Builder.<LimboGhost>of(LimboGhost::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.4f).clientTrackingRange(10).build("limbo_ghost"));

    public static final RegistryObject<SoundEvent> TOKEN_SOUND = SOUNDS.register("token_use",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "token_use")));

    public static final RegistryObject<Item> TOTEM = ITEMS.register("totem_posmortalidad",
            () -> new TotemItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));

    /** Solo lo suelta el jefe. Con un tótem de nivel 9 se crea el amuleto. */
    public static final RegistryObject<Item> STAR_THIRD = ITEMS.register("nether_star_third",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** Lo suelta el fantasma del limbo. Sirve para el sándwich. */
    public static final RegistryObject<Item> SPECTRALIUM = ITEMS.register("spectralium",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<Item> MAYONNAISE = ITEMS.register("mayonnaise",
            () -> new Item(new Item.Properties()));

    private static FoodProperties sandwichFood() {
        // como una manzana de Notch: regeneración, resistencia, resistencia al fuego y absorción
        return new FoodProperties.Builder().nutrition(8).saturationMod(1.2f).alwaysEat()
                .effect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1), 1.0f)
                .effect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 0), 1.0f)
                .effect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 6000, 0), 1.0f)
                .effect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 3), 1.0f)
                .build();
    }

    public static final RegistryObject<Item> SPECTRALIUM_SANDWICH = ITEMS.register("spectralium_sandwich",
            () -> new GlowingItem(new Item.Properties().rarity(Rarity.EPIC).food(sandwichFood())));

    public static final RegistryObject<Item> AMULET = ITEMS.register("posmortality_amulet",
            () -> new AmuletItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<RecipeSerializer<TotemRecipe>> TOTEM_RECIPE =
            RECIPES.register("totem_craft", () -> new SimpleCraftingRecipeSerializer<>(TotemRecipe::new));

    public static final RegistryObject<RecipeSerializer<AmuletRecipe>> AMULET_RECIPE =
            RECIPES.register("amulet_craft", () -> new SimpleCraftingRecipeSerializer<>(AmuletRecipe::new));

    public Posmortal() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
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
        e.put(LIMBO_GHOST.get(), LimboGhost.createAttributes().build());
    }

    private void common(FMLCommonSetupEvent e) {
        e.enqueueWork(Net::init);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(TOTEM);
            e.accept(STAR_THIRD);
            e.accept(AMULET);
            e.accept(SPECTRALIUM);
            e.accept(MAYONNAISE);
            e.accept(SPECTRALIUM_SANDWICH);
        }
        if (e.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            e.accept(SPECTRAL_FLOOR);
            e.accept(SPECTRAL_STONE);
            e.accept(SPECTRAL_BRICKS);
            e.accept(SPECTRAL_PILLAR);
            e.accept(SOUL_CRYSTAL);
            e.accept(HALO_MARBLE);
            e.accept(HALO_BRICKS);
            e.accept(HALO_PILLAR);
            e.accept(HALO_LIGHT);
            e.accept(CLOUD_BLOCK);
        }
    }
}
