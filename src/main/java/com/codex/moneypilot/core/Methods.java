package com.codex.moneypilot.core;

import static com.codex.moneypilot.core.Method.Category.COLLECTING;
import static com.codex.moneypilot.core.Method.Category.COMBAT;
import static com.codex.moneypilot.core.Method.Category.PROCESSING;
import static com.codex.moneypilot.core.Method.Category.SKILLING;
import static com.codex.moneypilot.core.Need.combat;
import static com.codex.moneypilot.core.Need.diary;
import static com.codex.moneypilot.core.Need.note;
import static com.codex.moneypilot.core.Need.quest;
import static com.codex.moneypilot.core.Need.skill;
import static com.codex.moneypilot.core.Need.started;
import static com.codex.moneypilot.core.Need.total;
import static net.runelite.api.Skill.AGILITY;
import static net.runelite.api.Skill.ATTACK;
import static net.runelite.api.Skill.CONSTRUCTION;
import static net.runelite.api.Skill.COOKING;
import static net.runelite.api.Skill.CRAFTING;
import static net.runelite.api.Skill.DEFENCE;
import static net.runelite.api.Skill.FARMING;
import static net.runelite.api.Skill.FIREMAKING;
import static net.runelite.api.Skill.FISHING;
import static net.runelite.api.Skill.HERBLORE;
import static net.runelite.api.Skill.HITPOINTS;
import static net.runelite.api.Skill.HUNTER;
import static net.runelite.api.Skill.MAGIC;
import static net.runelite.api.Skill.MINING;
import static net.runelite.api.Skill.PRAYER;
import static net.runelite.api.Skill.RANGED;
import static net.runelite.api.Skill.RUNECRAFT;
import static net.runelite.api.Skill.SAILING;
import static net.runelite.api.Skill.SLAYER;
import static net.runelite.api.Skill.SMITHING;
import static net.runelite.api.Skill.STRENGTH;
import static net.runelite.api.Skill.THIEVING;
import static net.runelite.api.Skill.WOODCUTTING;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.gameval.VarbitID;

/**
 * The money makers Money Pilot knows. Each one is a page of the Old School wiki's money making guide
 * (oldschool.runescape.wiki/w/Money_making_guide/...), and its needs, suggestions and profit an hour
 * were read from that page on 3 November 2026 (the Sailing methods on 4 November 2026). A level is a need
 * only where the game checks it (an altar, a door, a slayer level, a spell) or the guide marks it required;
 * the rest are suggestions.
 */
public final class Methods
{
	/** When the wiki's figures were read. */
	public static final String CHECKED = "Nov 2026";

	private static final List<Method> ALL;
	private static final List<Method> RECURRING;
	private static final Map<String, Method> BY_ID = new HashMap<>();

	private static final Need TROLL_STRONGHOLD = started(Quest.TROLL_STRONGHOLD, "Troll Stronghold (Dad defeated)");

