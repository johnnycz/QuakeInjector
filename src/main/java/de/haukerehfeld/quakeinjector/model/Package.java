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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.event.ChangeListener;

/**
 * A requirement that has an installation candidate available.
 */
public class Package extends SortableRequirement implements Requirement {

	/**
	 * easily have change listeners
	 */
	private ChangeListenerList listeners = new ChangeListenerList();

	private String sha256;

	private String filename;

	private List<String> downloadUrls;

	private String author;

	private String title;

	private float normalizedUsersRating;

	private String description;

	/**
	 * Size in kb?
	 */
	private int size;

	private Date date;

	private final ExtractMapping extractMapping;

	private String commandline;

	private List<String> startmaps;
	
	private List<Requirement> requirements;

	private PackageFileList fileList;
	private PackageFileList supposedFileList;

	public Package(String id,
				   String sha256,
				   String filename,
				   List<String> downloadUrls,
				   String author,
				   String title,
				   int size,
				   Date date,
				   boolean isInstalled,
				   float normalizedUsersRating,
				   String description,
				   ExtractMapping extractMapping, // TODO mapping
				   String commandline,
				   List<String> startmaps,
				   List<Requirement> requirements) {
		super(id);
		this.sha256 = sha256;
		this.filename = filename;
		this.downloadUrls = downloadUrls;
		this.author = author;
		this.title = title;
		this.size = size;
		this.date = date;
		super.setInstalled(isInstalled);
		this.normalizedUsersRating = normalizedUsersRating;
		this.description = description;
		this.extractMapping = extractMapping;
		this.commandline = commandline;
		this.startmaps = startmaps;
		this.requirements = requirements;
	}
	
	@Override
	public void addChangeListener(ChangeListener l) {
		listeners.addChangeListener(l);
	}


	@Override
	public void removeChangeListener(ChangeListener l) {
		listeners.removeChangeListener(l);
	}

	public String getSha256() {
		return sha256;
	}

	public String getFilename() {
		return filename;
	}

	public List<String> getDownloadUrls() {
		return downloadUrls;
	}

	public String getAuthor() {
		return author;
	}
	public String getTitle() {
		return title;
	}
	public int getSize() {
		return size;
	}
	public Date getDate() {
		return date;
	}

	public float getNormalizedUsersRating() { return normalizedUsersRating; }


	/**
	 * get description
	 */
	public String getDescription() { return description; }
	

	public ExtractMapping getExtractMapping() {
		return extractMapping;
	}

	public String getCommandline() {
		return commandline;
	}

	public List<String> getStartmaps() {
		return startmaps;
	}

	public void setRequirements(List<Requirement> requirements) {
		this.requirements = requirements;
	}

	public List<Requirement> getRequirements() {
		return this.requirements;
	}


	public List<Package> getAvailableRequirements() {
		List<Package> avails = new ArrayList<Package>();
		for (Requirement r: requirements) {
			if (r instanceof Package) {
				avails.add((Package) r);
			}
		}
		return avails;
	}


	public List<Requirement> getUnavailableRequirements() {
		List<Requirement> unavails = new ArrayList<Requirement>();

		for (Requirement r: requirements) {
			if (!r.isInstalled() && !(r instanceof Package)) {
				unavails.add(r);
			}
		}
		return unavails;
	}

	public List<Requirement> getUnmetRequirements() {
		List<Requirement> unmet = new ArrayList<Requirement>();
		for (Requirement requirement: requirements) {
			if (!requirement.isInstalled()) {
				unmet.add(requirement);
			}
		}
		
		return unmet;
	}

	@Override
	protected void notifyChangeListeners() {
		listeners.notifyChangeListeners(this);
	}

	@Override
	public String toString() {
		return getId() + " (" + isInstalled() + ")";
	}

	public PackageFileList getFileList() { return fileList; }
	
	/**
	 * Files obtained by installing this package.
	 */
	public void setFileList(PackageFileList fileList) { this.fileList = fileList; }

	public PackageFileList getSupposedFileList() { return supposedFileList; }

	/**
	 * Files that the metadata says should be obtained by installing this package.
	 *
	 * <p>This does not list the files in the archive, but files actually installed on the disk after applying installation rules for extraction.</p>
	 */
	public void setSupposedFileList(PackageFileList supposedFileList) { this.supposedFileList = supposedFileList; }


}

