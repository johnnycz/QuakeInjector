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
package de.haukerehfeld.quakeinjector.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;

import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import de.haukerehfeld.quakeinjector.utils.QuakeEngineDetector.EngineType;
import de.haukerehfeld.quakeinjector.utils.QuakeEngineSearch;
import de.haukerehfeld.quakeinjector.utils.QuakeEngineSearch.Result;

/**
 * Picks the Quake engine. On opening it searches the Quake directory and its direct subfolders for
 * exe files on a background thread and lists the Quake engines among them, modern engines first.
 * "Show only recommended" (checked at first) limits the list to the modern engines; exe files that
 * aren't engines are never shown. A button at the bottom left chooses
 * another executable with the file chooser. Closing the dialog stops the search.
 */
public class EngineSelectionDialog extends JDialog {
	private static final String windowTitle = "Quake Engine";

	private static final Executor UI = SwingUtilities::invokeLater;

	private static final String VKQUAKE_URL = "https://github.com/Novum/vkQuake";

	private final File quakeDir;
	private final JPathPanel engineExecutable;
	private final CancellationToken token = new CancellationToken();

	private final JLabel progress = new JLabel();
	private final JCheckBox onlyRecommended = new JCheckBox("Show only recommended", true);
	private final DefaultListModel<Result> engineModel = new DefaultListModel<>();
	private final JList<Result> engines = new JList<>(engineModel);

	/** All exe files found, in {@link QuakeEngineSearch#ORDER}; empty until the search is done */
	private List<Result> results = List.of();

