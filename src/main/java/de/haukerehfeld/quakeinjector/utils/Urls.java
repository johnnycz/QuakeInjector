package de.haukerehfeld.quakeinjector.utils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/** Turning web addresses put together from settings and database values into valid URIs. */
public final class Urls {
	private Urls() { }

	/**
	 * Escape the characters a URL can't contain as they are, e.g. a space becomes {@code %20} (not
	 * {@code +}, as {@link java.net.URLEncoder} would do). Reserved characters such as {@code /}, {@code ?}
	 * or {@code +} are kept. Use {@link URI#toASCIIString()} to also escape non-ASCII characters.
	 *
	 * @throws URISyntaxException if {@code address} isn't a URL, e.g. it has no or an unknown scheme
	 */
	public static URI escape(String address) throws URISyntaxException {
		URL url;
		try {
			url = new URL(address);
		}
		catch (MalformedURLException e) {
			throw new URISyntaxException(address, e.getMessage());
		}
		return new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(),
			url.getQuery(), url.getRef());
	}
}
