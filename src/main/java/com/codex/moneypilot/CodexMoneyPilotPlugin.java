package com.codex.moneypilot;

import com.codex.moneypilot.core.Account;
import com.codex.moneypilot.core.Coach;
import com.codex.moneypilot.core.GoalPlan;
import com.codex.moneypilot.core.Pinned;
import com.codex.moneypilot.core.Target;
import com.codex.moneypilot.core.Format;
import com.codex.moneypilot.core.Holdings;
import com.codex.moneypilot.core.Logged;
import com.codex.moneypilot.core.Method;
import com.codex.moneypilot.core.Methods;
import com.codex.moneypilot.core.Need;
import com.codex.moneypilot.core.PickpocketLog;
import com.codex.moneypilot.core.PickpocketTracker;
import com.codex.moneypilot.core.Pickpockets;
import com.codex.moneypilot.core.Planner;
import com.codex.moneypilot.core.Rates;
import com.codex.moneypilot.core.Clocked;
import com.codex.moneypilot.core.Run;
import com.codex.moneypilot.notes.FieldNotes;
import com.codex.moneypilot.ui.Actions;
import com.codex.moneypilot.ui.MoneyPanel;
import com.codex.moneypilot.ui.PickpocketPlaque;
import com.codex.moneypilot.ui.RunPlaque;
import com.codex.moneypilot.ui.Sigil;
import com.codex.moneypilot.ui.Tab;
import com.codex.moneypilot.ui.View;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

/**
 * Codex Money Pilot: which money makers this account can do, ranked by what they pay an hour. It
 * starts from the Old School wiki's money making guide and moves to your own figures as you clock
 * runs, valuing what your bank, pack and gear gained at today's prices. It also reads the sessions
 * Codex Session Chronicle files, and keeps a pickpocket log whose rates rank the pickpocketing methods.
 * It reads the game and never acts in it; nothing leaves the computer.
 */
@PluginDescriptor(
	name = "Codex Money Pilot",
	description = "Money makers you can do now, ranked by what they pay you an hour: wiki figures first, then your own clocked runs",
	tags = {"codex", "money", "gp", "profit", "money making", "planner", "gp/hr", "wiki", "pickpocket", "thieving", "coin pouch"},
	internalName = "codex-money-pilot"
)
public class CodexMoneyPilotPlugin extends Plugin
{
	/** Codex Session Chronicle's journal, read and never written. */
	static final String CHRONICLE_GROUP = "codexsessionchronicle";
	static final String CHRONICLE_JOURNAL = "journal";

	private static final String RUNS_KEY = "runs";
	private static final String RUN_KEY = "run";
	private static final String BANK_KEY = "bank";
	private static final String TAB_KEY = "tab";
	private static final String HIDDEN_KEY = "hidden";
	private static final String PICKS_KEY = "pickpockets";
	/** The plaque shows while the last pick was this recent. */
	private static final int PLAQUE_TICKS = 100;
	/** Runs kept, newest first. */
	static final int MAX_RUNS = 300;

	/** Ticks between reading the account again (levels, quests, diaries). */
	private static final int ACCOUNT_TICKS = 50;
	/** Ticks between working out what the run under way has made. */
	private static final int LIVE_TICKS = 5;
	/** Ticks between saving the run under way, so a crash loses little. */
	private static final int SAVE_TICKS = 100;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	@Inject
	private CodexMoneyPilotConfig config;

	private MoneyPanel panel;
	private NavigationButton navButton;
	private RunPlaque plaque;
	private PickpocketPlaque pickPlaque;
	private FieldNotes notes;

