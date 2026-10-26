package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.InstallRandomMap;
import de.haukerehfeld.quakeinjector.gui.PackageTable;
import de.haukerehfeld.quakeinjector.gui.PackageTableModel;
import de.haukerehfeld.quakeinjector.guimodel.Column;
import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.guimodel.PackageListViewModel;
import de.haukerehfeld.quakeinjector.model.CommandHandler;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageList;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The "Install Random Map" button: picks only from the rows the table shows and installs the package unless it is
 * installed or being installed. Runs on the Swing event thread; needs no window, so it works headless.
 */
public class InstallRandomMapTest {
	private static final int NAME = Column.getColumnNumber(Column.NAME);

	/** Records the packages asked to be installed. */
	private static class RecordingCommands implements CommandHandler {
		final List<String> installed = new ArrayList<>();
		boolean installInProgress;

		@Override public void install(Package p) { installed.add(p.getId()); }
		@Override public void uninstall(Package p) { fail("uninstall"); }
		@Override public void play(Package p, String startmap) { fail("play"); }
		@Override public void saveConfig() { }
		@Override public boolean isInstallInProgress(Package p) { return installInProgress; }
		@Override public void openPackageWebpage(Package p) { }
		@Override public URI getScreenshotUri(Package p) { return null; }
	}

	private final RecordingCommands commands = new RecordingCommands();
	private PackageInteractionViewModel interaction;
	private PackageTable table;
	private InstallRandomMap button;

	private static Package pkg(String id) {
		return TestZip.testPackage(id, new ExtractMapping(), "file:///unused.zip");
	}

	private void onEdt(Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	private void createTable(Package... packages) {
		List<Requirement> list = new ArrayList<>(List.of(packages));
		interaction = new PackageInteractionViewModel(commands, null);
		PackageListViewModel listVm = new PackageListViewModel(interaction);
		listVm.setMapList(new PackageList(new RequirementList(list)));
		PackageTableModel model = new PackageTableModel(listVm, interaction);
		table = new PackageTable(model);
		button = new InstallRandomMap(table, interaction, new Random(7));
	}

	private void filter(String text) {
		table.getRowSorter().setRowFilter(((PackageTableModel) table.getModel()).filter(text));
	}

	private void click() {
		button.actionPerformed(null);
	}

	/** The id in the selected row, or null. */
	private String selectedRowId() {
		int viewRow = table.getSelectedRow();
		return viewRow < 0 ? null : (String) table.getModel().getValueAt(table.convertRowIndexToModel(viewRow), NAME);
	}

	@Test
	public void picksOnlyVisibleRowsAndInstallsThem() throws Exception {
		onEdt(() -> {
			createTable(pkg("keep1"), pkg("other1"), pkg("keep2"), pkg("other2"), pkg("other3"), pkg("keep3"));
			filter("keep");

			Set<String> picked = new HashSet<>();
			for (int i = 0; i < 50; i++) {
				click();
				String id = interaction.getSelectedPackage().getId();
				assertTrue(id.startsWith("keep"), "picked a filtered-out package: " + id);
				assertEquals(id, selectedRowId(), "the table shows the picked package selected");
				assertEquals(id, commands.installed.get(commands.installed.size() - 1));
				picked.add(id);
			}
			assertEquals(Set.of("keep1", "keep2", "keep3"), picked, "every visible package can be picked");
		});
	}

	@Test
	public void doesNotInstallAnInstalledPackage() throws Exception {
		onEdt(() -> {
			Package installed = pkg("a");
			installed.setInstalled(true);
			createTable(installed);

			click();

			assertSame(installed, interaction.getSelectedPackage());
			assertEquals(List.of(), commands.installed);
		});
	}

	@Test
	public void doesNotInstallAPackageBeingInstalled() throws Exception {
		onEdt(() -> {
			createTable(pkg("a"));
			commands.installInProgress = true;

			click();

			assertEquals("a", interaction.getSelectedPackage().getId());
			assertEquals(List.of(), commands.installed);
		});
	}

	@Test
	public void doesNothingWhenNoRowIsShown() throws Exception {
		onEdt(() -> {
			createTable(pkg("a"), pkg("b"));
			filter("nomatch");

			click();

			assertNull(interaction.getSelectedPackage());
			assertEquals(List.of(), commands.installed);
		});
	}
}
