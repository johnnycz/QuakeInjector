package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.PackageInteractionPanelView;
import de.haukerehfeld.quakeinjector.gui.TrashIcon;
import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.model.CommandHandler;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which buttons the interaction panel shows. Runs on the Swing event thread; needs no window.
 */
public class PackageInteractionPanelViewTest {
	private PackageInteractionViewModel vm;
	private PackageInteractionPanelView panel;

	private static Package pkg(String id, boolean installed) {
		return new Package(id, "sha", id + ".zip", List.of("file:///unused.zip"), "author", "title",
			1000, null, installed, 0f, "description", new ExtractMapping(), "",
			List.of("start", id + "_e1m1"), List.of());
	}

	private static CommandHandler noCommands() {
		return new CommandHandler() {
			public void install(Package p) { }
			public void uninstall(Package p) { }
			public void play(Package p, String startmap) { }
			public void saveConfig() { }
			public boolean isInstallInProgress(Package p) { return false; }
			public void openPackageWebpage(Package p) { }
			public void openScreenshotInBrowser(Package p) { }
			public URI getScreenshotUri(Package p) { return null; }
		};
	}

	private void onEdt(Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	private void createPanel(Package selected) {
		vm = new PackageInteractionViewModel(noCommands());
		vm.setSelectedPackage(selected);
		panel = new PackageInteractionPanelView(vm);
	}

	private static void collect(Container c, List<Component> into) {
		for (Component child : c.getComponents()) {
			into.add(child);
			if (child instanceof Container cc) {
				collect(cc, into);
			}
		}
	}

	/** Visible: the component and all its parents up to the panel (the layout hides the other card). */
	private boolean visible(Component c) {
		for (Component p = c; p != panel; p = p.getParent()) {
			if (!p.isVisible()) {
				return false;
			}
		}
		return true;
	}

	/** The visible buttons, by text ("" for the icon-only one). */
	private List<String> visibleButtons() {
		List<Component> all = new ArrayList<>();
		collect(panel, all);
		List<String> texts = new ArrayList<>();
		for (Component c : all) {
			if (c instanceof AbstractButton b && !(c.getParent() instanceof JComboBox) && visible(b)) {
				texts.add(b.getText() == null ? "" : b.getText());
			}
		}
		return texts;
	}

	private AbstractButton iconButton() {
		List<Component> all = new ArrayList<>();
		collect(panel, all);
		for (Component c : all) {
			if (c instanceof AbstractButton b && b.getIcon() instanceof TrashIcon) {
				return b;
			}
		}
		throw new AssertionError("no uninstall button");
	}

	private boolean startmapsVisible() {
		List<Component> all = new ArrayList<>();
		collect(panel, all);
		return all.stream().anyMatch(c -> c instanceof JComboBox && visible(c));
	}

	@Test
	public void notInstalledShowsOnlyTheInstallButton() throws Exception {
		onEdt(() -> {
			createPanel(pkg("jam9", false));

			assertEquals(List.of("Install"), visibleButtons(), "no package name on the button");
			assertFalse(startmapsVisible());
		});
	}

	@Test
	public void installedShowsPlayStartmapsAndTheUninstallIcon() throws Exception {
		onEdt(() -> {
			createPanel(pkg("jam9", true));

			assertEquals(List.of("Play", ""), visibleButtons());
			assertTrue(startmapsVisible());
			AbstractButton uninstall = iconButton();
			assertEquals("Uninstall", uninstall.getToolTipText());
		});
	}

	@Test
	public void switchesWhenThePackageGetsInstalled() throws Exception {
		Package pkg = pkg("jam9", false);
		onEdt(() -> {
			createPanel(pkg);
			pkg.setInstalled(true);

			assertEquals(List.of("Play", ""), visibleButtons());
		});
	}

	@Test
	public void installIsDisabledWithoutASelection() throws Exception {
		onEdt(() -> {
			createPanel(null);

			List<Component> all = new ArrayList<>();
			collect(panel, all);
			AbstractButton install = all.stream()
				.filter(c -> c instanceof AbstractButton b && "Install".equals(b.getText()))
				.map(c -> (AbstractButton) c).findFirst().orElseThrow();
			assertTrue(visible(install));
			assertFalse(install.isEnabled());
		});
	}
}
