package com.codex.moneypilot.ui;

/** The panel's views. */
public enum Tab
{
	BEST("Best now"),
	REACH("Within reach"),
	YOURS("Your rates"),
	GOAL("Goal");

	private final String title;

	Tab(String title)
	{
		this.title = title;
	}

	public String getTitle()
	{
		return title;
	}
}
