package de.haukerehfeld.quakeinjector.feature.list;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.DialogProvider;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.utils.AsyncTask;
import de.haukerehfeld.quakeinjector.utils.Download;

import java.io.*;
import java.util.List;

public class PackageDatabaseService {

	private final Configuration config;
	private final DialogProvider dialogProvider;

	public PackageDatabaseService(Configuration config, DialogProvider dialogProvider) {
		this.config = config;
		this.dialogProvider = dialogProvider;
	}


	private List<Requirement> parseDatabase(InputStream database) {
		final PackageDatabaseParser parser = new PackageDatabaseSolrJsonParser(config);

		return parser.parse(database);
	}


	private InputStream downloadDatabase(String databaseUrl) throws IOException {
		//get download stream
		Download d = Download.create(databaseUrl);
		d.connect();
		InputStream dl;
		//int size = d.getSize();
		// if (size > 0) {
		// 	ProgressListener progress =
		// 	    new SumProgressListener(new PercentageProgressListener(size, this));
		// 	dl = d.getStream(progress);
		// }
		// else {
		dl = d.getStream(null);
		//}

		return dl;
	}

	/**
	 * Create an async task to parse the online database.
	 * The task will download the database with caching fallback.
	 */
	public AsyncTask<LoadedPackageDatabase> createParseDatabaseTask() {
		return new PackageDatabaseCacheTask(config);
	}

}