	private Account account = Account.UNKNOWN;
	private Map<Integer, Long> bank;
	private Map<Integer, Long> inventory = Collections.emptyMap();
	private Map<Integer, Long> worn = Collections.emptyMap();
	private boolean bankDirty;
	private Run run;
	private Holdings runStart;
	private long runProfit;
	/** Time played on the run when its profit last changed, and the coach's last verdict, so it does not flicker. */
	private long runChangedAt;
	private Coach.Verdict coached;
	private volatile View.Live live;
	private List<Clocked> runs = new ArrayList<>();
	private List<Logged> journal = Collections.emptyList();
	private boolean chronicle;
	/** Codex Gear Roadmap's savings goal, read and never written; null when there is none. */
	private Pinned.Gear gearGoal;
	/** Codex Navigator's unfinished pinned goals, read and never written. */
	private List<Pinned.Goal> pinned = Collections.emptyList();
	private boolean navigator;
	private PickpocketLog pickLog = new PickpocketLog();
	private PickpocketTracker tracker = new PickpocketTracker(pickLog);
	private Map<Integer, Long> seedBox = Collections.emptyMap();
	private final Map<Integer, Boolean> edible = new HashMap<>();
	private volatile View.Picking picking;
	private boolean picksDirty;
	/** The profile the pickpocket log was read from, so it is saved back there even after the profile changes. */
	private String picksProfile;
	private boolean pickRefresh;
	/** True once the pouch warning has been said, until the pouches are opened. */
	private boolean pouchWarned;
	private final Set<Method.Category> shown = EnumSet.allOf(Method.Category.class);

	private int accountTicks = -1;
	private int tickCount;
	private boolean loaded;

