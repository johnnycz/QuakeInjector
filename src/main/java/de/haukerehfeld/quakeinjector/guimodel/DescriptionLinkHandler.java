package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.DialogProvider;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Follows a link the user clicked in a package description (see {@link DescriptionLink}):
 * <ul>
 * <li>a package's SHA-256 selects that package in the list</li>
 * <li>an http or https address opens in the browser, after the user agreed</li>
 * <li>anything else is logged as an error and not opened</li>
 * </ul>
 * Call it on the UI thread: it may show a dialog.
 */
public class DescriptionLinkHandler {
	static final String[] OPEN_OR_CANCEL = {"Open in Browser", "Cancel"};

	private final DialogProvider dialogs;
	private final Function<String, String> packageIdOfSha256;
	private final Consumer<String> showPackage;
	private final Consumer<String> openInBrowser;

	/**
	 * @param packageIdOfSha256 the id of the loaded package with this SHA-256 (lowercase), or null
	 * @param showPackage selects the package with this id in the list
	 * @param openInBrowser opens an http or https address
	 */
	public DescriptionLinkHandler(DialogProvider dialogs, Function<String, String> packageIdOfSha256,
	                              Consumer<String> showPackage, Consumer<String> openInBrowser) {
		this.dialogs = dialogs;
		this.packageIdOfSha256 = packageIdOfSha256;
		this.showPackage = showPackage;
		this.openInBrowser = openInBrowser;
	}

	public void open(String href) {
		DescriptionLink link = DescriptionLink.of(href);
		if (link instanceof DescriptionLink.ToPackage toPackage) {
			String id = packageIdOfSha256.apply(toPackage.sha256());
			if (id == null) {
				System.err.println("ERROR: No package with SHA-256 " + toPackage.sha256() + " in the list");
				return;
			}
			showPackage.accept(id);
		}
		else if (link instanceof DescriptionLink.ToWebPage webPage) {
			int answer = dialogs.showOptions("Open this address in your web browser?\n\n" + webPage.url(),
				"Open Web Page", OPEN_OR_CANCEL, OPEN_OR_CANCEL[1]);
			if (answer == 0) {
				openInBrowser.accept(webPage.url());
			}
		}
		else {
			System.err.println("ERROR: Not opening link '" + href + "' from the package description:"
				+ " only packages (their SHA-256) and http or https addresses are opened");
		}
	}
}
