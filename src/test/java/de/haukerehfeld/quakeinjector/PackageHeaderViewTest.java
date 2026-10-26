package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.PackageDescriptionView;
import de.haukerehfeld.quakeinjector.gui.PackageHeaderView;
import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.model.CommandHandler;
import de.haukerehfeld.quakeinjector.model.Package;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.net.URI;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The header and description views with nothing selected, as at startup. The commands reject a missing
 * package like the real ones do (they use it right away). Runs on the Swing event thread; needs no window.
 */
public class PackageHeaderViewTest {
	private static CommandHandler commandsNeedingAPackage() {
		return new CommandHandler() {
			public void install(Package p) { Objects.requireNonNull(p); }
			public void uninstall(Package p) { Objects.requireNonNull(p); }
			public void play(Package p, String startmap) { Objects.requireNonNull(p); }
			public void saveConfig() { }
			public boolean isInstallInProgress(Package p) { Objects.requireNonNull(p); return false; }
			public void openPackageWebpage(Package p) { Objects.requireNonNull(p); }
			public URI getScreenshotUri(Package p) { Objects.requireNonNull(p); return null; }
		};
	}

	@Test
	public void nothingSelectedAtStartup() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var vm = new PackageInteractionViewModel(commandsNeedingAPackage(), null);
			new PackageHeaderView(vm, new JPanel());
			new PackageDescriptionView(vm);

			assertNull(vm.getScreenshotUri());
			// clicking the title area with nothing selected does nothing
			vm.openPackageWebpage();
		});
	}
}
