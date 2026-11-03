# Codex Money Pilot

Which money makers can this account do right now, and which pays **you** the most an hour?

Money Pilot reads your levels, quests and achievement diaries, checks them against what each money
making method needs, and ranks the ones you can do by profit an hour. It starts from the Old School
wiki's money making guide, and as you clock your own runs your figure takes over from the wiki's, so
the list follows how you actually play.

![Best now](docs/best.png)

## What it does

- **Best now**: every method you can do today, best paying first. Each card says where its figure
  comes from (Wiki, Yours, or Your sessions) and how you fit it: *Ready*, *Gear up* (the guide
  suggests more than you have), or *Check* (a need the game does not show, such as having beaten Dad
  in Troll Stronghold). Open a card for its needs, the guide's suggestions with a tick or cross for
  each, what it pays by every source, and a link to the wiki guide.
- **Within reach**: what is nearest to opening up and exactly what it still needs ("Runecraft 77
  (you 74), Sins of the Father"), with the ones that would pay more than your best today first.
  Methods you could start but are far below the guide's suggested levels (a level 3 account and the
  Chambers of Xeric, say) are listed here as a *Stretch* rather than ranked with the rest.
- **Your rates**: the runs you have clocked, method by method, against the wiki's figure.
- **Clock a run**: press it on any method. Money Pilot notes what your bank, inventory and worn gear
  hold, and when you stop it values the difference at today's Grand Exchange prices, so a price
  moving while you play is not counted as money made. Time only runs while you are logged in. A
  small plaque on the game screen shows the time, profit so far and rate.
- Your own figure replaces the wiki's once a method has a quarter of an hour clocked in the last 60
  days.
- Free-to-play accounts only see free-to-play methods, and ironmen do not see methods that buy their
  supplies. Wilderness methods are marked and can be hidden.

![A method opened](docs/best-open.png) ![Within reach](docs/reach.png) ![Your rates](docs/yours.png)

## Works with the Codex suite

If **Codex Session Chronicle** is installed, the sessions it files count too: a session spent mainly
on Vorkath gives Money Pilot your Vorkath rate. It uses the net worth Codex Ledger saw the session
gain when Ledger is running, or Chronicle's loot less supplies otherwise. Money Pilot only reads
Chronicle's journal and never changes it.

## Good to know

- Open your bank once so Money Pilot knows what you start a run with.
- Bank your loot before you stop a run. Items in a looting bag, rune pouch or Grand Exchange offer
  are not counted until they reach your bank, pack or gear.
- Money Pilot reads the game and never acts in it. It shows numbers and wiki links, never what to
  click. Nothing leaves your computer.

## Where the numbers come from

Every method is a page of the Old School wiki's money making guide
(`oldschool.runescape.wiki/w/Money_making_guide/...`), read on 3 November 2026. A level counts as a
**need** only where the game checks it (an altar, a door, a slayer level, a spell) or the guide marks
it required; everything else the guide lists is a **suggestion**. The wiki's profit figures move with
prices, which is why your own clocked rate replaces them.

| Method | Needs (from the wiki) |
| --- | --- |
| Crafting blood runes | Runecraft 77, Sins of the Father |
| Crafting death runes through the Abyss | Runecraft 65, Enter the Abyss, Mourning's End Part II |
| Crafting wrath runes | Runecraft 95, Dragon Slayer II |
| Smelting runite / adamantite bars at Blast Furnace | Smithing 85 / 70, started The Giant Dwarf |
| Pickpocketing vyres / elves | Thieving 82 + Sins of the Father / Thieving 85 + Song of the Elves |
| Rogues' Castle chests | Thieving 84, Prayer 43, Medium Wilderness Diary |
| Casting Tan Leather | Magic 78, Lunar Diplomacy, Hard Fremennik Diary |
| Killing Kree'arra | Ranged 70 (Armadyl's Eyrie, unboostable), Troll Stronghold |
| Killing Commander Zilyana / General Graardor / K'ril | Agility 70 / Strength 70 and Prayer 43 / Hitpoints 70, Troll Stronghold |
| Slayer bosses and monsters | Hydra 95, Cerberus 91, Kraken 87, brutal black dragons 77, gargoyles 75 Slayer |
| Vorkath, rune dragons | Dragon Slayer II |
| Zulrah | Started Regicide |

The full list, with each page's wording, is in `MethodsTest.gatesMatchTheWiki` and `core/Methods.java`.

## Settings

- **Show Wilderness methods** (on)
- **Run plaque** (on): the clocked run on the game screen
- **Chat line when a run ends** (on)
- **Keep field notes** (off): a local record of what the plugin reads and decides, for checking its
  guesses, written to `.runelite/plugin-data/codex-money-pilot`

## Part of Codex

Codex is a suite of RuneLite plugins that share one look and read each other's data, each useful on
its own: Navigator, Guardian, Ledger, Session Chronicle, Gear Roadmap and more.
