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

import de.haukerehfeld.quakeinjector.utils.AsyncTask;
import de.haukerehfeld.quakeinjector.utils.AsyncProgressListener;
import de.haukerehfeld.quakeinjector.utils.Download;
import de.haukerehfeld.quakeinjector.utils.PercentageProgressListener;
import de.haukerehfeld.quakeinjector.utils.ProgressListener;
import de.haukerehfeld.quakeinjector.utils.SumProgressListener;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Download a file from a URL to an output stream.
 */
public class DownloadTask implements AsyncTask<Long> {
	private static final int BUFFER_SIZE = 1024;

	private final Download download;
	private final OutputStream out;
	private final AsyncProgressListener progressListener;

	private volatile boolean cancelled = false;

	public DownloadTask(Download download, OutputStream out) {
		this(download, out, null);
	}

	public DownloadTask(Download download, OutputStream out, AsyncProgressListener progressListener) {
		this.download = download;
		this.out = out;
		this.progressListener = progressListener;
	}

	@Override
	public Long execute() throws IOException, FileNotFoundException, Installer.CancelledException {
		System.out.println("Downloading " + download);

		download.connect();

		// Build progress filter chain
		final ProgressListener progress = new SumProgressListener(
			new PercentageProgressListener(download.getSize(), this::reportProgress));

		// close the stream when done, otherwise the connection (or local file) stays open
		try (InputStream in = download.getStream()) {
			final byte[] data = new byte[BUFFER_SIZE];
			long downloadSize = 0;
			int readcount;

			while ((readcount = in.read(data, 0, BUFFER_SIZE)) != -1) {
				if (cancelled) {
					System.out.println("Download cancelled");
					throw new Installer.CancelledException();
				}

				progress.publish(readcount);
				out.write(data, 0, readcount);
				downloadSize += readcount;
			}

			out.flush();
			return downloadSize;
		}
	}

	private void reportProgress(long percentage) {
		if (progressListener != null && percentage <= 100) {
			progressListener.onProgress(percentage, 100);
		}
	}

	public void cancel() {
		this.cancelled = true;
	}
}
