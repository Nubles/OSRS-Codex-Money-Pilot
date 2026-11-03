package com.codex.moneypilot.ui;

/** The panel's three views. */
public enum Tab
{
	BEST("Best now"),
	REACH("Within reach"),
	YOURS("Your rates");

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
