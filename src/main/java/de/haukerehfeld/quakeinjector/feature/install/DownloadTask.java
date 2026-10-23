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
import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import de.haukerehfeld.quakeinjector.utils.Download;
import de.haukerehfeld.quakeinjector.utils.PercentageProgressListener;
import de.haukerehfeld.quakeinjector.utils.ProgressListener;
import de.haukerehfeld.quakeinjector.utils.SumProgressListener;

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
	private final CancellationToken token;
	private final AsyncProgressListener progressListener;

	public DownloadTask(Download download, OutputStream out, CancellationToken token) {
		this(download, out, token, null);
	}

	public DownloadTask(Download download, OutputStream out, CancellationToken token,
	                    AsyncProgressListener progressListener) {
		this.download = download;
		this.out = out;
		this.token = token;
		this.progressListener = progressListener;
	}

	@Override
	public Long execute() throws IOException, CancelledException {
		System.out.println("Downloading " + download);
		token.throwIfCancelled();

		download.connect();

		// Build progress filter chain
		final ProgressListener progress = new SumProgressListener(
			new PercentageProgressListener(download.getSize(), this::reportProgress));

		final InputStream in = download.getStream();
		// Closing the stream on cancel wakes up a read() that is blocked on the network
		try (in; CancellationToken.Registration ignored = token.onCancel(() -> closeQuietly(in))) {
			final byte[] data = new byte[BUFFER_SIZE];
			long downloadSize = 0;
			int readcount;

			while ((readcount = in.read(data, 0, BUFFER_SIZE)) != -1) {
				token.throwIfCancelled();

				progress.publish(readcount);
				out.write(data, 0, readcount);
				downloadSize += readcount;
			}

			out.flush();
			return downloadSize;
		}
		catch (IOException e) {
			if (token.isCancelled()) {
				// The stream was closed by cancel(), so this is a cancellation, not an I/O error
				System.out.println("Download cancelled");
				throw new CancelledException();
			}
			throw e;
		}
	}

	private void reportProgress(long percentage) {
		if (progressListener != null && percentage <= 100) {
			progressListener.onProgress(percentage, 100);
		}
	}

	private static void closeQuietly(InputStream in) {
		try {
			in.close();
		}
		catch (IOException e) {
			System.err.println("Couldn't close download stream: " + e.getMessage());
		}
	}
}