	/**
	 * @param quakeDir the Quake directory to search, a valid directory
	 * @param engineExecutable the settings' engine path field, set by Okay and the manual choice
	 */
	public EngineSelectionDialog(Window owner, File quakeDir, JPathPanel engineExecutable) {
		super(owner, windowTitle, ModalityType.APPLICATION_MODAL);
		this.quakeDir = quakeDir;
		this.engineExecutable = engineExecutable;

		// top: search progress, and the filter to the right of it
		onlyRecommended.addItemListener(e -> fillList(selectedFile()));

		JPanel top = new JPanel(new BorderLayout(LookAndFeelDefaults.FRAMEPADDING, 0));
		top.add(progress, BorderLayout.CENTER);
		top.add(onlyRecommended, BorderLayout.LINE_END);
		top.setBorder(BorderFactory.createEmptyBorder(0, 0, LookAndFeelDefaults.FRAMEPADDING, 0));

		// the rest: the exe files found
		engines.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		engines.setVisibleRowCount(12);
		engines.setCellRenderer(new ResultRenderer());

		JPanel content = new JPanel(new BorderLayout());
		content.setBorder(LookAndFeelDefaults.PADDINGBORDER);
		content.add(top, BorderLayout.PAGE_START);
		content.add(new JScrollPane(engines), BorderLayout.CENTER);
		add(content, BorderLayout.CENTER);

		// bottom: the manual choice on the left, Okay and Cancel on the right
		JButton chooseManually = new JButton("Select other executable...");
		chooseManually.addActionListener(e -> {
			if (engineExecutable.chooseFile(this)) {
				dispose();
			}
		});
		final JButton okay = new JButton("Okay");
		final JButton cancel = new JButton("Cancel");
		okay.setEnabled(false);
		engines.addListSelectionListener(e -> okay.setEnabled(!engines.isSelectionEmpty()));
		okay.addActionListener(e -> useSelected());
		cancel.addActionListener(e -> dispose());

		JPanel bottom = new JPanel(new BorderLayout());
		JPanel left = new JPanel(new GridBagLayout());
		final int pad = LookAndFeelDefaults.FRAMEPADDING;
		left.add(chooseManually, new GridBagConstraints() {{
			insets = new Insets(pad, pad, pad, 0);
		}});
		bottom.add(left, BorderLayout.LINE_START);
		bottom.add(new OkayCancelApplyPanel(okay, cancel, null, false), BorderLayout.CENTER);
		add(bottom, BorderLayout.PAGE_END);

		// double click picks a row like Okay
		engines.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && !engines.isSelectionEmpty()) {
					useSelected();
				}
			}
		});

		// Enter presses Okay (does nothing while it is disabled), Escape cancels
		getRootPane().setDefaultButton(okay);
		getRootPane().registerKeyboardAction(e -> dispose(),
			KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
			JComponent.WHEN_IN_FOCUSED_WINDOW);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				token.cancel();
			}
		});

		progress.setText("Looking for Quake engines in " + quakeDir + "...");
		startSearch();

		pack();
		setMinimumSize(new Dimension(500, 300));
		setLocationRelativeTo(owner);
	}

	private void startSearch() {
		Executor searchThread = r -> {
			Thread t = new Thread(r, "Quake engine search");
			t.setDaemon(true);
			t.start();
		};
		CompletableFuture
			.supplyAsync(() -> QuakeEngineSearch.search(quakeDir, token, this::reportProgress), searchThread)
			.whenCompleteAsync(this::showResults, UI);
	}

	/** On the search thread */
	private void reportProgress(int done, int total) {
		UI.execute(() -> {
			if (!token.isCancelled()) {
				progress.setText("Checking " + done + " of " + total + " exe files...");
			}
		});
	}

	private void showResults(List<Result> found, Throwable error) {
		if (token.isCancelled()) {
			return; // closed meanwhile
		}
		if (error != null) {
			Throwable cause = error instanceof CompletionException ? error.getCause() : error;
			if (!(cause instanceof CancelledException)) {
				progress.setText("Search failed: " + cause);
			}
			return;
		}

		long engineCount = found.stream().filter(r -> r.getType() != EngineType.NOT_ENGINE).count();
		if (found.isEmpty()) {
			progress.setText("No exe files found in " + quakeDir);
		}
		else {
			progress.setText("Found " + engineCount + (engineCount == 1 ? " engine" : " engines")
			    + " in " + found.size() + " exe " + (found.size() == 1 ? "file" : "files"));
		}

		results = found;
		// the engine from the settings is selected if it is shown
		fillList(engineExecutable.getPath());

		if (found.stream().noneMatch(r -> r.getType() == EngineType.MODERN)) {
			whenShown(this::showNoRecommendedEngine);
		}
	}

	/** Run r now if the dialog is on screen, otherwise once it opens */
	private void whenShown(Runnable r) {
		if (isShowing()) {
			r.run();
			return;
		}
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				removeWindowListener(this);
				r.run();
			}
		});
	}

	private void showNoRecommendedEngine() {
		JEditorPane message = new JEditorPane("text/html",
			"<html><body>"
			+ "No recommended Quake engine was found in the Quake Directory,<br>"
			+ "so a lot of the packages won't be playable.<br><br>"
			+ "We recommend downloading a modern Quake engine such as<br>"
			+ "<a href=\"" + VKQUAKE_URL + "\">vkQuake</a> (" + VKQUAKE_URL + ")."
			+ "</body></html>");
		message.setEditable(false);
		message.setOpaque(false);
		// the look and feel's label font instead of the HTML default
		message.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		message.setFont(UIManager.getFont("Label.font"));
		message.addHyperlinkListener(e -> {
			if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				BrowserLauncher.openURL(e.getDescription());
			}
		});
		JOptionPane.showMessageDialog(this, message, "No Recommended Engine Found",
		                              JOptionPane.WARNING_MESSAGE);
	}

	/**
	 * Show the results the filter lets through, and select the one at toSelect if it is among them
	 */
	private void fillList(File toSelect) {
		Path select = toSelect == null ? null : normalize(toSelect);
		engineModel.clear();
		for (Result r : results) {
			if (r.getType() == EngineType.NOT_ENGINE
			    || (onlyRecommended.isSelected() && r.getType() != EngineType.MODERN)) {
				continue;
			}
			engineModel.addElement(r);
			if (normalize(r.getFile()).equals(select)) {
				engines.setSelectedIndex(engineModel.size() - 1);
			}
		}
		if (!engines.isSelectionEmpty()) {
			engines.ensureIndexIsVisible(engines.getSelectedIndex());
		}
	}

	private File selectedFile() {
		Result selected = engines.getSelectedValue();
		return selected == null ? null : selected.getFile();
	}

	private void useSelected() {
		File selected = selectedFile();
		if (selected != null) {
			engineExecutable.setPath(selected);
			dispose();
		}
	}

	private static Path normalize(File f) {
		return f.getAbsoluteFile().toPath().normalize();
	}

	/** An exe file as its path in the Quake directory */
	private class ResultRenderer extends DefaultListCellRenderer {
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index,
		                                              boolean isSelected, boolean cellHasFocus) {
			Result r = (Result) value;
			String path = normalize(quakeDir).relativize(normalize(r.getFile())).toString();
			return super.getListCellRendererComponent(list, path, index, isSelected, cellHasFocus);
		}
	}
}
