package com.codex.moneypilot.core;

/**
 * Something the player is saving coins for: Gear Roadmap's savings goal, priced at today's Grand
 * Exchange prices for the pieces not yet owned, or a figure typed into the settings. What is short is
 * the price less the coins the account already holds.
 */
public final class Target
{
	public enum Source
	{
		/** Codex Gear Roadmap's savings goal. */
		GEAR_ROADMAP("Gear Roadmap goal"),
		/** The coins goal typed into Money Pilot's settings. */
		TYPED("Your coins goal");

		private final String title;

		Source(String title)
		{
			this.title = title;
		}

		public String getTitle()
		{
			return title;
		}
	}

	private final String name;
	private final Source source;
	private final long price;
	private final long coins;
	private final boolean coinsKnown;

	public Target(String name, Source source, long price, long coins, boolean coinsKnown)
	{
		this.name = name == null || name.trim().isEmpty() ? source.getTitle() : name.trim();
		this.source = source;
		this.price = Math.max(0, price);
		this.coins = Math.max(0, coins);
		this.coinsKnown = coinsKnown;
	}

	public String getName()
	{
		return name;
	}

	public Source getSource()
	{
		return source;
	}

	/** What it costs: the pieces not owned at today's prices, or the typed figure. */
	public long getPrice()
	{
		return price;
	}

	/** Coins and platinum tokens held in the bank and pack. */
	public long getCoins()
	{
		return coins;
	}

	/** False before the bank has been seen, when the coins counted are only those carried. */
	public boolean isCoinsKnown()
	{
		return coinsKnown;
	}

	/** Coins still to make. */
	public long getShort()
	{
		return Math.max(0, price - coins);
	}

	public boolean isAffordable()
	{
		return price > 0 && coins >= price;
	}

	/** How far the coins already go, 0 to 1. */
	public double getProgress()
	{
		return price <= 0 ? 1 : Math.min(1, coins / (double) price);
	}
}
