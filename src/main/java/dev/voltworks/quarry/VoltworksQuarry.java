package dev.voltworks.quarry;

import dev.voltworks.Voltworks;
import dev.voltworks.quarry.block.QuarryBlock;
import dev.voltworks.quarry.block.QuarryBlockEntity;
import dev.voltworks.quarry.block.QuarryMarkerBlock;
import dev.voltworks.quarry.item.QuarryUpgradeItem;
import dev.voltworks.quarry.menu.QuarryMenu;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Voltworks Quarry: an addon for Voltworks Industrial. */
public class VoltworksQuarry implements ModInitializer {
	public static final String MOD_ID = "voltworks_quarry";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static final Block QUARRY = registerBlock("quarry", p -> new QuarryBlock(() -> VoltworksQuarry.QUARRY_ENTITY, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 8.0F).sound(SoundType.METAL)
					.requiresCorrectToolForDrops().lightLevel(s -> s.getValue(QuarryBlock.LIT) ? 8 : 0));
	public static final Block QUARRY_MARKER = registerBlock("quarry_marker", QuarryMarkerBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instabreak().noCollision().noOcclusion().sound(SoundType.METAL)
					.lightLevel(s -> 7).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
	public static final BlockEntityType<QuarryBlockEntity> QUARRY_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("quarry"),
			FabricBlockEntityTypeBuilder.create(QuarryBlockEntity::new, QUARRY).build());
	public static final MenuType<QuarryMenu> QUARRY_MENU = Registry.register(BuiltInRegistries.MENU, id("quarry"),
			new MenuType<>(QuarryMenu::client, FeatureFlags.VANILLA_SET));

	public static final Item RANGE_UPGRADE = registerItem("range_upgrade", p -> new QuarryUpgradeItem(p, QuarryUpgradeItem.Type.RANGE));
	public static final Item FORTUNE_UPGRADE = registerItem("fortune_upgrade", p -> new QuarryUpgradeItem(p, QuarryUpgradeItem.Type.FORTUNE));
	public static final Item SILK_TOUCH_UPGRADE = registerItem("silk_touch_upgrade", p -> new QuarryUpgradeItem(p, QuarryUpgradeItem.Type.SILK_TOUCH));
	/** Only used to draw the quarry's gantry in the world; not obtainable. (The laser is no longer drawn; its id is kept.) */
	public static final Item DRILL_HEAD = registerItem("drill_head", Item::new);
	public static final Item LASER = registerItem("laser", Item::new);
	public static final Item GANTRY_BEAM = registerItem("gantry_beam", Item::new);
	public static final Item GANTRY_JOINT = registerItem("gantry_joint", Item::new);
	public static final Item GANTRY_CARRIAGE = registerItem("gantry_carriage", Item::new);
	public static final Item DRILL_SHAFT = registerItem("drill_shaft", Item::new);
	/** Every render-only item, hidden from JEI. */
	public static final java.util.List<Item> RENDER_ONLY = java.util.List.of(DRILL_HEAD, LASER, GANTRY_BEAM, GANTRY_JOINT, GANTRY_CARRIAGE, DRILL_SHAFT);

	@Override
	public void onInitialize() {
		// Listed at the end of the Voltworks creative tab
		CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Voltworks.id("main"))).register(out -> {
			out.accept(QUARRY);
			out.accept(QUARRY_MARKER);
			out.accept(RANGE_UPGRADE);
			out.accept(FORTUNE_UPGRADE);
			out.accept(SILK_TOUCH_UPGRADE);
		});
	}

	private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(props.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static Item registerItem(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}
}
