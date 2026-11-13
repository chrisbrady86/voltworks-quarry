import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generates every Voltworks Quarry asset: textures, models, blockstates, loot, recipes, tags, translations
 * (including the guide pages from tools/guide.txt) and the mod icon. All art is original.
 * Usage: java tools/GenAssets.java src/main/resources
 * The quarry's sides, top and front gauge reuse Voltworks' own textures and models ("voltworks:block/...").
 */
public class GenAssets {
	static Path res;
	static final String NS = "voltworks_quarry";
	static final Map<String, String> LANG = new LinkedHashMap<>();

	public static void main(String[] args) throws Exception {
		res = Paths.get(args[0]);
		quarry();
		marker();
		upgrades();
		renderItems();
		lang();
		icon(res.toAbsolutePath().getParent().getParent().getParent());
		System.out.println("done");
	}

	// ---------------- quarry block ----------------
	static void quarry() throws Exception {
		for (boolean on : new boolean[]{false, true}) {
			// Front: tier 2 casing with a drill bit behind a round port; the port glows red while digging
			png("block/quarry_front" + (on ? "_on" : ""), (x, y, r) -> {
				double dx = x - 7.5, dy = y - 7.0, d = Math.sqrt(dx * dx + dy * dy);
				if (d <= 4.6) {
					// Drill bit: a downward cone with spiral grooves
					int half = Math.max(0, (11 - y) / 2);
					if (y >= 4 && y <= 11 && Math.abs(dx) <= half + 0.5) {
						boolean groove = (x + y) % 3 == 0;
						return groove ? rgb(110, 116, 126) : rgb(196, 202, 210) - (x > 7 ? 0x202020 : 0);
					}
					return on ? rgb(120 + (int) (60 * (4.6 - d)), 30, 24) : rgb(26, 28, 34);
				}
				if (d <= 5.6) return on ? rgb(255, 90, 60) : rgb(70, 74, 82); // port rim
				return casing2(x, y, r);
			});
			write("assets/" + NS + "/models/block/quarry" + (on ? "_on" : "") + ".json",
					"{ \"parent\": \"minecraft:block/orientable\", \"textures\": { \"front\": \"" + NS + ":block/quarry_front" + (on ? "_on" : "")
							+ "\", \"side\": \"voltworks:block/advanced_machine_side\", \"top\": \"" + NS + ":block/quarry_top\" } }");
		}
		// Top: tier 2 casing with a hazard-striped hatch
		png("block/quarry_top", (x, y, r) -> {
			if (inRect(x, y, 4, 4, 11, 11)) {
				if (x == 4 || y == 4 || x == 11 || y == 11) return ((x + y) / 2) % 2 == 0 ? rgb(230, 180, 40) : rgb(30, 30, 30);
				return y % 2 == 0 ? rgb(40, 42, 48) : rgb(58, 62, 70);
			}
			return casing(x, y, r, ADV, rgb(240, 170, 50));
		});

		// Multipart: the quarry model per facing / lit, plus Voltworks' slim front gauge for its charge
		String[] facings = {"north", "east", "south", "west"};
		int[] rot = {0, 90, 180, 270};
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			String y = rot[i] != 0 ? ", \"y\": " + rot[i] : "";
			for (boolean lit : new boolean[]{false, true}) {
				parts.add("  { \"when\": { \"facing\": " + q(facings[i]) + ", \"lit\": " + q(String.valueOf(lit)) + " }, \"apply\": { \"model\": "
						+ q(NS + ":block/quarry" + (lit ? "_on" : "")) + y + " } }");
			}
			for (int c = 0; c <= 6; c++) {
				parts.add("  { \"when\": { \"facing\": " + q(facings[i]) + ", \"charge\": " + q(String.valueOf(c)) + " }, \"apply\": { \"model\": "
						+ q("voltworks:block/gauge_" + c) + y + " } }");
			}
		}
		write("assets/" + NS + "/blockstates/quarry.json", "{ \"multipart\": [\n" + String.join(",\n", parts) + "\n] }");
		itemDef("quarry", NS + ":block/quarry");

		write("data/" + NS + "/loot_table/blocks/quarry.json", """
			{ "type": "minecraft:block", "pools": [ { "rolls": 1,
			  "condition": { "type": "minecraft:survives_explosion" },
			  "entries": [ { "type": "minecraft:item", "name": "voltworks_quarry:quarry" } ] } ],
			  "random_sequence": "voltworks_quarry:blocks/quarry" }
			""");
		tag("block", "minecraft", "mineable/pickaxe", NS + ":quarry");
		tag("block", "minecraft", "needs_iron_tool", NS + ":quarry");

