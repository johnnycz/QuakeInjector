package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.InstallQueuePanel;
import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel;
import de.haukerehfeld.quakeinjector.model.JobTracker;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The install queue panel must show a row for each job the service adds.
 * Runs on the Swing event thread; needs no window, so it works headless.
 */
public class InstallQueuePanelTest {
	private final InstallQueueViewModel vm = new InstallQueueViewModel();
	private InstallQueuePanel panel;

	private void onEdt(Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	private <T extends Component> List<T> components(Class<T> type) {
		List<T> found = new ArrayList<>();
		for (Component c : panel.getComponents()) {
			if (type.isInstance(c)) {
				found.add(type.cast(c));
			}
		}
		return found;
	}

	@Test
	public void unknownProgressShowsABusyBar() throws Exception {
		JobTracker[] job = new JobTracker[1];

		onEdt(() -> {
			panel = new InstallQueuePanel(vm);
			job[0] = vm.addJob("Downloading package database", () -> { });
			job[0].setProgress(JobTracker.UNKNOWN_PROGRESS);
		});
		onEdt(() -> {
			JProgressBar bar = components(JProgressBar.class).get(0);
			assertTrue(bar.isIndeterminate());
			assertEquals("Downloading package database", bar.getString(), "no percentage");
		});

		onEdt(() -> job[0].setProgress(30));
		onEdt(() -> {
			JProgressBar bar = components(JProgressBar.class).get(0);
			assertFalse(bar.isIndeterminate());
			assertEquals(30, bar.getValue());
		});
	}

	@Test
	public void addedJobShowsAProgressRowThatFollowsTheJob() throws Exception {
		AtomicInteger cancelled = new AtomicInteger();
		JobTracker[] job = new JobTracker[1];

		onEdt(() -> {
			panel = new InstallQueuePanel(vm);
			job[0] = vm.addJob("Installing testmap", cancelled::incrementAndGet);
		});
		onEdt(() -> {
			assertEquals(1, components(JProgressBar.class).size(), "a progress bar for the new job");
			assertEquals(1, components(JButton.class).size(), "a cancel button for the new job");
		});

		onEdt(() -> job[0].setProgress(42));
		onEdt(() -> assertEquals(42, components(JProgressBar.class).get(0).getValue()));

		onEdt(() -> components(JButton.class).get(0).doClick());
		assertEquals(1, cancelled.get(), "the cancel button must reach the job's cancel action");

		onEdt(() -> job[0].finish("Success"));
		onEdt(() -> {
			assertEquals(0, components(JProgressBar.class).size(), "finished jobs show a label instead");
			assertEquals(1, components(JLabel.class).size());
			assertTrue(components(JLabel.class).get(0).getText().contains("Success"),
				components(JLabel.class).get(0).getText());
		});
	}
}
