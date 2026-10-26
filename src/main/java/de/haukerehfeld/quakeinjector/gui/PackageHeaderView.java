package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The top of the info panel: the title of the selected package, the action buttons below it, and a line. It
 * doesn't scroll, and it keeps its height when nothing is selected.
 */
public class PackageHeaderView extends JPanel implements ChangeListener {
	/** Shown instead of an empty title, so the label keeps the height of one line. */
	private static final String BLANK = " ";

	private final PackageInteractionViewModel vm;

	private final JLabel title;

	public PackageHeaderView(PackageInteractionViewModel vm, Component actions) {
		super(new GridBagLayout());
		this.vm = vm;

		title = new JLabel() {
			// a long title gets cut off instead of making the panel wider
			@Override
			public Dimension getMinimumSize() {
				return new Dimension(0, super.getMinimumSize().height);
			}
		};
		// larger than the Install/Play buttons (1.3 times the normal size)
		title.setFont(title.getFont().deriveFont(title.getFont().getSize2D() * 1.6f));
		title.setHorizontalAlignment(SwingConstants.CENTER);
		title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		title.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				vm.openPackageWebpage();
			}
		});
		add(title, new GridBagConstraints() {{
			fill = HORIZONTAL;
			weightx = 1;
			ipadx = 5;
			ipady = 30;
		}});

		add(actions, new GridBagConstraints() {{
			gridy = 1;
			fill = HORIZONTAL;
			weightx = 1;
		}});

		// separates the header from the screenshot and description below
		add(new JSeparator(), new GridBagConstraints() {{
			gridy = 2;
			fill = HORIZONTAL;
			weightx = 1;
			insets = new Insets(6, 0, 0, 0);
		}});

		vm.addChangeListener(this);
		refreshUi();
	}

	private void refreshUi() {
		String text = vm.getTitle();
		title.setText(text == null || text.isEmpty() ? BLANK : text);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		Edt.check("PackageHeaderView");
		refreshUi();
	}
}
