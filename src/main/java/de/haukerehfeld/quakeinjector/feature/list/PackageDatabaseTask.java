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
package de.haukerehfeld.quakeinjector.feature.list;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.utils.AsyncTask;
import de.haukerehfeld.quakeinjector.utils.AsyncProgressListener;
import de.haukerehfeld.quakeinjector.utils.Download;
import de.haukerehfeld.quakeinjector.utils.PercentageProgressListener;
import de.haukerehfeld.quakeinjector.utils.ProgressListener;
import de.haukerehfeld.quakeinjector.utils.SumProgressListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Fetch and parse the package database from the remote repository.
 */
public class PackageDatabaseTask implements AsyncTask<List<Requirement>> {
	private final Configuration configuration;
	private final AsyncProgressListener progressListener;

	public PackageDatabaseTask(Configuration configuration) {
		this(configuration, null);
	}

	public PackageDatabaseTask(Configuration configuration, AsyncProgressListener progressListener) {
		this.configuration = configuration;
		this.progressListener = progressListener;
	}

	@Override
	public List<Requirement> execute() throws IOException, org.xml.sax.SAXException {
		InputStream dl = getDownloadStream(configuration.RepositoryDatabasePath.get());

		final PackageDatabaseParser parser = new PackageDatabaseSolrJsonParser(configuration);
		List<Requirement> all = parser.parse(dl);

		return all;
	}

	private InputStream getDownloadStream(final String databaseUrl) throws IOException {
		Download d = Download.create(databaseUrl);
		d.connect();
		int size = d.getSize();
		InputStream dl;

		if (size > 0) {
			ProgressListener progress = new SumProgressListener(
				new PercentageProgressListener(size, this::reportProgress));
			dl = d.getStream(progress);
		} else {
			dl = d.getStream(null);
		}

		return dl;
	}

	private void reportProgress(long percentage) {
		if (progressListener != null && percentage <= 100) {
			progressListener.onProgress(percentage, 100);
		}
	}
}
