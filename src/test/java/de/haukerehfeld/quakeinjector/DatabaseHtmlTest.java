package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.gui.PackageDescriptionView;
import de.haukerehfeld.quakeinjector.gui.PackageTable;
import de.haukerehfeld.quakeinjector.gui.PackageTableModel;
import de.haukerehfeld.quakeinjector.guimodel.Column;
import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.guimodel.PackageListViewModel;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageList;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.ElementIterator;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A package database entry full of HTML tricks, shown in the real views: nothing in it may load anything
 * or link anywhere but to web pages. Headless, on the event thread.
 */
public class DatabaseHtmlTest {
	private static final String EVIL_DESCRIPTION = "A map. <img src=\"file://attacker/share/x.png\">"
		+ "<p style=\"background-image: url(http://tracker.example/x.gif)\">Text</p>"
		+ "<object classid=\"javax.swing.JButton\"></object>"
		+ "<a href=\"file:///C:/Quake/id1/evil.bat\">Click</a> "
		+ "<a href=\"https://example.com/ok\">Fine</a>";

	private static final String SHA256 = "6147391e03cbcde1f8d5c8c105f8957736091984f2e183227cd1da9a620b2ffd";

	private static Package pkg(String id, String title, String description) {
		return pkg(id, title, description, "sha");
	}

	private static Package pkg(String id, String title, String description, String sha256) {
		return new Package(id, sha256, id + ".zip", List.of("file:///unused.zip"), "author", title,
			0, null, false, 0f, description, new ExtractMapping(), "", List.of(), List.of());
	}

	private static void onEdt(Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	private static List<JEditorPane> editorPanes(Container container) {
		List<JEditorPane> panes = new ArrayList<>();
		for (Component c : container.getComponents()) {
			if (c instanceof JEditorPane pane) {
				panes.add(pane);
			}
			else if (c instanceof Container child) {
				panes.addAll(editorPanes(child));
			}
		}
		return panes;
	}

	/** The tags and link targets in the pane's HTML document. */
	private static void collect(JEditorPane pane, List<HTML.Tag> tags, List<String> hrefs) {
		ElementIterator it = new ElementIterator(pane.getDocument());
		Element e;
		while ((e = it.next()) != null) {
			AttributeSet attributes = e.getAttributes();
			if (attributes.getAttribute(StyleConstants.NameAttribute) instanceof HTML.Tag tag) {
				tags.add(tag);
			}
			if (attributes.getAttribute(HTML.Tag.A) instanceof AttributeSet link
					&& link.getAttribute(HTML.Attribute.HREF) != null) {
				hrefs.add(link.getAttribute(HTML.Attribute.HREF).toString());
			}
			if (attributes.getAttribute(HTML.Attribute.STYLE) != null) {
				tags.add(HTML.Tag.STYLE);
			}
		}
	}

	@Test
	public void theDescriptionLoadsNothingAndLinksOnlyToWebPages() throws Exception {
		onEdt(() -> {
			Package evil = pkg("evil", "Evil", EVIL_DESCRIPTION);
			// a prerequisite links by its SHA-256; one without a valid SHA-256 isn't linked
			evil.setRequirements(List.<Requirement>of(pkg("\"><img src=x>", "t", "d", SHA256), pkg("file:x", "t", "d")));
			PackageInteractionViewModel vm = new PackageInteractionViewModel(null, null);
			PackageDescriptionView view = new PackageDescriptionView(vm);

			vm.setSelectedPackage(evil);

			List<HTML.Tag> tags = new ArrayList<>();
			List<String> hrefs = new ArrayList<>();
			for (JEditorPane pane : editorPanes(view)) {
				collect(pane, tags, hrefs);
			}
			assertFalse(tags.contains(HTML.Tag.IMG), "images: " + tags);
			assertFalse(tags.contains(HTML.Tag.OBJECT), "objects: " + tags);
			assertFalse(tags.contains(HTML.Tag.STYLE), "styles: " + tags);
			// the web link and the prerequisite's link stay, the file link doesn't
			assertTrue(hrefs.contains("https://example.com/ok"), "links: " + hrefs);
			assertTrue(hrefs.contains(SHA256), "the prerequisite links by its SHA-256: " + hrefs);
			assertEquals(2, hrefs.size(), "links: " + hrefs);
		});
	}

	@Test
	public void tableCellsShowTitlesAsTheyAre() throws Exception {
		onEdt(() -> {
			Package evil = pkg("evil", "<html><img src=\"file://attacker/share/x.png\">", "d");
			PackageInteractionViewModel interaction = new PackageInteractionViewModel(null, null);
			PackageListViewModel listVm = new PackageListViewModel(interaction);
			listVm.setMapList(new PackageList(new RequirementList(new ArrayList<>(List.of(evil)))));
			PackageTable table = new PackageTable(new PackageTableModel(listVm, interaction));
			int column = table.convertColumnIndexToView(Column.getColumnNumber(Column.TITLE));

			Component cell = table.prepareRenderer(table.getCellRenderer(0, column), 0, column);

			assertNull(((JComponent) cell).getClientProperty(BasicHTML.propertyKey), "the title is shown as HTML");
		});
	}
}
