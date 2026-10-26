package de.haukerehfeld.quakeinjector.gui;

import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;

/**
 * Keeps a frame maximized across a game session. A fullscreen game (exclusive or borderless) makes Java on Windows
 * drop the maximized state of our window, so after the game ends this maximizes it again if it was maximized when the
 * game started.
 * <p>
 * It doesn't restore while the game runs, because maximizing activates the window and would put it over the game.
 * Windows may un-maximize the window a little after the game's output ended, so after {@link #gameEnded()} it also
 * restores on every later loss of the maximized state, until {@link #stop()}. Call everything on the EDT.
 */
public class KeepMaximized implements WindowStateListener {
	private final Frame frame;
	private final boolean wasMaximized;
	private boolean listening;
	private boolean stopped;

	public KeepMaximized(Frame frame) {
		this.frame = frame;
		this.wasMaximized = frame != null && isMaximized(frame.getExtendedState());
	}

	/** The game exited: maximize the frame again if needed, and keep it maximized until {@link #stop()}. */
	public void gameEnded() {
		if (!wasMaximized) {
			return;
		}
		restore();
		if (!stopped && !listening) {
			frame.addWindowStateListener(this);
			listening = true;
		}
	}

	/** Stop restoring the state, e.g. when the engine output dialog closes. Restoring once at the game's end still happens. */
	public void stop() {
		stopped = true;
		if (listening) {
			frame.removeWindowStateListener(this);
			listening = false;
		}
	}

	@Override
	public void windowStateChanged(WindowEvent e) {
		if (!isMaximized(e.getNewState())) {
			restore();
		}
	}

	private void restore() {
		int state = frame.getExtendedState();
		if (!isMaximized(state)) {
			frame.setExtendedState(state | Frame.MAXIMIZED_BOTH);
		}
	}

	private static boolean isMaximized(int state) {
		return (state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
	}
}
