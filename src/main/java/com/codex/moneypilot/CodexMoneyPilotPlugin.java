package com.codex.moneypilot;

import com.codex.moneypilot.core.Account;
import com.codex.moneypilot.core.Format;
import com.codex.moneypilot.core.Holdings;
import com.codex.moneypilot.core.Logged;
import com.codex.moneypilot.core.Method;
import com.codex.moneypilot.core.Methods;
import com.codex.moneypilot.core.Need;
import com.codex.moneypilot.core.Planner;
import com.codex.moneypilot.core.Rates;
import com.codex.moneypilot.core.Clocked;
import com.codex.moneypilot.core.Run;
import com.codex.moneypilot.notes.FieldNotes;
import com.codex.moneypilot.ui.Actions;
import com.codex.moneypilot.ui.MoneyPanel;
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
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
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

/**
 * Codex Money Pilot: which money makers this account can do, ranked by what they pay an hour. It
 * starts from the Old School wiki's money making guide and moves to your own figures as you clock
 * runs, valuing what your bank, pack and gear gained at today's prices. It also reads the sessions
 * Codex Session Chronicle files. It reads the game and never acts in it; nothing leaves the computer.
 */
@PluginDescriptor(
	name = "Codex Money Pilot",
	description = "Money makers you can do now, ranked by what they pay you an hour: wiki figures first, then your own clocked runs",
	tags = {"codex", "money", "gp", "profit", "money making", "planner", "gp/hr", "wiki"},
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
	private FieldNotes notes;

	private Account account = Account.UNKNOWN;
	private Map<Integer, Long> bank;
	private Map<Integer, Long> inventory = Collections.emptyMap();
	private Map<Integer, Long> worn = Collections.emptyMap();
	private boolean bankDirty;
	private Run run;
	private Holdings runStart;
	private long runProfit;
	private volatile View.Live live;
	private List<Clocked> runs = new ArrayList<>();
	private List<Logged> journal = Collections.emptyList();
	private boolean chronicle;
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
		overlayManager.remove(plaque);
		clientToolbar.removeNavigation(navButton);
		if (notes != null)
		{
			notes.shutdown();
			notes = null;
		}
		panel = null;
		plaque = null;
		account = Account.UNKNOWN;
		bank = null;
		inventory = Collections.emptyMap();
		worn = Collections.emptyMap();
		run = null;
		runStart = null;
		live = null;
		runs = new ArrayList<>();
		journal = Collections.emptyList();
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
			account = Account.UNKNOWN;
			loaded = false;
			refresh();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		saveRun();
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
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id != InventoryID.INV && id != InventoryID.WORN && id != InventoryID.BANK)
		{
			return;
		}
		Map<Integer, Long> items = read(event.getItemContainer());
		if (id == InventoryID.INV)
		{
			inventory = items;
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
		if ("wilderness".equals(event.getKey()))
		{
			clientThread.invokeLater(this::refresh);
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
		for (Method m : Methods.all())
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
		runProfit = holdings().gainedSince(runStart, id -> itemManager.getItemPrice(id));
		Method m = Methods.byId(run.getMethod());
		live = m == null ? null : new View.Live(m, run.getPlayedMs(), runProfit, client.getGameState() == GameState.LOGGED_IN);
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
		Rates rates = Rates.of(runs, journal, now);
		Planner plan = loggedIn ? Planner.plan(Methods.all(), account, rates, new Planner.Filter(shown, config.wilderness())) : null;
		View view = new View(loggedIn, account, plan, rates, new ArrayList<>(runs), journal, shown, live, bank != null, chronicle, now);
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
		public void forget(Method method)
		{
			clientThread.invokeLater(() -> CodexMoneyPilotPlugin.this.forget(method));
		}
	}
}
