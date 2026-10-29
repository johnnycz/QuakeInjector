package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageList;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;

import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;

public class PackageListViewModel implements ChangeListener {

	private final ChangeListenerList listeners = new ChangeListenerList();
	public void addChangeListener(ChangeListener l) {
		listeners.addChangeListener(l);
	}

	public void removeChangeListener(ChangeListener l) {
		listeners.removeChangeListener(l);
	}

	private void notifyChangeListeners(Integer rowIndex) {
		listeners.notifyChangeListeners(rowIndex);
	}

	private final PackageInteractionViewModel packageInteractionViewModel;
	private PackageList data;
	/** The package to select once the first non-empty list arrives; null when there is none (any more) */
	private String packageToRestore;
	private boolean scrollToSelection;

	public PackageListViewModel(PackageInteractionViewModel packageInteractionViewModel) {
		this.packageInteractionViewModel = packageInteractionViewModel;
	}

	public void setMapList(PackageList data) {
		if (this.data != null) {
			//remove from all old
			this.data.removeChangeListener(this);
			for (Package map: this.data) {
				map.removeChangeListener(this);
			}
		}

		data.sort();

		//add to new
		this.data = data;
		data.addChangeListener(this);

		for (Package map: data) {
			map.addChangeListener(this);
		}

		// a reloaded package database brings new Package objects: keep the same package selected
		Package selected = packageInteractionViewModel.getSelectedPackage();
		if (selected != null) {
			packageInteractionViewModel.setSelectedPackage(data.get(selected.getId()));
		}

		// tried on the first list with packages only: later lists (the download after the cache) don't
		if (packageToRestore != null && data.size() > 0) {
			Package restored = data.get(packageToRestore);
			packageToRestore = null;
			if (restored != null && packageInteractionViewModel.getSelectedPackage() == null) {
				packageInteractionViewModel.setSelectedPackage(restored);
				scrollToSelection = true;
			}
		}

		notifyChangeListeners(-1);
	}

	/**
	 * Select this package (e.g. the one selected in the last session) when the first list with
	 * packages arrives, if it is in that list and nothing is selected yet.
	 */
	public void restoreSelection(String packageId) {
		packageToRestore = packageId;
	}

	/** @return true once after {@link #restoreSelection} selected a package: the view should scroll to it */
	public boolean takeScrollRequest() {
		boolean scroll = scrollToSelection;
		scrollToSelection = false;
		return scroll;
	}

	/** @return the id of the selected package, or null */
	public String selectedPackageId() {
		Package selected = packageInteractionViewModel.getSelectedPackage();
		return selected == null ? null : selected.getId();
	}

	/** @return the row of the selected package, or -1 if none is selected */
	public int selectedRowIndex() {
		Package selected = packageInteractionViewModel.getSelectedPackage();
		return selected == null || data == null ? -1 : data.indexOf(selected);
	}

	/** @return the row of the package with this id, or -1 if it isn't in the list */
	public int rowIndexOf(String packageId) {
		if (data == null || packageId == null) {
			return -1;
		}
		Package pkg = data.get(packageId);
		return pkg == null ? -1 : data.indexOf(pkg);
	}

	public void stateChanged(ChangeEvent e) {
		if (e.getSource() instanceof PackageList) {
			setMapList((PackageList) e.getSource());
			return;
		}

		if (e.getSource() instanceof Package) {
			Package r = (Package) e.getSource();
			int i = data.indexOf(r);
			notifyChangeListeners(i);
			return;
		}

		throw new RuntimeException("didn't recognise what changed!");
	}

	public int size() {
		return data.size();
	}

	public void selectPackageByRowIndex(int row) {
		Package pkg = data.get(row);
		packageInteractionViewModel.setSelectedPackage(pkg);
	}

	public Object getColumnData(int col, int row) {
		return Column.getColumn(col).getData(data.get(row));
	}
}
