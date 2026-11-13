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
	/** A small post: a hazard-striped steel stem with a red lamp on top. */
	static void marker() throws Exception {
		png("block/quarry_marker_post", (x, y, r) -> ((x + y) / 2) % 2 == 0 ? rgb(230, 180, 40) : rgb(36, 36, 40));
		png("block/quarry_marker_lamp", (x, y, r) -> {
			boolean edge = x == 0 || y == 0 || x == 3 || y == 3;
			return edge ? rgb(170, 30, 24) : (x == 1 && y == 1 ? rgb(255, 200, 180) : rgb(255, 70, 50));
		});
		write("assets/" + NS + "/models/block/quarry_marker.json", """
			{ "parent": "minecraft:block/block", "ambientocclusion": false,
			  "textures": { "post": "voltworks_quarry:block/quarry_marker_post", "lamp": "voltworks_quarry:block/quarry_marker_lamp",
			                "particle": "voltworks_quarry:block/quarry_marker_lamp" },
			  "elements": [
			    { "from": [7, 0, 7], "to": [9, 9, 9], "faces": {
			      "north": { "uv": [0, 0, 2, 9], "texture": "#post" }, "south": { "uv": [0, 0, 2, 9], "texture": "#post" },
			      "east": { "uv": [0, 0, 2, 9], "texture": "#post" }, "west": { "uv": [0, 0, 2, 9], "texture": "#post" },
			      "down": { "uv": [0, 0, 2, 2], "texture": "#post", "cullface": "down" } } },
			    { "from": [6, 9, 6], "to": [10, 12, 10], "faces": {
			      "north": { "uv": [0, 0, 4, 3], "texture": "#lamp" }, "south": { "uv": [0, 0, 4, 3], "texture": "#lamp" },
			      "east": { "uv": [0, 0, 4, 3], "texture": "#lamp" }, "west": { "uv": [0, 0, 4, 3], "texture": "#lamp" },
			      "up": { "uv": [0, 0, 4, 4], "texture": "#lamp" }, "down": { "uv": [0, 0, 4, 4], "texture": "#lamp" } } } ],
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

	static void upgrades() throws Exception {
		// Range: four cyan arrows pointing outwards from a square
		png("item/range_upgrade", upgradeCard((x, y, r) -> {
			int c = rgb(80, 220, 240);
			if (inRect(x, y, 6, 6, 9, 9) && (x == 6 || x == 9 || y == 6 || y == 9)) return rgb(200, 200, 205);
			if ((x == 7 || x == 8) && (y == 3 || y == 12)) return c;
			if ((y == 7 || y == 8) && (x == 3 || x == 12)) return c;
			if ((y == 4 || y == 11) && x >= 6 && x <= 9) return c;
			if ((x == 4 || x == 11) && y >= 6 && y <= 9) return c;
			return 0;
		}));
		// Fortune: a blue gem with a light facet
		png("item/fortune_upgrade", upgradeCard((x, y, r) -> {
			int dy = y - 3;
			if (y >= 3 && y <= 11) {
				int half = y <= 5 ? 1 + dy : Math.max(0, 11 - y);
				if (Math.abs(x - 7.5) <= half + 0.5) {
					if (y == 3 || Math.abs(x - 7.5) > half - 0.5) return rgb(20, 40, 120);
					return (x < 8 && y < 7) ? rgb(130, 170, 255) : rgb(50, 90, 220);
				}
			}
			return 0;
		}));
		// Silk Touch: a white feather curving across the card
		png("item/silk_touch_upgrade", upgradeCard((x, y, r) -> {
			double t = (x - 3) / 9.0;
			if (t < 0 || t > 1) return 0;
			double spine = 11 - 7 * t;
			if (Math.abs(y - spine) < 0.6) return rgb(170, 170, 180);
			if (t > 0.2 && y < spine && spine - y < 2.6 - 1.5 * Math.abs(t - 0.6)) return rgb(245, 245, 250);
			if (t > 0.2 && y > spine && y - spine < 1.6) return rgb(220, 220, 230);
			return 0;
		}));
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

	// ---------------- items only used by the renderer ----------------
	static void renderItems() throws Exception {
		// Drill head: a steel cone pointing down, with a gold collar and spiral grooves
		png("item/drill_head", (x, y, r) -> {
			if (y >= 1 && y <= 3 && x >= 5 && x <= 10) return y == 1 ? rgb(90, 94, 102) : rgb(60, 64, 72);  // hub
			if (y == 4 && x >= 3 && x <= 12) return x % 2 == 0 ? rgb(240, 190, 60) : rgb(200, 150, 40);  // collar
			if (y >= 5 && y <= 15) {
				double half = 4.5 * (15 - y) / 10.0;
				if (Math.abs(x - 7.5) <= half + 0.5) {
					if ((x + y) % 4 == 0) return rgb(96, 100, 110);
					return x < 8 ? rgb(214, 220, 228) : rgb(160, 166, 176);
				}
			}
			return 0;
		});
		// Laser: a bright red core with a soft glow either side
		png("item/laser", (x, y, r) -> {
			if (x == 7 || x == 8) return rgb(255, 210, 200);
			if (x == 6 || x == 9) return argb(200, 255, 50, 40);
			if (x == 5 || x == 10) return argb(70, 255, 40, 30);
			return 0;
		});
		generatedItem("drill_head");
		generatedItem("laser");
		LANG.put("item." + NS + ".drill_head", "Quarry Drill Head");
		LANG.put("item." + NS + ".laser", "Quarry Laser");
	}

	// ---------------- translations ----------------
	static void lang() throws Exception {
		LANG.put("gui." + NS + ".status.mining", "Mining");
		LANG.put("gui." + NS + ".status.no_power", "No power");
		LANG.put("gui." + NS + ".status.output_full", "Output full");
		LANG.put("gui." + NS + ".status.paused", "Paused (redstone)");
		LANG.put("gui." + NS + ".status.finished", "Finished");
		LANG.put("gui." + NS + ".status.waiting", "Area not loaded");
		LANG.put("gui." + NS + ".info", "Y %s · %sx%s · %s mined");
		LANG.put("gui." + NS + ".info_done", "%sx%s · %s mined");
		LANG.put("gui." + NS + ".status.area_too_big", "Area too big");
		LANG.put("gui." + NS + ".info_too_big", "%sx%s marked: add Range");
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
