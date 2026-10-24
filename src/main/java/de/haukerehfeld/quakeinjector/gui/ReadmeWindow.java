package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.ReadmeViewModel;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Shows the text files of an installed package, one at a time: the file name at the top, with buttons
 * to the previous and next file on its left and right, and the file's text below.
 */
public class ReadmeWindow extends JDialog implements ChangeListener {
	private static final double SCREEN_SHARE = 0.6;
	private static final int MARGIN = 24;
	private static final int FONT_STEP = 2;
	private static final int MIN_FONT_SIZE = 8;
	private static final int MAX_FONT_SIZE = 48;

	private final JLabel fileName = new JLabel("", SwingConstants.CENTER);
	private final JTextArea text = new JTextArea();
	private final JButton smaller;
	private final JButton larger;
	private final JButton previous;
	private final JButton next;
	private final ReadmeViewModel vm;

	public ReadmeWindow(Window owner, ReadmeViewModel vm) {
		super(owner, "Readme - " + vm.getTitle(), ModalityType.MODELESS);
		this.vm = vm;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		previous = new JButton("<");
		previous.setToolTipText("Previous file");
		previous.addActionListener((e) -> vm.previous());
		next = new JButton(">");
		next.setToolTipText("Next file");
		next.addActionListener((e) -> vm.next());
		sameSize(previous, next);
		fileName.setFont(fileName.getFont().deriveFont(Font.BOLD));

		text.setEditable(false);
		// long lines wrap at word boundaries instead of needing a horizontal scroll bar
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setFont(new Font(Font.MONOSPACED, Font.BOLD, text.getFont().getSize() + FONT_STEP));
		text.setMargin(new Insets(8, 10, 8, 10));

		smaller = new JButton("A\u2212");
		smaller.setToolTipText("Smaller text");
		smaller.addActionListener((e) -> changeFontSize(-FONT_STEP));
		larger = new JButton("A+");
		larger.setToolTipText("Larger text");
		larger.addActionListener((e) -> changeFontSize(FONT_STEP));
		sameSize(smaller, larger);

		// previous, file name, next
		JPanel navigation = new JPanel(new BorderLayout(8, 0));
		navigation.add(previous, BorderLayout.LINE_START);
		navigation.add(fileName, BorderLayout.CENTER);
		navigation.add(next, BorderLayout.LINE_END);

		JPanel fontButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		fontButtons.add(smaller);
		fontButtons.add(larger);

		// an empty space as wide as the font buttons on the left, so the file name stays centered
		JPanel top = new JPanel(new BorderLayout(16, 0));
		top.setBorder(BorderFactory.createEmptyBorder(MARGIN / 2, MARGIN, MARGIN / 2, MARGIN));
		top.add(Box.createHorizontalStrut(fontButtons.getPreferredSize().width), BorderLayout.LINE_START);
		top.add(navigation, BorderLayout.CENTER);
		top.add(fontButtons, BorderLayout.LINE_END);

		add(top, BorderLayout.PAGE_START);
		// a visible line around the text, in the theme's border color, and a wide margin around that
		JScrollPane scroll = new JScrollPane(text);
		Color line = UIManager.getColor("widgetBorder");
		scroll.setBorder(BorderFactory.createLineBorder(line != null ? line : Color.GRAY));
		JPanel textPanel = new JPanel(new BorderLayout());
		textPanel.setBorder(BorderFactory.createEmptyBorder(0, MARGIN, MARGIN, MARGIN));
		textPanel.add(scroll, BorderLayout.CENTER);
		add(textPanel, BorderLayout.CENTER);

		// Escape closes the window
		getRootPane().registerKeyboardAction((e) -> dispose(),
			KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		sizeToScreen(owner);
		vm.addChangeListener(this);
		vm.start();
	}

	/** Gives both buttons the size of the larger one. */
	private static void sameSize(JButton a, JButton b) {
		Dimension pa = a.getPreferredSize();
		Dimension pb = b.getPreferredSize();
		Dimension size = new Dimension(Math.max(pa.width, pb.width), Math.max(pa.height, pb.height));
		a.setPreferredSize(size);
		b.setPreferredSize(size);
	}

	private void changeFontSize(int delta) {
		Font font = text.getFont();
		int size = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, font.getSize() + delta));
		text.setFont(font.deriveFont((float) size));
		smaller.setEnabled(size > MIN_FONT_SIZE);
		larger.setEnabled(size < MAX_FONT_SIZE);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		Edt.check("ReadmeWindow");
		int count = vm.getFileCount();
		fileName.setText(count == 0 ? "" : vm.getCurrentFile() + "  (" + (vm.getCurrentIndex() + 1) + " of " + count + ")");
		previous.setEnabled(count > 1);
		next.setEnabled(count > 1);
		text.setText(vm.getText());
		text.setCaretPosition(0);
	}

	/** 60% of the width and height of the screen the owner is on, centered on it. */
	private void sizeToScreen(Window owner) {
		GraphicsConfiguration screen = owner != null ? owner.getGraphicsConfiguration()
			: GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
		Rectangle bounds = screen.getBounds();
		int width = (int) (bounds.width * SCREEN_SHARE);
		int height = (int) (bounds.height * SCREEN_SHARE);
		setBounds(bounds.x + (bounds.width - width) / 2, bounds.y + (bounds.height - height) / 2, width, height);
	}
}
