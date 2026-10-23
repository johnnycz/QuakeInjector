package de.haukerehfeld.quakeinjector.feature.list;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.utils.AsyncTask;

public class PackageDatabaseService {

	private final Configuration config;

	public PackageDatabaseService(Configuration config) {
		this.config = config;
	}

	/**
	 * Create an async task to parse the online database.
	 * The task will download the database with caching fallback.
	 */
	public AsyncTask<LoadedPackageDatabase> createParseDatabaseTask() {
		return new PackageDatabaseCacheTask(config);
	}

}
