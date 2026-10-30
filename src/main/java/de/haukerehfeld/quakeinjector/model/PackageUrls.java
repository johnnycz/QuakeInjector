package de.haukerehfeld.quakeinjector.model;

import de.haukerehfeld.quakeinjector.utils.Urls;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * The web addresses of a package on Quaddicted: its page and its screenshot, both found by the
 * package's SHA-256 under the addresses in the settings.
 */
public class PackageUrls {
	private final Configuration config;

	public PackageUrls(Configuration config) {
		this.config = config;
	}

	/**
	 * The package's page, e.g. {@code https://www.quaddicted.com/db/v1/maps/<sha256>}.
	 *
	 * @return null if the package has no SHA-256
	 * @throws URISyntaxException if the address in the settings makes no valid URL
	 */
	public URI webpage(Package pkg) throws URISyntaxException {
		String sha256 = pkg.getSha256();
		if (sha256 == null || sha256.isEmpty()) {
			return null;
		}
		return Urls.escape(config.mapWebpageBaseUrl.get() + sha256);
	}

	/**
	 * The package's screenshot, e.g. {@code https://www.quaddicted.com/files/quaddicted-images/by-sha256/
	 * <first two digits of the sha256>/<sha256>/<id>.jpg}.
	 *
	 * @return null if the package has no SHA-256
	 * @throws URISyntaxException if the address in the settings makes no valid URL
	 */
	public URI screenshot(Package pkg) throws URISyntaxException {
		String sha256 = pkg.getSha256();
		if (sha256 == null || sha256.length() < 2) {
			return null;
		}
		return Urls.escape(config.ScreenshotRepositoryPath.get()
			+ sha256.substring(0, 2) + "/" + sha256 + "/" + pkg.getId() + ".jpg");
	}
}
