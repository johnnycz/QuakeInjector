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

		notifyChangeListeners(-1);
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
