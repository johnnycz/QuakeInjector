package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.PackageDescriptionView;
import de.haukerehfeld.quakeinjector.guimodel.DescriptionLink;
import de.haukerehfeld.quakeinjector.guimodel.DescriptionLinkHandler;
import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.guimodel.PackageListViewModel;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageList;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import java.awt.Component;
import java.awt.Container;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Links in a package description: a package's SHA-256 selects it, an http(s) address opens in the
 * browser after asking, anything else is only logged.
 */
public class DescriptionLinkTest {
	private static final String SHA_A = "6147391e03cbcde1f8d5c8c105f8957736091984f2e183227cd1da9a620b2ffd";
	private static final String SHA_UNKNOWN = "0".repeat(64);

	private final RecordingDialogs dialogs = new RecordingDialogs();
	private final List<String> shown = new ArrayList<>();
	private final List<String> opened = new ArrayList<>();
	private final DescriptionLinkHandler handler = new DescriptionLinkHandler(dialogs,
		sha -> sha.equals(SHA_A) ? "a" : null, shown::add, opened::add);

	private final ByteArrayOutputStream err = new ByteArrayOutputStream();
	private PrintStream originalErr;

	@BeforeEach
	public void captureErrors() {
		originalErr = System.err;
		System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	public void restoreErrors() {
		System.setErr(originalErr);
	}

	private String errors() {
		return err.toString(StandardCharsets.UTF_8);
	}

	@Test
	public void linksAreSortedIntoThreeKinds() {
		assertEquals(new DescriptionLink.ToPackage(SHA_A), DescriptionLink.of(SHA_A));
		assertEquals(new DescriptionLink.ToPackage(SHA_A), DescriptionLink.of(SHA_A.toUpperCase()));
		assertEquals(new DescriptionLink.ToWebPage("https://example.com/a"), DescriptionLink.of("https://example.com/a"));
		assertEquals(new DescriptionLink.ToWebPage("http://example.com/"), DescriptionLink.of("http://example.com/"));

		for (String href : new String[] {
			"file:///C:/Quake/id1/evil.bat", "file://attacker/share/x", "javascript:alert(1)", "ms-msdt:/id x",
			"mailto:a@b.c", "../?include=metadata-%3Etags", SHA_A + ".html", SHA_A.substring(1), "a.html",
			"https://", "//example.com/x", "", "https://example.com/\nx",
		}) {
			assertEquals(new DescriptionLink.Refused(href), DescriptionLink.of(href), href);
		}
		assertInstanceOf(DescriptionLink.Refused.class, DescriptionLink.of(null));
	}

	@Test
	public void aPackageLinkSelectsThePackage() {
		handler.open(SHA_A);

		assertEquals(List.of("a"), shown);
		assertEquals(List.of(), dialogs.calls);
		assertEquals(List.of(), opened);
	}

	@Test
	public void aLinkToAPackageNotInTheListIsLogged() {
		handler.open(SHA_UNKNOWN);

		assertEquals(List.of(), shown);
		assertTrue(errors().contains("ERROR") && errors().contains(SHA_UNKNOWN), errors());
	}

	@Test
	public void aWebLinkOpensAfterTheUserAgreed() {
		dialogs.optionAnswer = 0;

		handler.open("https://example.com/a");

		assertEquals(List.of("options: Open Web Page"), dialogs.calls);
		assertTrue(dialogs.optionsMessage.contains("https://example.com/a"), dialogs.optionsMessage);
		assertEquals(List.of("https://example.com/a"), opened);
	}

	@Test
	public void aWebLinkStaysClosedIfTheUserDoesntAgree() {
		dialogs.optionAnswer = 1;
		handler.open("https://example.com/a");

		// closing the dialog
		dialogs.optionAnswer = -1;
		handler.open("https://example.com/a");

		assertEquals(List.of(), opened);
	}

	@Test
	public void anyOtherLinkIsLoggedAndNotOpened() {
		// optionAnswer stays null: asking would fail the test
		handler.open("file:///C:/Quake/id1/evil.bat");
		handler.open("../?include=metadata-%3Etags");

		assertEquals(List.of(), dialogs.calls);
		assertEquals(List.of(), opened);
		assertEquals(List.of(), shown);
		assertTrue(errors().contains("ERROR: Not opening link 'file:///C:/Quake/id1/evil.bat'"), errors());
		assertTrue(errors().contains("'../?include=metadata-%3Etags'"), errors());
	}

	@Test
	public void clickingInTheDescriptionPaneUsesTheHandler() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Package a = TestZip.testPackage("a", new ExtractMapping(), "file:///unused.zip");
			Package b = new Package("b", SHA_A, "b.zip", List.of("file:///unused.zip"), "author", "B",
				0, null, false, 0f, "See <a href=\"" + SHA_A + "\">B</a>", new ExtractMapping(), "", List.of(), List.of());
			PackageInteractionViewModel vm = new PackageInteractionViewModel(null, null);
			PackageListViewModel list = new PackageListViewModel(vm);
			list.setMapList(new PackageList(new RequirementList(new ArrayList<>(List.of(a, b)))));
			vm.setDescriptionLinkHandler(new DescriptionLinkHandler(dialogs, list::packageIdOfSha256, shown::add, opened::add));
			PackageDescriptionView view = new PackageDescriptionView(vm);
			vm.setSelectedPackage(a);
			JEditorPane description = descriptionPane(view);

			click(description, SHA_A);
			click(description, "file:///C:/x.bat");

			assertEquals(List.of("b"), shown);
			assertEquals(List.of(), opened);
			assertTrue(errors().contains("file:///C:/x.bat"), errors());
		});
	}

	@Test
	public void prerequisitesLinkByTheirSha256() {
		Package prerequisite = new Package("base", SHA_A, "base.zip", List.of("file:///unused.zip"), "author", "Base",
			0, null, false, 0f, "", new ExtractMapping(), "", List.of(), List.of());
		Package pkg = TestZip.testPackage("a", new ExtractMapping(), "file:///unused.zip");
		pkg.setRequirements(List.of(prerequisite, TestZip.testPackage("nohash", new ExtractMapping(), "file:///unused.zip")));
		PackageInteractionViewModel vm = new PackageInteractionViewModel(null, null);
		vm.setSelectedPackage(pkg);

		List<PackageInteractionViewModel.Prerequisite> prerequisites = vm.getRequirements();

		assertTrue(prerequisites.get(0).isLinked());
		assertEquals(SHA_A, prerequisites.get(0).sha256());
		assertFalse(prerequisites.get(1).isLinked(), "a package without a valid SHA-256 has no link");
	}

	private static void click(JEditorPane pane, String href) {
		pane.fireHyperlinkUpdate(new HyperlinkEvent(pane, HyperlinkEvent.EventType.ACTIVATED, null, href));
	}

	/** The pane with the description: the one that isn't the authors line. */
	private static JEditorPane descriptionPane(Container container) {
		List<JEditorPane> panes = new ArrayList<>();
		collect(container, panes);
		return panes.stream().filter(p -> p.getHyperlinkListeners().length > 0).findFirst().orElseThrow();
	}

	private static void collect(Container container, List<JEditorPane> panes) {
		for (Component c : container.getComponents()) {
			if (c instanceof JEditorPane pane) {
				panes.add(pane);
			}
			else if (c instanceof Container child) {
				collect(child, panes);
			}
		}
	}
}