	@Provides
	CodexMoneyPilotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexMoneyPilotConfig.class);
	}

	@Override
	protected void startUp()
	{
		notes = new FieldNotes(this::getPluginDirectory, gson);
		if (config.fieldNotes())
		{
			notes.open();
		}
		panel = new MoneyPanel(new PanelActions());
		panel.show(rememberedTab());
		readShown();
		navButton = NavigationButton.builder()
			.tooltip("Codex Money Pilot")
			.icon(Sigil.icon(16))
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		plaque = new RunPlaque(() -> config.plaque() ? live : null);
		overlayManager.add(plaque);
		pickPlaque = new PickpocketPlaque(() -> config.pickpocketPlaque() && config.pickpocketLog() ? picking : null);
		overlayManager.add(pickPlaque);
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				load();
				accountTicks = 0;
			}
			refresh();
		});
	}

	@Override
	protected void shutDown()
	{
		saveRun();
		saveBank();
		savePicks();
		overlayManager.remove(plaque);
		overlayManager.remove(pickPlaque);
		clientToolbar.removeNavigation(navButton);
		if (notes != null)
		{
			notes.shutdown();
			notes = null;
		}
		panel = null;
		plaque = null;
		pickPlaque = null;
		picking = null;
		pickLog = new PickpocketLog();
		tracker = new PickpocketTracker(pickLog);
		picksProfile = null;
		seedBox = Collections.emptyMap();
		edible.clear();
		account = Account.UNKNOWN;
		bank = null;
		inventory = Collections.emptyMap();
		worn = Collections.emptyMap();
		run = null;
		runStart = null;
		live = null;
		runs = new ArrayList<>();
		journal = Collections.emptyList();
		gearGoal = null;
		pinned = Collections.emptyList();
		navigator = false;
		loaded = false;
		accountTicks = -1;
	}

	// ---- Events ----

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			if (!loaded)
			{
				load();
			}
			// The quest log and varbits settle a couple of ticks after login.
			accountTicks = 2;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			saveRun();
			saveBank();
			savePicks();
			tracker.reset();
			picking = null;
			account = Account.UNKNOWN;
			loaded = false;
			refresh();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		saveRun();
		savePicks();
		load();
		accountTicks = 2;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		tickCount++;
		boolean changed = false;
		if (accountTicks >= 0 && --accountTicks < 0)
		{
			readAccount();
			accountTicks = ACCOUNT_TICKS;
			changed = true;
		}
		if (run != null)
		{
			run.tick();
			if (tickCount % LIVE_TICKS == 0)
			{
				measure();
				changed = true;
			}
			if (tickCount % SAVE_TICKS == 0)
			{
				saveRun();
			}
		}
		if (bankDirty && tickCount % 10 == 0)
		{
			saveBank();
		}
		if (config.pickpocketLog() && client.getGameState() == GameState.LOGGED_IN)
		{
			changed |= pickTick();
		}
		if (changed)
		{
			refresh();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		// A level gained can open a method; read the account again soon, not every tick.
		if (account.isKnown() && event.getLevel() != account.level(event.getSkill()) && (accountTicks < 0 || accountTicks > 3))
		{
			accountTicks = 3;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.pickpocketLog() || (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE))
		{
			return;
		}
		tracker.chat(Text.removeTags(event.getMessage()), System.currentTimeMillis());
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Only read, to know which NPC a pick is of; the click itself is left alone.
		if (config.pickpocketLog() && "Pickpocket".equals(event.getMenuOption()))
		{
			NPC npc = event.getMenuEntry().getNpc();
			tracker.aim(npc == null ? null : Text.removeTags(npc.getName()));
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id != InventoryID.INV && id != InventoryID.WORN && id != InventoryID.BANK && id != InventoryID.SEED_BOX)
		{
			return;
		}
		Map<Integer, Long> items = read(event.getItemContainer());
		if (id == InventoryID.INV || id == InventoryID.SEED_BOX)
		{
			if (id == InventoryID.INV)
			{
				inventory = items;
			}
			else
			{
				seedBox = items;
			}
			if (config.pickpocketLog())
			{
				Map<Integer, Long> pack = new HashMap<>(inventory);
				seedBox.forEach((k, v) -> pack.merge(k, v, Long::sum));
				tracker.pack(pack, banking());
			}
		}
		else if (id == InventoryID.WORN)
		{
			worn = items;
		}
		else
		{
			boolean first = bank == null;
			bank = items;
			bankDirty = true;
			if (first)
			{
				refresh();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CHRONICLE_GROUP.equals(event.getGroup()) && CHRONICLE_JOURNAL.equals(event.getKey()))
		{
			clientThread.invokeLater(() ->
			{
				readJournal();
				refresh();
			});
			return;
		}
		if ((Pinned.GEAR_GROUP.equals(event.getGroup()) && Pinned.GEAR_GOAL.equals(event.getKey()))
			|| (Pinned.NAVIGATOR_GROUP.equals(event.getGroup()) && Pinned.NAVIGATOR_BOARD.equals(event.getKey())))
		{
			// Another plugin saved its goal for some profile; only this account's matters.
			String profile = configManager.getRSProfileKey();
			if (profile != null && profile.equals(event.getProfile()))
			{
				clientThread.invokeLater(() ->
				{
					readGoals();
					refresh();
				});
			}
			return;
		}
		if (!CodexMoneyPilotConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("fieldNotes".equals(event.getKey()) && notes != null)
		{
			if (config.fieldNotes())
			{
				notes.open();
			}
			else
			{
				notes.close();
			}
			return;
		}
		if ("pickpocketLog".equals(event.getKey()) && !config.pickpocketLog())
		{
			clientThread.invokeLater(() ->
			{
				savePicks();
				tracker.reset();
				picking = null;
				refresh();
			});
			return;
		}
		if ("wilderness".equals(event.getKey()) || "pickpocketLog".equals(event.getKey()) || "pouchWarning".equals(event.getKey()) || "goalCoins".equals(event.getKey()) || "goalName".equals(event.getKey())
			|| "hoursPerDay".equals(event.getKey()) || "readGearRoadmap".equals(event.getKey()) || "runCoach".equals(event.getKey()))
		{
			clientThread.invokeLater(() ->
			{
				measure();
				refresh();
			});
		}
	}

	// ---- Reading the game ----

	private Map<Integer, Long> read(ItemContainer container)
	{
		Map<Integer, Long> items = new HashMap<>();
		if (container == null)
		{
			return items;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			// Noted and worn variants are the same item; a placeholder holds nothing and is left out above.
			items.merge(itemManager.canonicalize(item.getId()), (long) item.getQuantity(), Long::sum);
		}
		return items;
	}

	private void readAccount()
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return;
		}
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill s : Skill.values())
		{
			levels.put(s, client.getRealSkillLevel(s));
		}
		Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		Map<Integer, Integer> varbits = new HashMap<>();
		List<Method> every = new ArrayList<>(Methods.all());
		every.addAll(Methods.recurring());
		for (Method m : every)
		{
			readNeeds(m.getNeeds(), quests, varbits);
			readNeeds(m.getSuggests(), quests, varbits);
		}
		boolean members = client.getWorldType().contains(WorldType.MEMBERS) || client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT) > 0;
		boolean iron = client.getVarbitValue(VarbitID.IRONMAN) > 0;
		account = new Account(levels, quests, varbits, client.getLocalPlayer().getCombatLevel(), client.getTotalLevel(),
			members, iron, true);
	}

	private void readNeeds(List<Need> needs, Map<Quest, QuestState> quests, Map<Integer, Integer> varbits)
	{
		for (Need n : needs)
		{
			if (n.getQuest() != null && !quests.containsKey(n.getQuest()))
			{
				quests.put(n.getQuest(), n.getQuest().getState(client));
			}
			if (n.getKind() == Need.Kind.DIARY && !varbits.containsKey(n.getVarbit()))
			{
				varbits.put(n.getVarbit(), client.getVarbitValue(n.getVarbit()));
			}
		}
	}

	private void readJournal()
	{
		String json = configManager.getRSProfileConfiguration(CHRONICLE_GROUP, CHRONICLE_JOURNAL);
		chronicle = json != null;
		journal = Logged.read(gson, json, System.currentTimeMillis());
	}

	/** Gear Roadmap's savings goal and Navigator's pinned goals for this account; either may be absent. */
	private void readGoals()
	{
		// Other plugins' formats may change under us; whatever cannot be read is simply not planned for.
		gearGoal = Pinned.gear(gson, configManager.getRSProfileConfiguration(Pinned.GEAR_GROUP, Pinned.GEAR_GOAL));
		String board = configManager.getRSProfileConfiguration(Pinned.NAVIGATOR_GROUP, Pinned.NAVIGATOR_BOARD);
		navigator = board != null;
		pinned = Pinned.navigator(gson, board);
	}

	// ---- Pickpocketing ----

	/** A tick of the pickpocket log; true when the panel should be rebuilt. */
	private boolean pickTick()
	{
		long now = System.currentTimeMillis();
		tracker.tick(this::isEdible, now);
		boolean filed = tracker.takeChanged();
		if (filed)
		{
			picksDirty = true;
		}
		Pickpockets.Target target = tracker.getSitting();
		long since = tracker.sinceLastPick();
		if (target == null || since < 0 || since > PLAQUE_TICKS)
		{
			picking = null;
		}
		else
		{
			PickpocketLog.Counts live = tracker.getLive();
			int cap = target.hasPouch() ? Pickpockets.pouchCap(diary(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE),
				diary(VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE), diary(VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE)) : 0;
			int warnAt = Math.max(0, config.pouchWarning());
			long pouches = tracker.pouches();
			picking = new View.Picking(target, live.getAttempts(), live.getSuccesses(), live.getStuns(), live.getPlayedMs(),
				live.profit(this::price), pouches, cap, warnAt);
			if (picking.nearCap())
			{
				if (!pouchWarned)
				{
					pouchWarned = true;
					say(pouches >= cap ? "your coin pouches are full (" + pouches + " of " + cap + "): open them to pick on."
						: pouches + " of " + cap + " coin pouches: open them soon.");
				}
			}
			else
			{
				pouchWarned = false;
			}
		}
		if (picksDirty && tickCount % SAVE_TICKS == 0)
		{
			savePicks();
		}
		// The ranking follows the log, but not every tick.
		pickRefresh |= filed;
		if (pickRefresh && tickCount % LIVE_TICKS == 0)
		{
			pickRefresh = false;
			return true;
		}
		return false;
	}

	private boolean diary(int varbit)
	{
		return client.getVarbitValue(varbit) == 1;
	}

	private boolean isEdible(int id)
	{
		return edible.computeIfAbsent(id, k ->
		{
			ItemComposition c = itemManager.getItemComposition(k);
			if (c == null || c.getInventoryActions() == null)
			{
				return false;
			}
			for (String action : c.getInventoryActions())
			{
				if ("Eat".equals(action) || "Drink".equals(action))
				{
					return true;
				}
			}
			return false;
		});
	}

	/** True while the bank or a deposit box is open, when what leaves the pack is banked, not eaten. */
	private boolean banking()
	{
		return visible(InterfaceID.Bankmain.UNIVERSE) || visible(InterfaceID.BankDepositbox.UNIVERSE);
	}

	private boolean visible(int component)
	{
		net.runelite.api.widgets.Widget w = client.getWidget(component);
		return w != null && !w.isHidden();
	}

	private long price(int id)
	{
		return itemManager.getItemPrice(id);
	}

	private void forgetPicks(Pickpockets.Target target)
	{
		pickLog.forget(target);
		picksDirty = true;
		savePicks();
		refresh();
	}

	/** The pickpocket log as the panel shows it: each target valued at today's prices, best paying first. */
	private List<View.Picked> picked(long now)
	{
		List<View.Picked> out = new ArrayList<>();
		for (PickpocketLog.Tally t : pickLog.tallies(now).values())
		{
			out.add(new View.Picked(t.getTarget(), t.getAttempts(), t.getSuccesses(), t.getStuns(), t.getOpened(), t.getPlayedMs(),
				t.lootValue(this::price), t.foodValue(this::price), t.foodPerHour(), t.picksPerHour(),
				t.getPlayedMs() >= Rates.ENOUGH_MS));
		}
		out.sort((a, b) -> Long.compare(b.perHour(), a.perHour()));
		return out;
	}

	// ---- Runs ----

	private Holdings holdings()
	{
		return Holdings.of(bank, inventory, worn);
	}

	private void measure()
	{
		if (run == null || runStart == null)
		{
			return;
		}
		long profit = holdings().gainedSince(runStart, id -> itemManager.getItemPrice(id));
		if (profit != runProfit)
		{
			runChangedAt = run.getPlayedMs();
		}
		runProfit = profit;
		Method m = Methods.byId(run.getMethod());
		Coach coach = m == null || !config.runCoach() ? null
			: Coach.of(m, run.getPlayedMs(), runProfit, runChangedAt, runs,
				Rates.of(runs, Collections.emptyList(), System.currentTimeMillis()).own(m), coached);
		coached = coach == null ? null : coach.getVerdict();
		live = m == null ? null : new View.Live(m, run.getPlayedMs(), runProfit, client.getGameState() == GameState.LOGGED_IN, coach);
	}

	private void start(Method m)
	{
		if (run != null || bank == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		run = new Run(m.getId(), System.currentTimeMillis(), holdings());
		runStart = run.getStart();
		runProfit = 0;
		runChangedAt = 0;
		coached = null;
		measure();
		saveRun();
		if (notes != null && notes.isOpen())
		{
			JsonObject n = new JsonObject();
			n.addProperty("method", m.getId());
			n.addProperty("items", runStart.getItems().size());
			notes.note("run-start", n);
		}
		refresh();
	}

	private void stop(boolean keep)
	{
		if (run == null)
		{
			return;
		}
		measure();
		Clocked record = run.finish(runProfit);
		Method m = Methods.byId(run.getMethod());
		if (notes != null && notes.isOpen())
		{
			JsonObject n = new JsonObject();
			n.addProperty("method", run.getMethod());
			n.addProperty("ms", record.getPlayedMs());
			n.addProperty("gp", record.getProfit());
			n.addProperty("kept", keep && record.isValid());
			notes.note("run-stop", n);
		}
		run = null;
		runStart = null;
		live = null;
		configManager.unsetRSProfileConfiguration(CodexMoneyPilotConfig.GROUP, RUN_KEY);
		if (keep)
		{
			if (record.isValid())
			{
				runs.add(0, record);
				while (runs.size() > MAX_RUNS)
				{
					runs.remove(runs.size() - 1);
				}
				saveRuns();
				if (config.chatLine() && m != null)
				{
					long perHour = Math.round(record.getProfit() * 3_600_000.0 / record.getPlayedMs());
					say(m.getName() + ": " + Format.signed(record.getProfit()) + " in " + Format.duration(record.getPlayedMs())
						+ ", " + Format.signed(perHour) + " an hour.");
				}
			}
			else
			{
				say("Runs shorter than " + Format.duration(Clocked.MIN_MS) + " are not kept.");
			}
		}
		refresh();
	}

	private void forget(Method m)
	{
		runs.removeIf(r -> m.getId().equals(r.getMethod()));
		saveRuns();
		refresh();
	}

	private void say(String text)
	{
		String message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("Money Pilot: ")
			.append(ChatColorType.NORMAL)
			.append(text)
			.build();
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(message).build());
	}

	// ---- Saving ----

	private void load()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		loaded = true;
		runs = new ArrayList<>();
		String saved = configManager.getRSProfileConfiguration(CodexMoneyPilotConfig.GROUP, RUNS_KEY);
		if (saved != null)
		{
			try
			{
				List<Clocked> read = gson.fromJson(saved, new TypeToken<List<Clocked>>()
				{
				}.getType());
				if (read != null)
				{
					read.removeIf(r -> r == null || !r.isValid());
					runs.addAll(read);
				}
			}
			catch (RuntimeException e)
			{
				// A damaged list is started again rather than breaking the panel.
				runs = new ArrayList<>();
			}
		}
		String bankText = configManager.getRSProfileConfiguration(CodexMoneyPilotConfig.GROUP, BANK_KEY);
		bank = bankText == null ? null : new HashMap<>(Holdings.decode(bankText).getItems());
		run = null;
		runStart = null;
		live = null;
		String runText = configManager.getRSProfileConfiguration(CodexMoneyPilotConfig.GROUP, RUN_KEY);
		if (runText != null)
		{
			try
			{
				Run r = gson.fromJson(runText, Run.class);
				if (r != null && r.isValid())
				{
					run = r;
					runStart = r.getStart();
				}
			}
			catch (RuntimeException e)
			{
				// Lost, not fatal.
			}
		}
		readJournal();
		readGoals();
		picksProfile = configManager.getRSProfileKey();
		pickLog = PickpocketLog.decode(gson, configManager.getRSProfileConfiguration(CodexMoneyPilotConfig.GROUP, PICKS_KEY));
		pickLog.prune(System.currentTimeMillis());
		tracker = new PickpocketTracker(pickLog);
		picking = null;
		picksDirty = false;
	}

	private void saveRuns()
	{
		if (configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(CodexMoneyPilotConfig.GROUP, RUNS_KEY, gson.toJson(runs));
		}
	}

	private void saveRun()
	{
		if (run != null && configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(CodexMoneyPilotConfig.GROUP, RUN_KEY, gson.toJson(run));
		}
	}

	private void savePicks()
	{
		if (picksDirty && picksProfile != null)
		{
			pickLog.prune(System.currentTimeMillis());
			if (pickLog.isEmpty())
			{
				configManager.unsetConfiguration(CodexMoneyPilotConfig.GROUP, picksProfile, PICKS_KEY);
			}
			else
			{
				configManager.setConfiguration(CodexMoneyPilotConfig.GROUP, picksProfile, PICKS_KEY, pickLog.encode(gson));
			}
			picksDirty = false;
		}
	}

	private void saveBank()
	{
		if (bankDirty && bank != null && configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(CodexMoneyPilotConfig.GROUP, BANK_KEY, Holdings.of(bank).encode());
			bankDirty = false;
		}
	}

	private Tab rememberedTab()
	{
		String saved = configManager.getConfiguration(CodexMoneyPilotConfig.GROUP, TAB_KEY);
		for (Tab t : Tab.values())
		{
			if (t.name().equals(saved))
			{
				return t;
			}
		}
		return Tab.BEST;
	}

	private void readShown()
	{
		shown.clear();
		shown.addAll(EnumSet.allOf(Method.Category.class));
		String hidden = configManager.getConfiguration(CodexMoneyPilotConfig.GROUP, HIDDEN_KEY);
		if (hidden != null)
		{
			for (String part : hidden.split(","))
			{
				for (Method.Category c : Method.Category.values())
				{
					if (c.name().equals(part.trim()))
					{
						shown.remove(c);
					}
				}
			}
		}
	}

	private void saveShown()
	{
		Set<String> hidden = new LinkedHashSet<>();
		for (Method.Category c : Method.Category.values())
		{
			if (!shown.contains(c))
			{
				hidden.add(c.name());
			}
		}
		configManager.setConfiguration(CodexMoneyPilotConfig.GROUP, HIDDEN_KEY, String.join(",", hidden));
	}

	// ---- The panel ----

	/** Builds what the panel shows; on the client thread. */
	private void refresh()
	{
		MoneyPanel target = panel;
		if (target == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN && account.isKnown();
		Rates rates = Rates.of(runs, journal, config.pickpocketLog() ? pickLog.rates(now, this::price) : null, now);
		Planner plan = loggedIn ? Planner.plan(Methods.all(), account, rates, new Planner.Filter(shown, config.wilderness())) : null;
		View.Goals goals = plan == null ? View.Goals.NONE : goals(plan, rates);
		View view = new View(loggedIn, account, plan, rates, new ArrayList<>(runs), journal, shown, live, bank != null, chronicle, now,
			goals, config.pickpocketLog() ? picked(now) : null, config.pickpocketLog());
		if (plan != null && notes != null && notes.isOpen())
		{
			JsonObject n = new JsonObject();
			JsonArray top = new JsonArray();
			plan.getOpen().stream().limit(5).forEach(p -> top.add(p.getMethod().getId() + ":" + p.getRate().getSource()
				+ ":" + p.getRate().getPerHour()));
			n.add("open", top);
			n.addProperty("near", plan.getNear().size());
			n.addProperty("hidden", plan.getHidden());
			n.addProperty("members", account.isMembers());
			n.addProperty("iron", account.isIronman());
			n.addProperty("runs", runs.size());
			n.addProperty("sessions", journal.size());
			notes.noteChanged("plan", n);
		}
		SwingUtilities.invokeLater(() -> target.update(view));
	}

	/** The goals to plan for, each with its plan; on the client thread, where prices are read. */
	private View.Goals goals(Planner plan, Rates rates)
	{
		List<Target> targets = new ArrayList<>();
		long coins = coins(bank) + coins(inventory);
		boolean coinsKnown = bank != null;
		// Ironmen cannot buy the pieces, so a price on them would mean nothing.
		if (gearGoal != null && config.readGearRoadmap() && !account.isIronman())
		{
			Map<Integer, Long> held = new HashMap<>(holdings().getItems());
			long price = 0;
			boolean priced = true;
			for (int id : gearGoal.getIds())
			{
				int canon = itemManager.canonicalize(id);
				if (held.containsKey(canon) || held.containsKey(id))
				{
					continue;
				}
				long each = itemManager.getItemPrice(canon);
				if (each <= 0)
				{
					// A piece with no price would make the goal look cheaper than it is.
					priced = false;
					break;
				}
				price += each;
			}
			if (priced)
			{
				targets.add(new Target(gearGoal.getName(), Target.Source.GEAR_ROADMAP, price, coins, coinsKnown));
			}
		}
		if (config.goalCoins() > 0)
		{
			targets.add(new Target(config.goalName(), Target.Source.TYPED, config.goalCoins() * 1_000L, coins, coinsKnown));
		}
		List<Method> timegated = GoalPlan.runsFor(account, Methods.recurring());
		int hours = Math.max(1, Math.min(16, config.hoursPerDay()));
		List<View.Goal> out = new ArrayList<>();
		for (Target t : targets)
		{
			out.add(new View.Goal(t, GoalPlan.of(t.getShort(), plan.getOpen(), timegated, plan.getNear(), pinned, hours)));
		}
		return new View.Goals(out, navigator, pinned.size(), hours);
	}

	private static long coins(Map<Integer, Long> items)
	{
		if (items == null)
		{
			return 0;
		}
		return items.getOrDefault(ItemID.COINS, 0L) + 1_000L * items.getOrDefault(ItemID.PLATINUM, 0L);
	}

	private final class PanelActions implements Actions
	{
		@Override
		public void tab(Tab tab)
		{
			configManager.setConfiguration(CodexMoneyPilotConfig.GROUP, TAB_KEY, tab.name());
			if (panel != null)
			{
				panel.show(tab);
			}
		}

		@Override
		public void toggle(Method.Category category)
		{
			clientThread.invokeLater(() ->
			{
				if (!shown.remove(category))
				{
					shown.add(category);
				}
				saveShown();
				refresh();
			});
		}

		@Override
		public void start(Method method)
		{
			clientThread.invokeLater(() -> CodexMoneyPilotPlugin.this.start(method));
		}

		@Override
		public void stop()
		{
			clientThread.invokeLater(() -> CodexMoneyPilotPlugin.this.stop(true));
		}

		@Override
		public void discard()
		{
			clientThread.invokeLater(() -> CodexMoneyPilotPlugin.this.stop(false));
		}

		@Override
		public void guide(Method method)
		{
			LinkBrowser.browse(method.getUrl());
		}

		@Override
		public void forgetPicks(Pickpockets.Target target)
		{
			clientThread.invokeLater(() -> CodexMoneyPilotPlugin.this.forgetPicks(target));
		}

		@Override
		public void forget(Method method)
		{
			clientThread.invokeLater(() -> CodexMoneyPilotPlugin.this.forget(method));
		}
	}
}