		// Steel and circuits around an advanced frame, two diamond pickaxes for the digging and a power cell
		shaped("quarry", new String[]{"SCS", "DAD", "SPS"}, "S", "voltworks:steel_ingot", "C", "voltworks:circuit",
				"D", "minecraft:diamond_pickaxe", "A", "voltworks:advanced_machine_frame", "P", "voltworks:power_cell");

		LANG.put("block." + NS + ".quarry", "Quarry");
		LANG.put("tooltip." + NS + ".quarry", "Digs out the area behind it, down to bedrock");
		LANG.put("tooltip." + NS + ".quarry.power", "20 J/t while digging · chests on any side fill up");
	}

	// ---------------- quarry marker ----------------
	/** A survey post in tier 2 colours: slate base plate, slate stem with gold bands, a glowing red lamp under a slate cap. */
	static void marker() throws Exception {
		png("block/quarry_marker_post", (x, y, r) -> {
			if (y == 1 || y == 5) return x % 2 == 0 ? rgb(240, 180, 60) : rgb(200, 140, 40);
			return x % 2 == 0 ? shade(ADV, 22) : shade(ADV, -22);
		});
		png("block/quarry_marker_base", (x, y, r) -> casing(x, y, r, ADV, rgb(240, 170, 50)));
		png("block/quarry_marker_lamp", (x, y, r) -> {
			boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
			if (edge) return rgb(150, 26, 20);
			return x < 6 && y < 6 ? rgb(255, 205, 185) : ((x + y) % 5 == 0 ? rgb(255, 120, 90) : rgb(250, 64, 44));
		});
		write("assets/" + NS + "/models/block/quarry_marker.json", """
			{ "parent": "minecraft:block/block", "ambientocclusion": false,
			  "textures": { "post": "voltworks_quarry:block/quarry_marker_post", "lamp": "voltworks_quarry:block/quarry_marker_lamp",
			                "base": "voltworks_quarry:block/quarry_marker_base", "particle": "voltworks_quarry:block/quarry_marker_lamp" },
			  "elements": [
			""" + String.join(",\n", List.of(
				box(5, 0, 5, 11, 1, 11, "#base", all(0, 7, 6, 8), all(5, 5, 11, 11)),
				box(7, 1, 7, 9, 9, 9, "#post", all(0, 0, 2, 8), all(0, 0, 2, 2)),
				lit(box(6, 9, 6, 10, 12, 10, "#lamp", all(0, 4, 16, 16), all(0, 0, 16, 16))),
				box(5.5, 12, 5.5, 10.5, 13, 10.5, "#base", all(0, 0, 5, 1), all(5, 5, 10, 10)))) + """
			  ],
			  "display": {
			    "gui": { "rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [1.1, 1.1, 1.1] },
			    "ground": { "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5] },
			    "fixed": { "scale": [0.8, 0.8, 0.8] },
			    "thirdperson_righthand": { "rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5] },
			    "firstperson_righthand": { "rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6] } } }
			""");
		write("assets/" + NS + "/blockstates/quarry_marker.json", "{ \"variants\": { \"\": { \"model\": \"" + NS + ":block/quarry_marker\" } } }");
		itemDef("quarry_marker", NS + ":block/quarry_marker");
		write("data/" + NS + "/loot_table/blocks/quarry_marker.json", """
			{ "type": "minecraft:block", "pools": [ { "rolls": 1,
			  "condition": { "type": "minecraft:survives_explosion" },
			  "entries": [ { "type": "minecraft:item", "name": "voltworks_quarry:quarry_marker" } ] } ],
			  "random_sequence": "voltworks_quarry:blocks/quarry_marker" }
			""");
		// Two markers (one area) from a redstone torch on a tin ingot
		shapedCount("quarry_marker", 2, new String[]{"R", "T"}, "R", "minecraft:redstone_torch", "T", "voltworks:tin_ingot");

		LANG.put("block." + NS + ".quarry_marker", "Quarry Marker");
		LANG.put("tooltip." + NS + ".quarry_marker", "Put one touching a Quarry and one at the opposite corner of the area to dig");
		LANG.put("tooltip." + NS + ".quarry_marker.limit", "Up to 9x9, or 17 / 25 / 33 across with Range Upgrades");
		LANG.put("message." + NS + ".marker.pair", "Pairs with the marker at %s, %s, %s: a %sx%s area");
		LANG.put("message." + NS + ".marker.none", "No other marker within %s blocks");
	}

	// ---------------- upgrades ----------------
	/** Same card as the Voltworks upgrades: dark circuit board, gold edge connector, icon in the middle. */
	static Painter upgradeCard(Painter icon) {
		return (x, y, r) -> {
			if (x < 2 || x > 13 || y < 1 || y > 14) return 0;
			if (x == 2 || x == 13 || y == 1 || y == 14) return rgb(28, 28, 32);
			if (y == 13 && x % 2 == 1) return rgb(230, 180, 60);
			int ic = icon.at(x, y, r);
			if (ic != 0) return ic;
			return shade(rgb(40, 70, 60), (x + y) % 4 == 0 ? 10 : 0);
		};
	}

	/** Icons get a dark outline, as on the Voltworks Speed / Capacity / Ejector cards, so they read on the board. */
	static Painter outlined(Painter icon) {
		return (x, y, r) -> {
			int c = icon.at(x, y, r);
			if (c != 0) return c;
			if (x < 3 || x > 12 || y < 2 || y > 12) return 0;
			for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				if (icon.at(x + d[0], y + d[1], r) != 0) return rgb(14, 22, 20);
			}
			return 0;
		};
	}

	static void upgrades() throws Exception {
		// Range: four cyan arrows pointing outwards from a gold centre
		png("item/range_upgrade", upgradeCard(outlined((x, y, r) -> {
			int c = rgb(90, 226, 246), hi = rgb(210, 250, 255);
			if ((x == 7 || x == 8) && (y == 7 || y == 8)) return rgb(240, 180, 60);
			boolean midX = x == 7 || x == 8, midY = y == 7 || y == 8;
			if (midX && (y == 3 || y == 12)) return hi;                              // tips
			if (midY && (x == 3 || x == 12)) return hi;
			if ((y == 4 || y == 11) && x >= 6 && x <= 9) return c;                   // heads
			if ((x == 4 || x == 11) && y >= 6 && y <= 9) return c;
			if (midX && (y == 5 || y == 10)) return c;                               // shafts
			if (midY && (x == 5 || x == 10)) return c;
			return 0;
		})));
		// Fortune: a cut lapis gem with facets and a sparkle
		png("item/fortune_upgrade", upgradeCard(outlined((x, y, r) -> {
			if (x == 11 && y == 3) return rgb(255, 255, 255);
			if ((x == 11 && (y == 2 || y == 4)) || (y == 3 && (x == 10 || x == 12))) return rgb(170, 200, 255);
			if (y < 4 || y > 11) return 0;
			int half = y == 4 ? 2 : y == 5 ? 3 : y == 6 ? 4 : 11 - y;
			double dx = x - 7.5;
			if (Math.abs(dx) > half) return 0;
			if (y == 4) return rgb(150, 190, 255);                                   // table
			if (y == 5 || y == 6) return dx < 0 ? rgb(110, 150, 250) : rgb(60, 100, 230);   // crown
			if (Math.abs(dx) < 1) return rgb(70, 110, 236);                          // pavilion facets
			return dx < 0 ? rgb(46, 80, 200) : rgb(28, 52, 150);
		})));
		// Silk Touch: a white feather with a quill, curving across the card
		png("item/silk_touch_upgrade", upgradeCard(outlined((x, y, r) -> {
			double t = (x - 3) / 9.0;
			if (t < 0 || t > 1) return 0;
			double spine = 12 - 8.5 * t;
			if (Math.abs(y - spine) < 0.55) return t < 0.25 ? rgb(200, 160, 90) : rgb(150, 150, 166);
			double w = t < 0.25 ? 0 : 2.8 - 2.6 * Math.abs(t - 0.62);
			if (y < spine && spine - y < w) return (x + y) % 3 == 0 ? rgb(214, 218, 232) : rgb(250, 250, 255);
			if (y > spine && y - spine < w * 0.6) return rgb(206, 210, 224);
			return 0;
		})));
		for (String item : List.of("range_upgrade", "fortune_upgrade", "silk_touch_upgrade")) generatedItem(item);
		shaped("range_upgrade", new String[]{"TCT", "RXR"}, "T", "voltworks:tin_ingot", "R", "minecraft:redstone",
				"C", "voltworks:circuit", "X", "minecraft:ender_pearl");
		shaped("fortune_upgrade", new String[]{"TCT", "RXR"}, "T", "voltworks:tin_ingot", "R", "minecraft:redstone",
				"C", "voltworks:circuit", "X", "minecraft:lapis_block");
		shaped("silk_touch_upgrade", new String[]{"TCT", "RXR"}, "T", "voltworks:tin_ingot", "R", "minecraft:redstone",
				"C", "voltworks:circuit", "X", "minecraft:emerald");

		LANG.put("item." + NS + ".range_upgrade", "Range Upgrade");
		LANG.put("item." + NS + ".fortune_upgrade", "Fortune Upgrade");
		LANG.put("item." + NS + ".silk_touch_upgrade", "Silk Touch Upgrade");
		LANG.put("tooltip." + NS + ".upgrade.range", "Quarry pit +8 blocks wide each (9x9 up to 33x33)");
		LANG.put("tooltip." + NS + ".upgrade.fortune", "One level of Fortune each (max III), +50% power per level");
		LANG.put("tooltip." + NS + ".upgrade.silk_touch", "Blocks drop themselves; double power. Overrides Fortune");
		LANG.put("tooltip." + NS + ".upgrade.hint", "Goes in a Quarry's upgrade slots");
	}

	// ---------------- the gantry: items only used by the renderer ----------------
	/**
	 * While it digs, the quarry draws a steel gantry over its pit: a frame round the edge on corner posts, a bridge
	 * that runs along the rows, a carriage on the bridge and a shaft down to a spinning drill head. Every part is
	 * a small 3D model (an item that is never obtainable), drawn in Voltworks' tier 2 slate with gold trim.
	 */
	static void renderItems() throws Exception {
		// Beam: a 4x4 steel girder one block long. Rows 0-3 are its side (slate, light top edge, dark bottom edge,
		// a lighter channel along the middle and a gold rivet at each end so joints between segments show);
		// x 0-3 of rows 4-7 is its end.
		png("block/gantry_beam", (x, y, r) -> {
			if (y < 4) {
				if (y == 0) return shade(ADV, 34);
				if (y == 3) return shade(ADV, -40);
				if ((x == 1 || x == 14) && y == 1) return rgb(240, 170, 50);
				return y == 1 ? shade(ADV, 10 + r.nextInt(5)) : shade(ADV, -6 + r.nextInt(5));
			}
			if (y < 8 && x < 4) {
				boolean edge = x == 0 || x == 3 || y == 4 || y == 7;
				return edge ? shade(ADV, -30) : shade(ADV, 6);
			}
			return 0;
		});
		write("assets/" + NS + "/models/item/gantry_beam.json", model("gantry_beam", "#beam", "voltworks_quarry:block/gantry_beam",
				box(6, 6, 0, 10, 10, 16, "#beam", side(0, 0, 16, 4, true), side(0, 4, 4, 8, false))));
		itemDef("gantry_beam", NS + ":item/gantry_beam");

		// Joint: a 6x6 block of hazard paint where beams meet and on top of the corner posts
		png("block/gantry_joint", (x, y, r) -> {
			if (x == 0 || y == 0 || x == 15 || y == 15) return rgb(28, 28, 32);
			return ((x + y) / 3) % 2 == 0 ? rgb(232, 180, 40) : rgb(36, 36, 40);
		});
		write("assets/" + NS + "/models/item/gantry_joint.json", model("gantry_joint", "#joint", "voltworks_quarry:block/gantry_joint",
				box(5, 5, 5, 11, 11, 11, "#joint", all(0, 0, 16, 16), all(0, 0, 16, 16))));
		itemDef("gantry_joint", NS + ":item/gantry_joint");

		// Carriage: a slate box riding the bridge, gold rivets, a hazard band, a red lamp on top, and a sleeve hanging under
		// it that the drill shaft slides out of. Texture: casing (whole 16x16) for the body, rows of the sleeve on top.
		png("block/gantry_carriage", (x, y, r) -> {
			if (y == 13 && x > 0 && x < 15) return ((x / 2) % 2 == 0) ? rgb(230, 180, 40) : rgb(30, 30, 30);
			return casing(x, y, r, ADV, rgb(240, 170, 50));
		});
		png("block/gantry_sleeve", (x, y, r) -> {
			if (y == 0 || y == 15) return rgb(240, 170, 50);
			return x % 4 == 0 ? shade(ADV, -36) : shade(ADV, x % 4 == 1 ? 18 : -4);
		});
		png("block/gantry_lamp", (x, y, r) -> x == 0 || y == 0 || x == 15 || y == 15 ? rgb(170, 30, 24)
				: (x < 6 && y < 6 ? rgb(255, 190, 170) : rgb(255, 70, 50)));
		write("assets/" + NS + "/models/item/gantry_carriage.json", """
			{ "textures": { "body": "voltworks_quarry:block/gantry_carriage", "sleeve": "voltworks_quarry:block/gantry_sleeve",
			                "lamp": "voltworks_quarry:block/gantry_lamp", "particle": "voltworks_quarry:block/gantry_carriage" },
			  "elements": [
			""" + String.join(",\n", List.of(
				box(2, 4, 3, 14, 12, 13, "#body", all(0, 2, 16, 14), all(0, 0, 16, 16)),
				box(6, -6, 6, 10, 4, 10, "#sleeve", all(0, 0, 4, 16), all(0, 0, 4, 4)),
				lit(box(6.5, 12, 6.5, 9.5, 13.5, 9.5, "#lamp", all(0, 0, 16, 16), all(0, 0, 16, 16))))) + """
			  ] }
			""");
		itemDef("gantry_carriage", NS + ":item/gantry_carriage");

		// Shaft: a polished steel rod, stretched to any length (the texture is the same all the way down)
		png("block/drill_shaft", (x, y, r) -> switch (x % 4) {
			case 0 -> rgb(110, 116, 126);
			case 1 -> rgb(222, 228, 236);
			case 2 -> rgb(176, 182, 192);
			default -> rgb(140, 146, 156);
		});
		write("assets/" + NS + "/models/item/drill_shaft.json", model("drill_shaft", "#rod", "voltworks_quarry:block/drill_shaft",
				box(6.5, 0, 6.5, 9.5, 16, 9.5, "#rod", all(0, 0, 3, 16), all(0, 0, 3, 3))));
		itemDef("drill_shaft", NS + ":item/drill_shaft");

		// Drill head: a slate motor housing with gold rivets and a gold collar over a stepped steel bit with spiral
		// grooves. The tip is at the model's bottom (y 0), so the renderer can put it on the block being dug.
		png("block/drill_housing", (x, y, r) -> casing(x, y, r, ADV, rgb(240, 170, 50)));
		png("block/drill_collar", (x, y, r) -> x % 2 == 0 ? rgb(240, 190, 60) : rgb(196, 146, 40));
		png("block/drill_bit", (x, y, r) -> {
			int groove = Math.floorMod(x - y, 4);
			if (groove == 0) return rgb(84, 88, 98);
			return groove == 1 ? rgb(226, 232, 240) : rgb(168, 174, 184);
		});
		write("assets/" + NS + "/models/item/drill_head.json", """
			{ "textures": { "housing": "voltworks_quarry:block/drill_housing", "collar": "voltworks_quarry:block/drill_collar",
			                "bit": "voltworks_quarry:block/drill_bit", "particle": "voltworks_quarry:block/drill_housing" },
			  "elements": [
			""" + String.join(",\n", List.of(
				box(4, 9, 4, 12, 15, 12, "#housing", all(0, 4, 16, 16), all(0, 0, 16, 16)),
				box(3.5, 8, 3.5, 12.5, 9, 12.5, "#collar", all(0, 0, 9, 1), all(0, 0, 9, 9)),
				box(5, 6, 5, 11, 8, 11, "#bit", all(0, 0, 6, 2), all(0, 0, 6, 6)),
				box(5.75, 4, 5.75, 10.25, 6, 10.25, "#bit", all(2, 2, 6.5, 4), all(0, 0, 4.5, 4.5)),
				box(6.5, 2, 6.5, 9.5, 4, 9.5, "#bit", all(4, 4, 7, 6), all(0, 0, 3, 3)),
				box(7.25, 0, 7.25, 8.75, 2, 8.75, "#bit", all(6, 6, 7.5, 8), all(0, 0, 1.5, 1.5)))) + """
			  ] }
			""");
		itemDef("drill_head", NS + ":item/drill_head");

		// Laser: no longer drawn (the gantry replaced it); kept so the item id stays registered
		png("item/laser", (x, y, r) -> {
			if (x == 7 || x == 8) return rgb(255, 210, 200);
			if (x == 6 || x == 9) return argb(200, 255, 50, 40);
			if (x == 5 || x == 10) return argb(70, 255, 40, 30);
			return 0;
		});
		generatedItem("laser");
		LANG.put("item." + NS + ".drill_head", "Quarry Drill Head");
		LANG.put("item." + NS + ".laser", "Quarry Laser");
		LANG.put("item." + NS + ".gantry_beam", "Quarry Gantry Beam");
		LANG.put("item." + NS + ".gantry_joint", "Quarry Gantry Joint");
		LANG.put("item." + NS + ".gantry_carriage", "Quarry Gantry Carriage");
		LANG.put("item." + NS + ".drill_shaft", "Quarry Drill Shaft");
	}

	// ---------------- model helpers ----------------
	/** A one-element model; no display transforms, so the renderer places it 1:1 (centred on the block). */
	static String model(String name, String key, String texture, String element) {
		return "{ \"textures\": { " + q(key.substring(1)) + ": " + q(texture) + ", \"particle\": " + q(texture) + " },\n  \"elements\": [\n" + element + "\n  ] }\n";
	}

	/** UVs for the four sides and the two ends of a box. */
	record Faces(String sides, String ends) {}

	static String uv(double u0, double v0, double u1, double v1) {
		return "[" + n(u0) + ", " + n(v0) + ", " + n(u1) + ", " + n(v1) + "]";
	}

	static String n(double d) {
		return d == Math.rint(d) ? String.valueOf((int) d) : String.valueOf(d);
	}

	/** Side UV; {@code alongZ} turns the up/down faces so a beam lying along Z gets its strip lengthways. */
	static String[] side(double u0, double v0, double u1, double v1, boolean alongZ) {
		return new String[]{uv(u0, v0, u1, v1), alongZ ? "90" : "0"};
	}

	static String[] all(double u0, double v0, double u1, double v1) {
		return new String[]{uv(u0, v0, u1, v1), "0"};
	}

	/**
	 * One box. {@code sides} is used for north/south/east/west (and, for a beam along Z, for up/down turned 90°);
	 * {@code ends} for up/down — or, for a beam along Z (sides rotated), for north/south.
	 */
	static String box(double x0, double y0, double z0, double x1, double y1, double z1, String tex, String[] sides, String[] ends) {
		boolean beamZ = sides[1].equals("90");
		String s = "\"uv\": " + sides[0] + ", \"texture\": " + q(tex);
		String e = "\"uv\": " + ends[0] + ", \"texture\": " + q(tex);
		String sr = s + ", \"rotation\": 90";
		String faces = beamZ
				? "\"north\": { " + e + " }, \"south\": { " + e + " }, \"east\": { " + s + " }, \"west\": { " + s + " }, \"up\": { " + sr + " }, \"down\": { " + sr + " }"
				: "\"north\": { " + s + " }, \"south\": { " + s + " }, \"east\": { " + s + " }, \"west\": { " + s + " }, \"up\": { " + e + " }, \"down\": { " + e + " }";
		return "    { \"from\": [" + n(x0) + ", " + n(y0) + ", " + n(z0) + "], \"to\": [" + n(x1) + ", " + n(y1) + ", " + n(z1) + "], \"faces\": { " + faces + " } }";
	}

	/** Makes an element glow (full brightness whatever the light around it). */
	static String lit(String element) {
		return element.replaceFirst("\\{ \"from\"", "{ \"light_emission\": 15, \"from\"");
	}

	// ---------------- translations ----------------
	static void lang() throws Exception {
		LANG.put("gui." + NS + ".status.mining", "Mining");
		LANG.put("gui." + NS + ".status.no_power", "No power");
		LANG.put("gui." + NS + ".status.output_full", "Output full");
		LANG.put("gui." + NS + ".status.paused", "Paused (redstone)");
		LANG.put("gui." + NS + ".status.finished", "Finished");
		LANG.put("gui." + NS + ".status.waiting", "Area not loaded");
		LANG.put("gui." + NS + ".status.area_too_big", "Area too big");
		// The display under the output (each line has to fit in about 90 pixels)
		LANG.put("gui." + NS + ".screen.layer", "Layer Y %s");
		LANG.put("gui." + NS + ".screen.area", "Area %s×%s");
		LANG.put("gui." + NS + ".screen.row", "Row %s of %s");
		LANG.put("gui." + NS + ".screen.mined", "%s mined");
		LANG.put("gui." + NS + ".screen.needs_range", "Add Range");
		LANG.put("gui." + NS + ".screen.bottom", "Reached bottom");
		LANG.put("gui." + NS + ".screen.depth", "Started at Y %s · now Y %s · bottom Y %s");
		// JEI info pages (one entry per paragraph)
		String jei = "jei." + NS + ".info.";
		LANG.put(jei + "quarry.1", "Digs out the 9x9 area behind it (its front faces you when placed), layer by layer, from its own height down to the bottom of the world.");
		LANG.put(jei + "quarry.2", "Needs Voltworks power through Copper Cable: 20 J/t while digging. Harder blocks take longer. Bedrock is skipped, water and lava are cleared, and chests in the way are emptied into the output.");
		LANG.put(jei + "quarry.3", "Fills any chest next to it by itself; Voltworks item pipes pull from it too. A redstone signal pauses it. Takes Range, Fortune and Silk Touch upgrades, plus the Voltworks Speed, Capacity and Ejector upgrades.");
		LANG.put(jei + "range_upgrade.1", "Quarry upgrade: makes the pit 8 blocks wider for each one (9x9, 17x17, 25x25, up to 33x33 with three). The quarry restarts from the top; dug-out air is skipped quickly.");
		LANG.put(jei + "fortune_upgrade.1", "Quarry upgrade: one level of Fortune for each one, up to Fortune III. Each level uses 50% more power. Silk Touch overrides it.");
		LANG.put(jei + "quarry_marker.1", "Sets the area a Quarry digs. Put one marker touching the Quarry and another at the opposite corner; the Quarry picks both up and digs the rectangle between them, corners included, from its own height down.");
		LANG.put(jei + "quarry_marker.2", "The area can be up to 9 blocks wide and long, or 17, 25 or 33 with one, two or three Range Upgrades. Right-click a marker to see which marker it pairs with.");
		LANG.put(jei + "silk_touch_upgrade.1", "Quarry upgrade: mined blocks drop themselves (stone, ores, grass). Uses double power and overrides Fortune.");
		Path guide = res.toAbsolutePath().getParent().getParent().getParent().resolve("tools/guide.txt");
		String[] pages = Files.readString(guide).replace("\r\n", "\n").strip().split("\n---\n");
		for (int i = 0; i < pages.length; i++) LANG.put("guide." + NS + ".page." + (i + 1), pages[i].strip());

		StringBuilder sb = new StringBuilder("{");
		String sep = "\n  ";
		for (var e : LANG.entrySet()) {
			sb.append(sep).append(jsonString(e.getKey())).append(": ").append(jsonString(e.getValue()));
			sep = ",\n  ";
		}
		write("assets/" + NS + "/lang/en_us.json", sb.append("\n}\n").toString());
	}

	// ---------------- mod icon ----------------
	/** Blue steel plate (as the Voltworks logo), a stepped pit, and a drill on a red laser digging into it. */
	static void icon(Path root) throws Exception {
		int s = 400;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		double u = s / 100.0;
		RoundRectangle2D plate = new RoundRectangle2D.Double(3 * u, 3 * u, 94 * u, 94 * u, 22 * u, 22 * u);
		g.setPaint(new GradientPaint(0, (float) (3 * u), new Color(78, 96, 122), 0, (float) (97 * u), new Color(34, 42, 56)));
		g.fill(plate);
		g.setStroke(new BasicStroke((float) (3 * u)));
		g.setColor(new Color(20, 24, 32));
		g.draw(plate);
		// The pit: stepped terraces, darker deeper down
		g.setClip(new RoundRectangle2D.Double(6 * u, 6 * u, 88 * u, 88 * u, 18 * u, 18 * u));
		int[][] steps = {{12, 52, 76}, {20, 62, 60}, {28, 72, 44}, {36, 82, 28}};
		g.setColor(new Color(120, 98, 72));
		g.fill(new Rectangle2D.Double(0, 46 * u, s, 54 * u));
		g.setColor(new Color(92, 170, 70));
		g.fill(new Rectangle2D.Double(0, 44 * u, s, 4 * u));
		for (int i = 0; i < steps.length; i++) {
			int c = 70 - i * 14;
			g.setColor(new Color(c + 20, c + 14, c + 8));
			g.fill(new Rectangle2D.Double(steps[i][0] * u, (steps[i][1] - 6) * u, steps[i][2] * u, 60 * u));
		}
		g.setClip(null);
		// Laser and drill
		g.setColor(new Color(255, 60, 40, 110));
		g.fill(new Rectangle2D.Double(46 * u, 8 * u, 8 * u, 62 * u));
		g.setColor(new Color(255, 200, 190));
		g.fill(new Rectangle2D.Double(48.5 * u, 8 * u, 3 * u, 62 * u));
		Path2D bit = new Path2D.Double();
		bit.moveTo(38 * u, 58 * u);
		bit.lineTo(62 * u, 58 * u);
		bit.lineTo(50 * u, 82 * u);
		bit.closePath();
		g.setPaint(new GradientPaint((float) (38 * u), 0, new Color(225, 230, 238), (float) (62 * u), 0, new Color(140, 146, 156)));
		g.fill(bit);
		g.setColor(new Color(90, 94, 104));
		g.setStroke(new BasicStroke((float) (2 * u)));
		for (int i = 0; i < 3; i++) g.draw(new Line2D.Double((40 + i * 4) * u, (62 + i * 6) * u, (58 - i * 2) * u, (60 + i * 6) * u));
		g.setColor(new Color(240, 190, 60));
		g.fill(new RoundRectangle2D.Double(36 * u, 52 * u, 28 * u, 7 * u, 3 * u, 3 * u));
		g.setColor(new Color(20, 24, 32));
		g.setStroke(new BasicStroke((float) (1.5 * u)));
		g.draw(bit);
		g.dispose();
		File big = root.resolve("docs/images/logo.png").toFile();
		big.getParentFile().mkdirs();
		ImageIO.write(img, "png", big);
		BufferedImage small = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = small.createGraphics();
		sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		sg.drawImage(img, 0, 0, 128, 128, null);
		sg.dispose();
		File f = res.resolve("assets/" + NS + "/icon.png").toFile();
		f.getParentFile().mkdirs();
		ImageIO.write(small, "png", f);
	}

	// ---------------- helpers ----------------
	interface Painter { int at(int x, int y, Random r); }

	static int rgb(int r, int g, int b) { return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
	static int argb(int a, int r, int g, int b) { return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
	static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
	static int shade(int c, int d) { return rgb(((c >> 16) & 255) + d, ((c >> 8) & 255) + d, (c & 255) + d); }
	static boolean inRect(int x, int y, int x0, int y0, int x1, int y1) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }

	/** Voltworks' tier 2 casing colours, so the quarry sits with the Industrial Crusher and friends. */
	static final int ADV = rgb(64, 78, 98);

	static int casing(int x, int y, Random r, int base, int rivet) {
		if (x == 0 || y == 0) return shade(base, 40);
		if (x == 15 || y == 15) return shade(base, -50);
		if (x == 1 || y == 1 || x == 14 || y == 14) return shade(base, -12);
		if ((x == 2 || x == 13) && (y == 2 || y == 13)) return rivet;
		return shade(base, r.nextInt(9) - 4);
	}

	static int casing2(int x, int y, Random r) {
		if (y == 14 && x > 0 && x < 15) return ((x / 2) % 2 == 0) ? rgb(230, 180, 40) : rgb(30, 30, 30);
		return casing(x, y, r, ADV, rgb(240, 170, 50));
	}

	static void png(String path, Painter p) throws Exception {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(path.hashCode());
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setRGB(x, y, p.at(x, y, r));
		File f = res.resolve("assets/" + NS + "/textures/" + path + ".png").toFile();
		f.getParentFile().mkdirs();
		ImageIO.write(img, "png", f);
	}

	static void write(String path, String json) throws Exception {
		Path p = res.resolve(path);
		Files.createDirectories(p.getParent());
		Files.writeString(p, json);
	}

	static String q(String s) { return "\"" + s + "\""; }

	static String jsonString(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}

	static void itemDef(String name, String model) throws Exception {
		write("assets/" + NS + "/items/" + name + ".json", "{ \"model\": { \"type\": \"minecraft:model\", \"model\": " + q(model) + " } }");
	}

	static void generatedItem(String item) throws Exception {
		write("assets/" + NS + "/models/item/" + item + ".json",
				"{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"" + NS + ":item/" + item + "\" } }");
		itemDef(item, NS + ":item/" + item);
	}

	static void shaped(String result, String[] pattern, String... keys) throws Exception {
		shapedCount(result, 1, pattern, keys);
	}

	static void shapedCount(String result, int count, String[] pattern, String... keys) throws Exception {
		List<String> k = new ArrayList<>();
		for (int i = 0; i < keys.length; i += 2) k.add(q(keys[i]) + ": " + q(keys[i + 1]));
		List<String> p = new ArrayList<>();
		for (String row : pattern) p.add(q(row));
		write("data/" + NS + "/recipe/" + result + ".json",
				"{ \"type\": \"minecraft:crafting_shaped\",\n  \"pattern\": [" + String.join(", ", p) + "],\n  \"key\": { "
						+ String.join(", ", k) + " },\n  \"result\": { \"id\": \"" + NS + ":" + result + "\", \"count\": " + count + " } }");
	}

	static void tag(String kind, String ns, String path, String... values) throws Exception {
		List<String> v = new ArrayList<>();
		for (String s : values) v.add(q(s));
		write("data/" + ns + "/tags/" + kind + "/" + path + ".json", "{ \"replace\": false, \"values\": [" + String.join(", ", v) + "] }");
	}
}
