package com.adrian.posmortal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
    public static final RegistryObject<Block> SOUL_CRYSTAL = block("soul_crystal",
            () -> new Block(unbreakable().lightLevel(s -> 13)));

    // ---------------------------------------------------------------- entidades, sonido, objetos

    public static final RegistryObject<EntityType<MilkBeam>> MILK_BEAM = ENTITIES.register("milk_beam",
            () -> EntityType.Builder.<MilkBeam>of(MilkBeam::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1).build("milk_beam"));

    public static final RegistryObject<EntityType<Doppelganger>> DOPPELGANGER = ENTITIES.register("doppelganger",
            () -> EntityType.Builder.<Doppelganger>of(Doppelganger::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).build("doppelganger"));

    public static final RegistryObject<SoundEvent> TOKEN_SOUND = SOUNDS.register("token_use",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "token_use")));

    public static final RegistryObject<Item> TOTEM = ITEMS.register("totem_posmortalidad",
            () -> new TotemItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));

    /** Solo lo suelta el jefe. Con un tótem de nivel 9 se crea el amuleto. */
    public static final RegistryObject<Item> STAR_THIRD = ITEMS.register("nether_star_third",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

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
    }

    private void common(FMLCommonSetupEvent e) {
        e.enqueueWork(Net::init);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(TOTEM);
            e.accept(STAR_THIRD);
            e.accept(AMULET);
        }
        if (e.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            e.accept(SPECTRAL_FLOOR);
            e.accept(SPECTRAL_STONE);
            e.accept(SPECTRAL_BRICKS);
            e.accept(SPECTRAL_PILLAR);
            e.accept(SOUL_CRYSTAL);
        }
    }
}
