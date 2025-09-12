package de.haukerehfeld.quakeinjector.feature.list;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.DialogProvider;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.utils.Download;

import javax.swing.*;
import java.io.*;
import java.util.List;
import java.util.concurrent.Future;

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
	 * Parse the online database
	 */
	public Future<List<Requirement>> doParseDatabase() {

		final String databaseUrl = config.RepositoryDatabasePath.get();

		final SwingWorker<List<Requirement>, Void> dbParse
				= new SwingWorker<>() {
			/**
			 * the stream for the database download
			 **/
			private BufferedInputStream downloadStream;
			/**
			 * we need to try to download the db to a tmp file first so the old one doesn't get overwritten
			 */
			private File tmpFile;
			/**
			 * the stream to the temporary file
			 */
			private BufferedOutputStream tmpWriteStream;
			/**
			 * the cached database file
			 **/
			private File cache;
			/**
			 * stream from the cached database file, if needed
			 **/
			private BufferedInputStream cacheReadStream;
			/**
			 * whether the temporary file was populated with a good DB
			 **/
			private boolean updateCache = false;

			private BufferedInputStream cachedDatabaseStream() throws IOException {
				if (cache != null && cache.exists() && cache.canRead()) {
					try {
						return new BufferedInputStream(new FileInputStream(cache));
					} catch (IOException ignored) {
					}
				}
				throw new IOException("cannot download package database or read local cache");
			}

			@Override
			public List<Requirement> doInBackground() throws IOException {
				cache = config.LocalDatabaseFile.get();
				cache = cache.getAbsoluteFile();
				InputStream db;
				try {
					//download database and dump to file
					downloadStream = new BufferedInputStream(downloadDatabase(databaseUrl));
					tmpFile = File.createTempFile(cache.getName(), null, cache.getParentFile());
					tmpWriteStream = new BufferedOutputStream(new FileOutputStream(tmpFile));
					db = new DumpInputStream(downloadStream, tmpWriteStream);
					List<Requirement> parseResult = parseDatabase(db);
					updateCache = true;
					return parseResult;
				} catch (Exception e) {
					cacheReadStream = cachedDatabaseStream();
					return parseDatabase(cacheReadStream);
				}
			}

			@Override
			public void done() {
				try {
					if (cacheReadStream != null) {
						cacheReadStream.close();
					}
					if (tmpWriteStream != null) {
						tmpWriteStream.close();
					}
					if (downloadStream != null) {
						downloadStream.close();
					}
				} catch (IOException e) {
				}
				if (updateCache) {
					if (cache.exists()) {
						if (!cache.delete()) {
							System.err.println("Couldn't delete the real cache file!");
						}
					}
					if (!tmpFile.renameTo(cache)) {
						System.err.println("Couldn't move the temporary cache file to the real cache file!");
					}
				} else {
					if (tmpFile != null && tmpFile.exists()) {
						tmpFile.delete();
					}
					String msg = "Failed to fetch current database; using previously downloaded info.";
					dialogProvider.showWarning(msg, "Downloading failed");
				}
			}
		};

		var progressReport = dialogProvider.showProgressPopup("Downloading package database", () -> dbParse.cancel(true));
		dbParse.addPropertyChangeListener(evt -> {
			if ("progress".equals(evt.getPropertyName())) {
				int p = (Integer) evt.getNewValue();
				progressReport.setProgress(p);
			}
			else if ("state".equalsIgnoreCase(evt.getPropertyName())
					&& evt.getNewValue().equals(SwingWorker.StateValue.DONE)) {
				progressReport.finish("Done");
			}
		});
		dbParse.execute();
		progressReport.start();
		return dbParse;
	}

}
