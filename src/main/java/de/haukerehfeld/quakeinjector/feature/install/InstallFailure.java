package de.haukerehfeld.quakeinjector.feature.install;

import de.haukerehfeld.quakeinjector.model.PackageFileList;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import de.haukerehfeld.quakeinjector.utils.HTTPException;
import de.haukerehfeld.quakeinjector.utils.OnlineFileNotFoundException;
import de.haukerehfeld.quakeinjector.utils.SafePath;
import de.haukerehfeld.quakeinjector.utils.UnsafePathException;
import org.apache.commons.compress.archivers.ArchiveException;

import java.io.IOException;
import java.net.SocketException;

/**
 * Why an install stopped, plus the files it had already written (which need cleaning up).
 */
public final class InstallFailure {
	public enum Kind {
		/** The user cancelled. */
		CANCELLED,
		/** The package isn't in the online repository. */
		NOT_FOUND,
		/** A file in the install directory couldn't be written. */
		NOT_WRITABLE,
		/** A file of the package would end up outside its directory (see {@link SafePath}). */
		UNSAFE_PATH,
		/** The download failed. */
		NETWORK,
		/** The downloaded archive couldn't be read. */
		IO,
		/** Anything else; a bug. */
		UNEXPECTED
	}

	private final Kind kind;
	private final Throwable cause;
	private final PackageFileList partialFiles;

	public InstallFailure(Kind kind, Throwable cause, PackageFileList partialFiles) {
		this.kind = kind;
		this.cause = cause;
		this.partialFiles = partialFiles;
	}

	/**
	 * Classify an error that ended an install pipeline.
	 *
	 * @param error the error, possibly wrapped by CompletableFuture or {@link InstallFailedException}
	 * @param packageId used for the (empty) file list when the error happened before extraction
	 */
	static InstallFailure of(Throwable error, String packageId) {
		Throwable cause = Futures.unwrap(error);
		PackageFileList partialFiles = new PackageFileList(packageId);
		if (cause instanceof InstallFailedException failed) {
			partialFiles = failed.getPartialFiles();
			cause = failed.getCause();
		}
		return new InstallFailure(kindOf(cause), cause, partialFiles);
	}

	private static Kind kindOf(Throwable cause) {
		// order matters: the more specific exception types come first
		if (cause instanceof CancelledException) {
			return Kind.CANCELLED;
		}
		if (cause instanceof OnlineFileNotFoundException) {
			return Kind.NOT_FOUND;
		}
		if (cause instanceof FileNotWritableException) {
			return Kind.NOT_WRITABLE;
		}
		if (cause instanceof UnsafePathException) {
			return Kind.UNSAFE_PATH;
		}
		if (cause instanceof SocketException || cause instanceof HTTPException) {
			return Kind.NETWORK;
		}
		if (cause instanceof IOException || cause instanceof ArchiveException) {
			return Kind.IO;
		}
		return Kind.UNEXPECTED;
	}

	public Kind getKind() {
		return kind;
	}

	public Throwable getCause() {
		return cause;
	}

	/**
	 * Files and dirs written before the install stopped; empty if it stopped before extracting.
	 * By the time an {@link InstallListener} sees this, the pipeline has already deleted them.
	 */
	public PackageFileList getPartialFiles() {
		return partialFiles;
	}

	@Override
	public String toString() {
		return kind + ": " + cause;
	}
}
