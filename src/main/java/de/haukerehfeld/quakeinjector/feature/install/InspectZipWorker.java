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
package de.haukerehfeld.quakeinjector.feature.install;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;

import javax.swing.SwingWorker;

/**
 * Inspect the archive and gather all entries
 */
public class InspectZipWorker extends SwingWorker<List<ArchiveEntry>, Void> {
	private final InputStream input;

	public InspectZipWorker(InputStream input) {
		this.input = input;
	}

	@Override
	public List<ArchiveEntry> doInBackground() throws IOException,
            FileNotFoundException, ArchiveException {
		try (ArchiveInputStream<? extends ArchiveEntry> archiveStream = new ArchiveStreamFactory()
				.createArchiveInputStream(input)) {
			List<ArchiveEntry> entries = new ArrayList<>();

			ArchiveEntry entry;
			while ((entry = archiveStream.getNextEntry()) != null) {
				entries.add(entry);

			}

			return entries;
		}
	}
}