	static
	{
		List<Method> m = new ArrayList<>();

		// ---- Combat ----
		m.add(new Method("chambers-of-xeric", "Chambers of Xeric (Normal Mode)", "Chambers of Xeric (Normal Mode)", COMBAT,
			7_807_848, "3 raids an hour")
			.suggest(skill(ATTACK, 90), skill(PRAYER, 74))
			.match("Chambers of Xeric"));
		m.add(new Method("vardorvis", "Killing Vardorvis", "Killing Vardorvis", COMBAT, 6_709_626, "32 kills an hour")
			.need(quest(Quest.DESERT_TREASURE_II__THE_FALLEN_EMPIRE))
			.suggest(skill(ATTACK, 90), skill(STRENGTH, 90), skill(DEFENCE, 90), skill(PRAYER, 70))
			.match("Vardorvis"));
		m.add(new Method("muspah-tbow", "Killing Phantom Muspah", "Killing Phantom Muspah (Twisted bow)", COMBAT,
			5_799_215, "25 kills an hour, twisted bow")
			.need(quest(Quest.SECRETS_OF_THE_NORTH))
			.suggest(skill(RANGED, 90), skill(DEFENCE, 80), skill(PRAYER, 74), skill(MAGIC, 85))
			.match("Phantom Muspah"));
		m.add(new Method("kreearra", "Killing Kree'arra", "Killing Kree'arra", COMBAT, 4_301_296, "27 kills an hour")
			// Armadyl's Eyrie: "Ranged 70", which cannot be boosted, and a crossbow with a mithril grapple.
			.need(skill(RANGED, 70), TROLL_STRONGHOLD)
			.suggest(skill(RANGED, 80), skill(PRAYER, 74), note("Crossbow and mithril grapple to enter"))
			.match("Kree'arra"));
		m.add(new Method("demonic-gorillas", "Killing demonic gorillas", "Killing demonic gorillas", COMBAT, 3_944_238,
			"70 kills an hour")
			.need(quest(Quest.MONKEY_MADNESS_II))
			.suggest(skill(ATTACK, 80), skill(STRENGTH, 85), skill(DEFENCE, 80), skill(HITPOINTS, 80), skill(RANGED, 85),
				skill(MAGIC, 70), skill(PRAYER, 70))
			.match("Demonic gorilla"));
		m.add(new Method("corrupted-gauntlet", "Completing The Corrupted Gauntlet", "Completing The Corrupted Gauntlet",
			COMBAT, 3_690_162, "6 completions an hour")
			.need(quest(Quest.SONG_OF_THE_ELVES))
			.suggest(skill(ATTACK, 80), skill(STRENGTH, 85), skill(RANGED, 85), skill(MAGIC, 80), skill(PRAYER, 77))
			.match("Corrupted Hunllef", "The Corrupted Gauntlet", "Corrupted Gauntlet"));
		m.add(new Method("hydra", "Killing the Alchemical Hydra", "Killing the Alchemical Hydra", COMBAT, 3_594_219,
			"25 kills an hour")
			.need(skill(SLAYER, 95), note("A hydra slayer task"))
			.suggest(skill(RANGED, 75), skill(PRAYER, 74), skill(HITPOINTS, 85))
			.match("Alchemical Hydra"));
		m.add(new Method("vorkath", "Killing Vorkath", "Killing Vorkath (Dragon hunter crossbow)", COMBAT, 3_439_802,
			"30 kills an hour, dragon hunter crossbow")
			.need(quest(Quest.DRAGON_SLAYER_II))
			.suggest(skill(RANGED, 90), skill(DEFENCE, 80), skill(PRAYER, 74))
			.match("Vorkath"));
		m.add(new Method("tombs-expert", "Tombs of Amascut (Expert)", "Tombs of Amascut (Expert)", COMBAT, 3_267_121,
			"1.75 raids an hour, solo")
			.need(quest(Quest.BENEATH_CURSED_SANDS))
			.suggest(skill(ATTACK, 82), skill(STRENGTH, 85), skill(DEFENCE, 80), skill(HITPOINTS, 85), skill(RANGED, 90),
				skill(MAGIC, 94), skill(PRAYER, 77))
			.match("Tombs of Amascut"));
		m.add(new Method("zilyana", "Killing Commander Zilyana", "Killing Commander Zilyana", COMBAT, 3_188_681,
			"27 kills an hour")
			.need(skill(AGILITY, 70), TROLL_STRONGHOLD)
			.suggest(combat(100), skill(RANGED, 80), skill(DEFENCE, 90), skill(HITPOINTS, 70), skill(PRAYER, 74))
			.match("Commander Zilyana"));
		m.add(new Method("graardor", "Killing General Graardor", "Killing General Graardor", COMBAT, 3_138_744,
			"27 kills an hour")
			.need(skill(STRENGTH, 70), skill(PRAYER, 43), TROLL_STRONGHOLD)
			.suggest(skill(ATTACK, 80), skill(DEFENCE, 80), skill(MAGIC, 75), skill(PRAYER, 70))
			.match("General Graardor"));
		m.add(new Method("corporeal-beast", "Killing the Corporeal Beast", "Killing the Corporeal Beast", COMBAT,
			3_114_823, "10 kills an hour")
			.suggest(note("High melee stats"), skill(PRAYER, 37))
			.match("Corporeal Beast"));
		m.add(new Method("revenants-msb", "Killing revenants", "Killing revenants (Magic shortbow)", COMBAT, 2_988_467,
			"110 kills an hour, magic shortbow")
			.wilderness()
			.suggest(skill(DEFENCE, 45), skill(RANGED, 80), skill(PRAYER, 43))
			.match("Revenant*"));
		m.add(new Method("cerberus", "Killing Cerberus", "Killing Cerberus", COMBAT, 2_766_966, null)
			.need(skill(SLAYER, 91), note("A hellhound slayer task"))
			.suggest(skill(ATTACK, 85), skill(PRAYER, 70))
			.match("Cerberus"));
		m.add(new Method("zulrah", "Killing Zulrah", "Killing Zulrah", COMBAT, 2_627_241, "before supplies")
			.need(started(Quest.REGICIDE, "Started Regicide"))
			.suggest(skill(RANGED, 80), skill(MAGIC, 85), skill(DEFENCE, 70), skill(PRAYER, 45))
			.match("Zulrah"));
		m.add(new Method("dagannoth-kings", "Killing Dagannoth Kings (solo tribrid)", "Killing Dagannoth Kings (Solo tribrid)",
			COMBAT, 1_991_199, "25 kills of each king an hour")
			.need(quest(Quest.THE_FREMENNIK_TRIALS))
			.suggest(skill(ATTACK, 85), skill(RANGED, 85), skill(MAGIC, 85), skill(DEFENCE, 70), skill(PRAYER, 74),
				skill(AGILITY, 85))
			.match("Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"));
		m.add(new Method("scorpia", "Killing Scorpia", "Killing Scorpia", COMBAT, 1_889_732, "72 kills an hour")
			.wilderness()
			.suggest(skill(MAGIC, 82), skill(PRAYER, 40))
			.match("Scorpia"));
		m.add(new Method("gauntlet", "Completing The Gauntlet", "Completing The Gauntlet", COMBAT, 1_785_305,
			"7 completions an hour")
			.need(quest(Quest.SONG_OF_THE_ELVES))
			.suggest(skill(ATTACK, 80), skill(STRENGTH, 85), skill(RANGED, 85), skill(MAGIC, 80), skill(PRAYER, 77))
			.match("Crystalline Hunllef", "The Gauntlet", "Gauntlet"));
		m.add(new Method("zombie-pirates", "Killing zombie pirates", "Killing zombie pirates (Budget)", COMBAT, 1_669_465,
			"400 kills an hour")
			.wilderness()
			.suggest(skill(ATTACK, 60), skill(PRAYER, 37))
			.match("Zombie pirate"));
		m.add(new Method("brutal-black-dragons", "Killing brutal black dragons", "Killing brutal black dragons", COMBAT,
			1_534_360, "65 kills an hour")
			// The monster's own page: "Slayer level 77" to damage them.
			.need(skill(SLAYER, 77))
			.suggest(skill(PRAYER, 37), skill(CONSTRUCTION, 45), skill(RANGED, 85))
			.match("Brutal black dragon"));
		m.add(new Method("rune-dragons", "Killing rune dragons", "Killing rune dragons", COMBAT, 1_516_442,
			"about 50 kills an hour")
			.need(quest(Quest.DRAGON_SLAYER_II))
			.suggest(skill(HITPOINTS, 90), skill(MAGIC, 75))
			.match("Rune dragon"));
		m.add(new Method("frost-dragons", "Killing frost dragons", "Killing Frost dragons (on-task)", COMBAT, 1_992_179,
			"143 kills an hour, on a slayer task")
			// Frost dragons live in Grimstone Dungeon; the monster's page: "87 Sailing to access".
			.need(skill(SAILING, 87))
			.suggest(skill(MAGIC, 75), skill(ATTACK, 80), skill(STRENGTH, 80), skill(PRAYER, 43),
				note("A frost dragon slayer task"))
			.match("Frost dragon"));
		m.add(new Method("moons-of-peril", "Moons of Peril", "Moons of Peril", COMBAT, 1_345_861, "10 kills an hour")
			.need(quest(Quest.PERILOUS_MOONS))
			.suggest(skill(ATTACK, 70), skill(STRENGTH, 70), skill(DEFENCE, 70), skill(PRAYER, 70), skill(HUNTER, 20),
				skill(COOKING, 30), skill(HERBLORE, 38))
			.match("Lunar Chest", "Blue Moon", "Eclipse Moon", "Blood Moon"));
		m.add(new Method("kril", "Killing K'ril Tsutsaroth", "Killing K'ril Tsutsaroth", COMBAT, 1_324_420,
			"26 kills an hour")
			.need(skill(HITPOINTS, 70), TROLL_STRONGHOLD)
			.suggest(skill(ATTACK, 80), skill(DEFENCE, 90), skill(MAGIC, 75), skill(PRAYER, 70))
			.match("K'ril Tsutsaroth"));
		m.add(new Method("lizardman-shamans", "Killing Lizardman shamans", "Killing Lizardman Shamans (Canyon)", COMBAT,
			1_012_059, "180 kills an hour")
			.suggest(skill(RANGED, 75), skill(PRAYER, 70), quest(Quest.DWARF_CANNON))
			.match("Lizardman shaman"));
		m.add(new Method("green-dragons", "Killing green dragons", "Killing green dragons", COMBAT, 889_226,
			"180 kills an hour in the Wilderness")
			.wilderness()
			.suggest(combat(80), skill(RANGED, 60), skill(PRAYER, 25), quest(Quest.DRAGON_SLAYER_I))
			.match("Green dragon"));
		m.add(new Method("gargoyles", "Killing gargoyles", "Killing gargoyles", COMBAT, 853_766, "230 kills an hour")
			.need(skill(SLAYER, 75), quest(Quest.PRIEST_IN_PERIL))
			.suggest(skill(ATTACK, 70), skill(DEFENCE, 85), skill(MAGIC, 55))
			.match("Gargoyle"));
		m.add(new Method("kraken", "Killing the Kraken", "Killing the Kraken", COMBAT, 843_734, "60 kills an hour")
			.need(skill(SLAYER, 87))
			.suggest(skill(MAGIC, 85))
			.match("Kraken"));
		m.add(new Method("barrows", "Barrows", "Barrows", COMBAT, 612_413, "12 chests an hour")
			.need(quest(Quest.PRIEST_IN_PERIL))
			.suggest(skill(PRAYER, 43), skill(ATTACK, 70), skill(MAGIC, 50), skill(RANGED, 50))
			.match("Barrows"));
		m.add(new Method("sarachnis", "Killing Sarachnis", "Killing Sarachnis", COMBAT, 458_799, "30 kills an hour")
			.suggest(skill(ATTACK, 70), skill(STRENGTH, 70), skill(DEFENCE, 70), skill(PRAYER, 70))
			.match("Sarachnis"));
		m.add(new Method("king-black-dragon", "Killing the King Black Dragon", "Killing the King Black Dragon", COMBAT,
			275_309, "30 kills an hour")
			.suggest(skill(ATTACK, 80), skill(RANGED, 70), skill(HITPOINTS, 70), skill(PRAYER, 70))
			.match("King Black Dragon"));

		// ---- Skilling ----
		m.add(new Method("elves", "Pickpocketing elves", "Pickpocketing elves", SKILLING, 3_883_071,
			"566 pickpockets an hour")
			.skill(THIEVING)
			.need(skill(THIEVING, 85), quest(Quest.SONG_OF_THE_ELVES))
			.suggest(skill(AGILITY, 50), skill(MAGIC, 47), diary("Hard Ardougne Diary", VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE)));
		m.add(new Method("blood-runes", "Crafting blood runes", "Crafting blood runes", SKILLING, 3_353_542,
			"about 75 trips an hour")
			.skill(RUNECRAFT)
			.need(skill(RUNECRAFT, 77), quest(Quest.SINS_OF_THE_FATHER))
			.suggest(skill(FIREMAKING, 62)));
		m.add(new Method("vyres", "Pickpocketing vyres", "Pickpocketing vyres", SKILLING, 2_845_913,
			"722 pickpockets an hour")
			.skill(THIEVING)
			.need(skill(THIEVING, 82), quest(Quest.SINS_OF_THE_FATHER))
			.suggest(skill(AGILITY, 50), skill(MAGIC, 47), diary("Hard Ardougne Diary", VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE)));
		m.add(new Method("wrath-runes", "Crafting wrath runes", "Crafting wrath runes", SKILLING, 2_763_024, null)
			.skill(RUNECRAFT)
			.need(skill(RUNECRAFT, 95), quest(Quest.DRAGON_SLAYER_II)));
		m.add(new Method("blood-runes-abyss", "Crafting blood runes through the Abyss", "Crafting blood runes through the Abyss",
			SKILLING, 2_644_497, "50 to 60 trips an hour")
			.skill(RUNECRAFT)
			.need(skill(RUNECRAFT, 77), quest(Quest.SINS_OF_THE_FATHER), quest(Quest.ENTER_THE_ABYSS))
			.suggest(skill(FIREMAKING, 62)));
		m.add(new Method("runite-bars-bf", "Smelting runite bars at Blast Furnace", "Smelting runite bars at Blast Furnace",
			SKILLING, 2_589_188, "1,850 to 2,150 bars an hour")
			.skill(SMITHING).trade()
			.need(skill(SMITHING, 85), started(Quest.THE_GIANT_DWARF, "Started The Giant Dwarf")));
		m.add(new Method("rogues-castle", "Stealing from Rogues' Castle chests", "Stealing from Rogues' Castle chests",
			SKILLING, 2_324_162, "385 chests an hour")
			.skill(THIEVING).wilderness()
			.need(skill(THIEVING, 84), skill(PRAYER, 43), diary("Medium Wilderness Diary", VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE))
			.suggest(diary("Hard Wilderness Diary", VarbitID.WILDERNESS_DIARY_HARD_COMPLETE)));
		m.add(new Method("adamantite-bars-bf", "Smelting adamantite bars at Blast Furnace",
			"Smelting adamantite bars at Blast Furnace", SKILLING, 2_284_380, "2,400 to 2,700 bars an hour")
			.skill(SMITHING).trade()
			.need(skill(SMITHING, 70), started(Quest.THE_GIANT_DWARF, "Started The Giant Dwarf")));
		m.add(new Method("tan-leather", "Casting Tan Leather", "Casting Tan Leather", SKILLING, 2_208_000,
			"about 8,000 hides an hour")
			.skill(MAGIC).trade()
			.need(skill(MAGIC, 78), quest(Quest.LUNAR_DIPLOMACY), diary("Hard Fremennik Diary", VarbitID.FREMENNIK_DIARY_HARD_COMPLETE)));
		m.add(new Method("mastering-mixology", "Mastering Mixology", "Mastering Mixology", SKILLING, 2_192_145, null)
			.skill(HERBLORE)
			.need(skill(HERBLORE, 60), quest(Quest.CHILDREN_OF_THE_SUN))
			.suggest(skill(HERBLORE, 81)));
		m.add(new Method("death-runes-abyss", "Crafting death runes through the Abyss", "Crafting death runes through the Abyss",
			SKILLING, 2_037_258, "about 50 trips an hour")
			.skill(RUNECRAFT)
			.need(skill(RUNECRAFT, 65), quest(Quest.ENTER_THE_ABYSS), quest(Quest.MOURNINGS_END_PART_II))
			.suggest(skill(FIREMAKING, 75)));
		m.add(new Method("astral-runes", "Crafting astral runes", "Crafting astral runes", SKILLING, 2_008_701,
			"73 trips an hour at 85 Runecraft")
			.skill(RUNECRAFT)
			// The guide: "Runecraft 85 (82 will slightly reduce profit, 40 will significantly)"; 40 is the altar.
			.need(skill(RUNECRAFT, 40), quest(Quest.LUNAR_DIPLOMACY))
			.suggest(skill(RUNECRAFT, 85), skill(FIREMAKING, 75), skill(MAGIC, 69)));
		m.add(new Method("trawling-marlin", "Deep sea trawling for marlin", "Deep sea trawling for marlin", SKILLING,
			2_005_922, "650 marlin an hour, a sloop with two cotton nets")
			.skill(FISHING)
			// The guide's requirements: Sailing 78, Fishing 91 (marlin shoals, boostable), Construction 72 and Pandemonium.
			.need(skill(SAILING, 78), skill(FISHING, 91), skill(CONSTRUCTION, 72), quest(Quest.PANDEMONIUM))
			.suggest(skill(SAILING, 92), skill(CONSTRUCTION, 84), note("A sloop, two cotton trawling nets and an eternal brazier")));
		m.add(new Method("runite-ore-f2p", "Mining runite ore (free-to-play)", "Mining runite ore (free-to-play)",
			SKILLING, 389_920, "about 40 ores an hour")
			.f2p().skill(MINING)
			.need(skill(MINING, 85)));

		// ---- Processing ----
		m.add(new Method("raw-yellowfin", "Cutting raw yellowfin", "Cutting raw yellowfin", PROCESSING, 3_058_000,
			"about 11,000 fish an hour with a chum spreader")
			.trade()
			// Cut on a boat's chum facility. The guide gives Sailing as "varies": the chum station is built at
			// Sailing 56 and Construction 45; its figure is for the chum spreader (Sailing 82, Construction 74).
			.need(skill(SAILING, 56), skill(CONSTRUCTION, 45))
			.suggest(skill(SAILING, 82), skill(CONSTRUCTION, 74), skill(COOKING, 99)));
		m.add(new Method("sanfew-serum", "Making sanfew serum(4)", "Making sanfew serum(4)", PROCESSING, 2_439_000, null)
			.trade()
			.need(skill(HERBLORE, 65), quest(Quest.DRUIDIC_RITUAL), quest(Quest.ZOGRE_FLESH_EATERS)));
		m.add(new Method("crystal-keys", "Creating crystal keys", "Creating crystal keys", PROCESSING, 2_410_000,
			"one key a tick")
			.trade());
		m.add(new Method("unicorn-horns", "Grinding unicorn horns", "Grinding unicorn horns", PROCESSING, 1_962_000,
			"18,000 horns an hour, high attention")
			.trade());
		m.add(new Method("ranarr-potions", "Making ranarr potions", "Making ranarr potions", PROCESSING, 1_252_000,
			"about 4,100 potions an hour")
			.trade()
			.need(skill(HERBLORE, 30), quest(Quest.DRUIDIC_RITUAL)));
		m.add(new Method("black-dragonhide", "Tanning black dragonhide", "Tanning black dragonhide", PROCESSING, 1_225_942,
			"about 2,800 hides an hour")
			.trade());
		m.add(new Method("snapdragon-potions", "Making snapdragon potions", "Making snapdragon potions", PROCESSING,
			1_036_000, "about 4,400 potions an hour")
			.trade()
			.need(skill(HERBLORE, 63), quest(Quest.DRUIDIC_RITUAL)));
		m.add(new Method("birdhouse-traps", "Making birdhouse traps", "Making birdhouse traps", PROCESSING, 662_000, null)
			.trade()
			.need(skill(CRAFTING, 25))
			.suggest(note("A hammer and a chisel")));
		m.add(new Method("mahogany-planks", "Making mahogany planks", "Making mahogany planks", PROCESSING, 617_760, null)
			.trade()
			.need(quest(Quest.CHILDREN_OF_THE_SUN)));
		m.add(new Method("bow-strings", "Spinning flax to bow strings", "Spinning flax to bow strings", PROCESSING, 331_200,
			"2,000 an hour")
			.trade()
			.need(skill(CRAFTING, 10))
			.suggest(quest(Quest.CHILDREN_OF_THE_SUN)));
		m.add(new Method("pie-shells", "Making pie shells", "Making pie shells", PROCESSING, 294_000, null)
			.f2p().trade());
		m.add(new Method("dyes", "Making dyes", "Making dyes", PROCESSING, 281_328, "1,248 dyes an hour")
			.f2p().trade());
		m.add(new Method("cowhide", "Tanning cowhide", "Tanning cowhide", PROCESSING, 234_904, "about 2,000 hides an hour")
			.f2p().trade());

		// ---- Collecting ----
		m.add(new Method("snape-grass", "Collecting snape grass", "Collecting snape grass", COLLECTING, 699_124, null)
			.suggest(skill(MAGIC, 72), quest(Quest.THE_FREMENNIK_TRIALS), quest(Quest.LUNAR_DIPLOMACY)));
		m.add(new Method("anti-dragon-shields-f2p", "Collecting anti-dragon shields (free-to-play)",
			"Collecting anti-dragon shields (free-to-play)", COLLECTING, 279_862, "up to 130 trips an hour")
			.f2p()
			.need(quest(Quest.DRAGON_SLAYER_I)));
		m.add(new Method("cosmic-runes-f2p", "Collecting cosmic runes", "Collecting cosmic runes", COLLECTING, 171_000, null)
			.f2p()
			.suggest(skill(PRAYER, 43)));
		m.add(new Method("wine-of-zamorak-f2p", "Collecting wine of zamorak (free-to-play)",
			"Collecting wine of zamorak (free-to-play)", COLLECTING, 88_428, null)
			.f2p()
			// Telekinetic Grab is cast at 33 Magic; the guide lists 37.
			.need(skill(MAGIC, 33), total(500))
			.suggest(skill(MAGIC, 37)));

		m.sort((a, b) -> Long.compare(b.getWikiPerHour(), a.getWikiPerHour()));
		ALL = Collections.unmodifiableList(m);

		// ---- Timegated runs, done alongside other play ----
		// From each method's page (Money_making_guide/...), read on 4 November 2026: profit per instance after
		// tax, activity time and minimum recurrence time. The hourly figure is the guide's "effective profit",
		// per hour spent on the runs themselves, so these are planned alongside a method and never ranked with them.
		List<Method> r = new ArrayList<>();
		r.add(new Method("bird-houses", "Bird house runs", "Bird house trapping", COLLECTING, 2_239_436,
			"4 redwood bird houses, 99 Hunter")
			.recurring(52_254, 84, 50, null)
			.skill(HUNTER)
			// Redwood bird houses, which the guide's figure is for, are placed at Hunter 89.
			.need(skill(HUNTER, 89), quest(Quest.BONE_VOYAGE))
			.suggest(skill(MAGIC, 49)));
		r.add(new Method("herbs-ranarr", "Herb runs (ranarr weed)", "Farming ranarr weed", COLLECTING, 1_789_600,
			"88 herbs a run, ultracompost, magic secateurs")
			.recurring(178_960, 360, 90, "herbs")
			.skill(FARMING)
			.need(skill(FARMING, 32), quest(Quest.GHOSTS_AHOY), quest(Quest.MY_ARMS_BIG_ADVENTURE),
				quest(Quest.FAIRYTALE_I__GROWING_PAINS))
			.suggest(skill(MAGIC, 61), skill(AGILITY, 47)));
		r.add(new Method("herbs-snapdragon", "Herb runs (snapdragon)", "Farming snapdragon", COLLECTING, 1_751_740,
			"ultracompost, magic secateurs")
			.recurring(175_174, 360, 90, "herbs")
			.skill(FARMING)
			.need(skill(FARMING, 62), quest(Quest.GHOSTS_AHOY), quest(Quest.MY_ARMS_BIG_ADVENTURE),
				quest(Quest.FAIRYTALE_I__GROWING_PAINS))
			.suggest(skill(MAGIC, 61), skill(AGILITY, 47)));
		r.add(new Method("herbs-torstol", "Herb runs (torstol)", "Farming torstol", COLLECTING, 1_467_244,
			"ultracompost, magic secateurs")
			.recurring(146_724, 360, 90, "herbs")
			.skill(FARMING)
			.need(skill(FARMING, 85), quest(Quest.GHOSTS_AHOY), quest(Quest.MY_ARMS_BIG_ADVENTURE),
				quest(Quest.FAIRYTALE_I__GROWING_PAINS))
			.suggest(skill(MAGIC, 61), skill(AGILITY, 47)));
		r.add(new Method("miscellania", "Managing Miscellania", "Managing Miscellania", COLLECTING, 2_637_151,
			"75,000 coffer, 10 workers mining and 5 on herbs")
			.recurring(21_976, 30, 1440, null)
			.need(quest(Quest.THRONE_OF_MISCELLANIA))
			.suggest(quest(Quest.ROYAL_TROUBLE), skill(WOODCUTTING, 45), skill(FARMING, 10), skill(HERBLORE, 35)));
		RECURRING = Collections.unmodifiableList(r);

		for (Method method : ALL)
		{
			if (BY_ID.put(method.getId(), method) != null)
			{
				throw new IllegalStateException("Two methods share the id " + method.getId());
			}
		}
	}

	private Methods()
	{
	}

	/** Every method, best paying by the wiki first. */
	public static List<Method> all()
	{
		return ALL;
	}

	/**
	 * Timegated runs (bird houses, herb runs, Miscellania) that pay per run and come round on a timer.
	 * They are not ranked with the methods above: the goal planner fits them in alongside one.
	 */
	public static List<Method> recurring()
	{
		return RECURRING;
	}

	/** The method with an id, or null. */
	public static Method byId(String id)
	{
		return id == null ? null : BY_ID.get(id);
	}
}
