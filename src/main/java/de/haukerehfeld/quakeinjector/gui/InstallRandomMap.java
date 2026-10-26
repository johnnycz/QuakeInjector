package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;

/**
 * "Install Random Map": selects a random row of those the table shows (so the filter applies) and installs that
 * package unless it is installed or being installed already.
 */
public class InstallRandomMap implements ActionListener {
	private final PackageTable table;
	private final PackageInteractionViewModel packageVm;
	private final Random random;

	public InstallRandomMap(PackageTable table, PackageInteractionViewModel packageVm, Random random) {
		this.table = table;
		this.packageVm = packageVm;
		this.random = random;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		int rows = table.getRowCount();
		if (rows == 0) {
			return;
		}
		int viewRow = random.nextInt(rows);
		// the table's selection listener makes this the selected package of packageVm
		table.setRowSelectionInterval(viewRow, viewRow);
		table.scrollRectToVisible(new Rectangle(table.getCellRect(viewRow, 0, true)));

		if (!packageVm.isInstalled() && !packageVm.isInstalling()) {
			packageVm.install();
		}
	}
}
