/*
Copyright 2009 Hauke Rehfeld


This file is part of QuakeInjector.

QuakeInjector is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

QuakeInjector is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with QuakeInjector.  If not, see <http://www.gnu.org/licenses/>.
*/
package de.haukerehfeld.quakeinjector.model;

import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import de.haukerehfeld.quakeinjector.utils.ChangeListener;

public class RequirementList implements Iterable<Requirement> {

	private HashMap<String, Integer> names = new HashMap<String, Integer>();
	private List<Requirement> requirements;
	
	public RequirementList() {
		this.requirements = Collections.<Requirement>emptyList();
	}
	public RequirementList(List<Requirement> requirements) {
		setRequirements(requirements);
	}

	/**
	 * Replace the list, e.g. with a newly loaded package database. Packages that are installed in the
	 * current list stay installed, with their file list: the new objects don't know about that yet.
	 */
	public void setRequirements(List<Requirement> requirements) {
		for (Requirement r : requirements) {
			Requirement old = get(r.getId());
			if (old != r && old.isInstalled()) {
				if (r instanceof Package pkg && old instanceof Package oldPkg) {
					// the file list first: listeners of the installed flag may read it
					pkg.setFileList(oldPkg.getFileList());
				}
				r.setInstalled(true);
			}
		}

		names.clear();
		for (int i = 0; i < requirements.size(); ++i) {
			names.put(requirements.get(i).getId(), i);
		}
		this.requirements = requirements;

	}

	public void setInstalled(PackageFileList l) {
		Requirement r = get(l.getId());
		if (r instanceof UnavailableRequirement) {
			System.err.println("Can't set installed on UnavailableRequirement " + l.getId());
			return;
		}
		r.setInstalled(true);
		if (r instanceof Package) {
			((Package) r).setFileList(l);
		}
	}

	/** Mark these packages as installed with the given files, then notify the listeners once. */
	public void setInstalled(List<PackageFileList> installed) {
		for (PackageFileList l : installed) {
			setInstalled(l);
		}
		notifyChangeListeners();
	}

	public Requirement get(int i) {
		return requirements.get(i);
	}
	
	public Requirement get(String id) {
		Integer i = names.get(id);
		if (i == null) {
			return new UnavailableRequirement(id);
		}
		return requirements.get(i);
	}		

	public Iterator<Requirement> iterator() {
		return requirements.iterator();
	}

	public int size() {
		return requirements.size();
	}

	public int indexOf(Requirement r) {
		return requirements.indexOf(r);
	}


	/**
	 * easily have change listeners
	 */
	private ChangeListenerList listeners = new ChangeListenerList();

	public void addChangeListener(ChangeListener l) {
		listeners.addChangeListener(l);
	}
	public void removeChangeListener(ChangeListener l) {
		listeners.removeChangeListener(l);
	}
	public void notifyChangeListeners() {
		listeners.notifyChangeListeners(this);
	}

}