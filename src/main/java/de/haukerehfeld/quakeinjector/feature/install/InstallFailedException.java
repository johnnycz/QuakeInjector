package de.haukerehfeld.quakeinjector.feature.install;

import de.haukerehfeld.quakeinjector.model.PackageFileList;

/**
 * Carries a failure out of the extract stage together with the files written before it,
 * so the next pipeline stage can hand them to cleanup.
 */
class InstallFailedException extends RuntimeException {
	private final transient PackageFileList partialFiles;

	InstallFailedException(Throwable cause, PackageFileList partialFiles) {
		super(cause);
		this.partialFiles = partialFiles;
	}

	PackageFileList getPartialFiles() {
		return partialFiles;
	}
}
