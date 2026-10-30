/*
Copyright 2014 Eric Wasylishen


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
package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.DescriptionLink;

import java.awt.Desktop;
import java.net.URI;

/**
 * Opens web pages in the browser. Only http and https: links come from the package database, and
 * the operating system would open anything else (file:, ms-msdt:, search-ms:, ...) with whatever
 * program handles it, which can run code.
 */
public class BrowserLauncher {
	private static edu.stanford.ejalbert.BrowserLauncher fallback = null;

	/** Whether {@link #openURL} opens the URL: an absolute http or https URL with a host. */
	public static boolean isWebAddress(String url) {
		return DescriptionLink.isWebAddress(url);
	}

	public static void openURL(String url) {
		if (!isWebAddress(url)) {
			System.err.println("Not opening " + url + ": only http and https links are opened");
			return;
		}
		try {
			Desktop.getDesktop().browse(URI.create(url));
		} catch (Exception e) {
			System.err.println("Error calling Desktop#browse(URI): " + e.getMessage());
			
			if (fallback == null) {
				try {
					fallback = new edu.stanford.ejalbert.BrowserLauncher();
				} catch (Exception e2) {
					System.err.println("Couldn't init browserlauncher: " + e2.getMessage());
				}
			}
			
			if (fallback != null) {
				fallback.openURLinBrowser(url);
			}
		}		
	}
}
