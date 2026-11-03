package com.codex.moneypilot.ui;

import com.codex.moneypilot.core.Method;

/** What the panel's buttons ask the plugin to do. */
public interface Actions
{
	void tab(Tab tab);

	/** Show or hide a category of methods. */
	void toggle(Method.Category category);

	/** Start clocking a run of a method. */
	void start(Method method);

	/** Finish the run under way and keep it. */
	void stop();

	/** Throw away the run under way. */
	void discard();

	/** Open the method's guide on the wiki. */
	void guide(Method method);

	/** Forget every clocked run of a method. */
	void forget(Method method);
}
