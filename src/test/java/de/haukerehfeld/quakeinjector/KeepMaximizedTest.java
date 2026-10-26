package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.KeepMaximized;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A fullscreen game un-maximizes the main window; it is maximized again once the game ends. The frame is never shown,
 * the test stands in for Windows by changing its state; needs a non-headless environment to create a frame.
 */
public class KeepMaximizedTest {
	private JFrame frame;

	private void onEdt(Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	@BeforeEach
	public void createFrame() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display to create a frame");
		onEdt(() -> frame = new JFrame());
	}

	@AfterEach
	public void disposeFrame() throws Exception {
		if (frame != null) {
			onEdt(frame::dispose);
		}
	}

	/** What Windows does to the frame during or after the game: un-maximize it, and tell the listeners. */
	private void unmaximizeBySystem() {
		int old = frame.getExtendedState();
		frame.setExtendedState(Frame.NORMAL);
		WindowEvent e = new WindowEvent(frame, WindowEvent.WINDOW_STATE_CHANGED, old, Frame.NORMAL);
		for (var l : List.of(frame.getWindowStateListeners())) {
			l.windowStateChanged(e);
		}
	}

	private boolean maximized() {
		return (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
	}

	@Test
	public void maximizesAgainWhenTheGameEnds() throws Exception {
		onEdt(() -> {
			frame.setExtendedState(Frame.MAXIMIZED_BOTH);
			KeepMaximized keep = new KeepMaximized(frame);

			unmaximizeBySystem();
			assertFalse(maximized(), "not restored while the game runs, it would cover the game");

			keep.gameEnded();
			assertTrue(maximized());
		});
	}

	@Test
	public void keepsItMaximizedAfterTheGameUntilStopped() throws Exception {
		onEdt(() -> {
			frame.setExtendedState(Frame.MAXIMIZED_BOTH);
			KeepMaximized keep = new KeepMaximized(frame);
			keep.gameEnded();

			unmaximizeBySystem(); // Windows un-maximizes a little after the output ended
			assertTrue(maximized());

			keep.stop();
			assertEquals(0, frame.getWindowStateListeners().length, "the listener is removed");
			unmaximizeBySystem(); // e.g. the user un-maximizes it later
			assertFalse(maximized());
		});
	}

	@Test
	public void doesNotMaximizeAWindowThatWasNotMaximized() throws Exception {
		onEdt(() -> {
			frame.setExtendedState(Frame.NORMAL);
			KeepMaximized keep = new KeepMaximized(frame);

			keep.gameEnded();

			assertFalse(maximized());
			assertEquals(0, frame.getWindowStateListeners().length);
		});
	}

	@Test
	public void restoresOnceWhenTheDialogClosedBeforeTheGameEnded() throws Exception {
		onEdt(() -> {
			frame.setExtendedState(Frame.MAXIMIZED_BOTH);
			KeepMaximized keep = new KeepMaximized(frame);
			keep.stop(); // the output dialog was closed while the game ran

			unmaximizeBySystem();
			keep.gameEnded();

			assertTrue(maximized());
			assertEquals(0, frame.getWindowStateListeners().length, "no listener left behind");
		});
	}
}
