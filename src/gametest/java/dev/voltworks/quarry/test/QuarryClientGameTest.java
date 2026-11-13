package dev.voltworks.quarry.test;

import dev.voltworks.block.MachineBlock;
import dev.voltworks.block.entity.BatteryBlockEntity;
import dev.voltworks.quarry.VoltworksQuarry;
import dev.voltworks.quarry.block.QuarryBlockEntity;
import dev.voltworks.registry.ModBlocks;
import dev.voltworks.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;

/**
 * In-world test: builds five quarries in the air above spawn, each with a small block layout in its pit,
 * runs the game and checks what they dug. Run with: ./gradlew runClientGameTest
 *
 * Every quarry faces north, so its pit lies south of it: x -4..+4, z +1..+9 (9x9), starting at its own height.
 */
public class QuarryClientGameTest implements FabricClientGameTest {
	private static final int RUN_TICKS = 900;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(s -> s.setWorldType(new WorldCreationUiState.WorldTypeEntry(
						s.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL))))
				.create()) {
			world.getServer().runOnServer(QuarryClientGameTest::build);
			// Mid-dig: the drill head and laser over the fortune quarry's pit
			context.waitTicks(120);
			world.getServer().runOnServer(server -> {
				BlockPos o = origin(server);
				command(server, String.format("tp @a %d %d %d 35 40", o.getX() + 7, o.getY() + 5, o.getZ() - 3));
			});
			context.waitTicks(20);
			context.takeScreenshot("quarry-digging");
			for (int t = 140; t < RUN_TICKS; t += 100) {
				context.waitTicks(100);
				world.getServer().runOnServer(server -> {
					QuarryBlockEntity a = (QuarryBlockEntity) server.overworld().getBlockEntity(origin(server));
					System.out.println("[voltworks-quarry-test] trace: mined " + a.getBlocksMined() + " status " + a.getStatus() + " target " + a.getTarget() + " energy " + a.getEnergy());
				});
			}
			List<String> failures = world.getServer().computeOnServer(QuarryClientGameTest::check);

			// The finished pit, then the GUI
			world.getServer().runOnServer(server -> {
				BlockPos o = origin(server);
				command(server, String.format("tp @a %d %d %d 0 50", o.getX(), o.getY() + 6, o.getZ() - 5));
			});
			context.waitTicks(20);
			context.takeScreenshot("quarry-pit");
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0)
					.openMenu((MenuProvider) server.overworld().getBlockEntity(origin(server))));
			context.waitTicks(30);
			context.takeScreenshot("quarry-gui");
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0)
					.openMenu((MenuProvider) server.overworld().getBlockEntity(origin(server).offset(60, 0, 0))));
			context.waitTicks(30);
			context.takeScreenshot("quarry-gui-paused");
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).closeContainer());

			if (!failures.isEmpty()) {
				throw new AssertionError("Voltworks Quarry checks failed:\n - " + String.join("\n - ", failures));
			}
			System.out.println("[voltworks-quarry-test] ALL CHECKS PASSED");
		}
	}

	private static BlockPos origin(MinecraftServer server) {
		BlockPos spawn = server.getRespawnData().pos();
		return new BlockPos(spawn.getX(), 150, spawn.getZ());
	}

	private static void command(MinecraftServer server, String cmd) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
	}

	private static void set(ServerLevel level, BlockPos pos, Block block) {
		level.setBlockAndUpdate(pos, block.defaultBlockState());
	}

	/** A quarry facing north, a full Huge Battery west of it, a chest on top and the given upgrades. */
	private static QuarryBlockEntity quarry(ServerLevel level, BlockPos pos, boolean powered, Item... upgrades) {
		level.setBlockAndUpdate(pos, VoltworksQuarry.QUARRY.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
		QuarryBlockEntity quarry = (QuarryBlockEntity) level.getBlockEntity(pos);
		for (int i = 0; i < upgrades.length; i++) quarry.setItem(QuarryBlockEntity.OUTPUT_SLOTS + i, new ItemStack(upgrades[i]));
		if (powered) {
			level.setBlockAndUpdate(pos.west(), ModBlocks.HUGE_BATTERY.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
			BatteryBlockEntity battery = (BatteryBlockEntity) level.getBlockEntity(pos.west());
			while (battery.receiveEnergy(Integer.MAX_VALUE, false) > 0) {
			}
		}
		set(level, pos.above(), Blocks.CHEST);
		return quarry;
	}

	/** Pit position: dx sideways (-4..4), dz rows away from the quarry (1..9), dy down from the quarry's height. */
	private static BlockPos pit(BlockPos q, int dx, int dz, int dy) {
		return q.offset(dx, -dy, dz);
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos o = origin(server);
		command(server, "time set day");
		command(server, "weather clear");
		command(server, "gamemode creative @a");
		server.getPlayerList().getPlayers().forEach(p -> {
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		});
		command(server, String.format("tp @a %d %d %d", o.getX(), o.getY() + 3, o.getZ() - 4));

		// A: Speed x2 + Fortune. Top layer stone with ores, a chest of diamonds, bedrock and a water source; dirt below.
		quarry(level, o, true, ModItems.SPEED_UPGRADE, ModItems.SPEED_UPGRADE, VoltworksQuarry.FORTUNE_UPGRADE);
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = 1; dz <= 9; dz++) {
				set(level, pit(o, dx, dz, 0), Blocks.STONE);
				set(level, pit(o, dx, dz, 1), Blocks.DIRT);
			}
		}
		for (int i = 0; i < 4; i++) set(level, pit(o, -3 + 2 * i, 5, 0), Blocks.IRON_ORE);
		set(level, pit(o, 2, 2, 0), Blocks.CHEST);
		((Container) level.getBlockEntity(pit(o, 2, 2, 0))).setItem(0, new ItemStack(Items.DIAMOND, 5));
		set(level, pit(o, -2, 3, 0), Blocks.BEDROCK);
		set(level, pit(o, 0, 8, 0), Blocks.WATER);

		// B: Silk Touch + Speed x2: stone, iron ore and grass come out as themselves
		BlockPos b = o.offset(30, 0, 0);
		quarry(level, b, true, VoltworksQuarry.SILK_TOUCH_UPGRADE, ModItems.SPEED_UPGRADE, ModItems.SPEED_UPGRADE);
		set(level, pit(b, 0, 1, 0), Blocks.STONE);
		set(level, pit(b, 1, 1, 0), Blocks.IRON_ORE);
		set(level, pit(b, -1, 1, 0), Blocks.GRASS_BLOCK);

		// C: powered but held by a redstone signal
		BlockPos c = o.offset(60, 0, 0);
		quarry(level, c, true);
		set(level, c.east(), Blocks.REDSTONE_BLOCK);
		set(level, pit(c, 0, 1, 0), Blocks.STONE);

		// D: no power at all
		BlockPos d = o.offset(75, 0, 0);
		quarry(level, d, false);
		set(level, pit(d, 0, 1, 0), Blocks.STONE);

		// E: Range Upgrade (17x17): blocks at both far corners
		BlockPos e = o.offset(100, 0, 0);
		quarry(level, e, true, VoltworksQuarry.RANGE_UPGRADE, ModItems.SPEED_UPGRADE);
		set(level, pit(e, -8, 17, 0), Blocks.STONE);
		set(level, pit(e, 8, 17, 0), Blocks.STONE);
	}

	private static int count(ServerLevel level, BlockPos quarry, Item item) {
		int n = 0;
		for (Container c : new Container[]{(Container) level.getBlockEntity(quarry), (Container) level.getBlockEntity(quarry.above())}) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	private static List<String> check(MinecraftServer server) {
		List<String> fails = new ArrayList<>();
		ServerLevel level = server.overworld();
		BlockPos o = origin(server);

		// A
		QuarryBlockEntity a = (QuarryBlockEntity) level.getBlockEntity(o);
		int left = 0;
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = 1; dz <= 9; dz++) {
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos p = pit(o, dx, dz, dy);
					BlockState s = level.getBlockState(p);
					if (dx == -2 && dz == 3 && dy == 0) {
						if (!s.is(Blocks.BEDROCK)) fails.add("A: bedrock was mined");
					} else if (!s.isAir()) {
						left++;
					}
				}
			}
		}
		if (left > 0) fails.add("A: " + left + " blocks left in the test layers");
		if (a.getBlocksMined() < 160) fails.add("A: only " + a.getBlocksMined() + " blocks mined");
		if (count(level, o, Items.DIAMOND) < 5) fails.add("A: chest contents not collected (diamonds " + count(level, o, Items.DIAMOND) + ")");
		if (count(level, o, Items.CHEST) < 1) fails.add("A: the mined chest itself is missing");
		if (count(level, o, Items.RAW_IRON) < 4) fails.add("A: raw iron " + count(level, o, Items.RAW_IRON) + " < 4");
		if (count(level, o, Items.COBBLESTONE) < 70) fails.add("A: cobblestone " + count(level, o, Items.COBBLESTONE));
		if (count(level, o, Items.DIRT) < 81) fails.add("A: dirt " + count(level, o, Items.DIRT));
		if (count(level, o, Items.STONE) > 0) fails.add("A: stone dropped without Silk Touch");
		int chestItems = 0;
		Container top = (Container) level.getBlockEntity(o.above());
		for (int i = 0; i < top.getContainerSize(); i++) chestItems += top.getItem(i).getCount();
		if (chestItems == 0) fails.add("A: nothing was pushed into the chest on top");
		List<ItemEntity> spilled = level.getEntitiesOfClass(ItemEntity.class, new AABB(o.getX() - 6, o.getY() - 3, o.getZ() - 2, o.getX() + 6, o.getY() + 3, o.getZ() + 11));
		if (!spilled.isEmpty()) fails.add("A: " + spilled.size() + " item entities spilled around the pit");

		// B
		BlockPos b = o.offset(30, 0, 0);
		if (count(level, b, Items.STONE) < 1) fails.add("B: silk touch gave no stone");
		if (count(level, b, Items.IRON_ORE) < 1) fails.add("B: silk touch gave no iron ore");
		if (count(level, b, Items.GRASS_BLOCK) < 1) fails.add("B: silk touch gave no grass block");
		if (count(level, b, Items.RAW_IRON) > 0) fails.add("B: raw iron despite silk touch");

		// C
		BlockPos c = o.offset(60, 0, 0);
		QuarryBlockEntity qc = (QuarryBlockEntity) level.getBlockEntity(c);
		if (qc.getStatus() != QuarryBlockEntity.Status.PAUSED) fails.add("C: status " + qc.getStatus() + ", expected PAUSED");
		if (!level.getBlockState(pit(c, 0, 1, 0)).is(Blocks.STONE)) fails.add("C: mined while paused by redstone");

		// D
		BlockPos d = o.offset(75, 0, 0);
		QuarryBlockEntity qd = (QuarryBlockEntity) level.getBlockEntity(d);
		if (qd.getStatus() != QuarryBlockEntity.Status.NO_POWER) fails.add("D: status " + qd.getStatus() + ", expected NO_POWER");
		if (!level.getBlockState(pit(d, 0, 1, 0)).is(Blocks.STONE)) fails.add("D: mined without power");

		// E
		BlockPos e = o.offset(100, 0, 0);
		QuarryBlockEntity qe = (QuarryBlockEntity) level.getBlockEntity(e);
		if (qe.getSize() != 17) fails.add("E: size " + qe.getSize() + ", expected 17");
		if (!level.getBlockState(pit(e, -8, 17, 0)).isAir() || !level.getBlockState(pit(e, 8, 17, 0)).isAir()) {
			fails.add("E: far corners of the 17x17 pit not mined");
		}
		if (count(level, e, Items.COBBLESTONE) < 2) fails.add("E: cobblestone " + count(level, e, Items.COBBLESTONE));

		System.out.println("[voltworks-quarry-test] A mined " + a.getBlocksMined() + ", now at Y " + a.getLayerY() + ", status " + a.getStatus());
		return fails;
	}
}
