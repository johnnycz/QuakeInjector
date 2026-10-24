package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.PackageTable;
import de.haukerehfeld.quakeinjector.gui.PackageTableModel;
import de.haukerehfeld.quakeinjector.guimodel.Column;
import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.guimodel.PackageListViewModel;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageList;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Replacing the package list (a reloaded package database) must keep the selected package selected.
 * Runs on the Swing event thread; needs no window, so it works headless.
 */
public class PackageTableSelectionTest {
	private static final int NAME = Column.getColumnNumber(Column.NAME);

	private RequirementList requirements;
	private PackageInteractionViewModel interaction;
	private PackageTable table;

	private static Package pkg(String id) {
		return TestZip.testPackage(id, new ExtractMapping(), "file:///unused.zip");
	}

	private static List<Requirement> list(Requirement... requirements) {
		return new ArrayList<>(List.of(requirements));
	}

	private void onEdt(Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	private void createTable(Package... packages) {
		requirements = new RequirementList(list(packages));
		interaction = new PackageInteractionViewModel(null);
		PackageListViewModel listVm = new PackageListViewModel(interaction);
		listVm.setMapList(new PackageList(requirements));
		table = new PackageTable(new PackageTableModel(listVm, interaction));
	}

	private void select(String id) {
		PackageTableModel model = (PackageTableModel) table.getModel();
		for (int row = 0; row < model.getRowCount(); row++) {
			if (id.equals(model.getValueAt(row, NAME))) {
				int viewRow = table.convertRowIndexToView(row);
				table.setRowSelectionInterval(viewRow, viewRow);
				return;
			}
		}
		fail("no row for " + id);
	}

	/** The id in the selected row, or null. */
	private String selectedRowId() {
		int viewRow = table.getSelectedRow();
		return viewRow < 0 ? null : (String) table.getModel().getValueAt(table.convertRowIndexToModel(viewRow), NAME);
	}

	@Test
	public void selectionSurvivesReplacingTheList() throws Exception {
		Package newB = pkg("b");
		onEdt(() -> {
			createTable(pkg("a"), pkg("b"), pkg("c"));
			select("b");
			assertEquals("b", interaction.getSelectedPackage().getId());

			requirements.setRequirements(list(pkg("a"), newB, pkg("c")));
			requirements.notifyChangeListeners();
		});

		onEdt(() -> {
			assertSame(newB, interaction.getSelectedPackage(), "the new object of the same package");
			assertEquals("b", selectedRowId(), "the table still shows it selected");
		});
	}

	@Test
	public void selectionIsClearedWhenThePackageIsGone() throws Exception {
		onEdt(() -> {
			createTable(pkg("a"), pkg("b"));
			select("b");

			requirements.setRequirements(list(pkg("a")));
			requirements.notifyChangeListeners();
		});

		onEdt(() -> {
			assertNull(interaction.getSelectedPackage());
			assertNull(selectedRowId());
		});
	}
}
