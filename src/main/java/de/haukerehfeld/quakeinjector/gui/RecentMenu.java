package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.RecentPackagesViewModel;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Recent menu: the recently installed or played packages, newest first. Clicking one passes its id
 * to the select callback. Rebuilt whenever the view model changes.
 */
public class RecentMenu extends JMenu implements ChangeListener {
	private final RecentPackagesViewModel vm;
	private Consumer<String> onSelect = id -> { };

	public RecentMenu(RecentPackagesViewModel vm) {
		super("Recent");
		setMnemonic(KeyEvent.VK_R);
		this.vm = vm;
		vm.addChangeListener(this);
		rebuild();
	}

	/** Called with the id of the clicked package. */
	public void setSelectListener(Consumer<String> onSelect) {
		this.onSelect = onSelect;
	}

	private void rebuild() {
		removeAll();
		List<RecentPackagesViewModel.Item> items = vm.items();
		if (items.isEmpty()) {
			JMenuItem none = new JMenuItem("No recent maps");
			none.setEnabled(false);
			add(none);
			return;
		}
		for (RecentPackagesViewModel.Item item : items) {
			JMenuItem menuItem;
			if (item.mnemonic() != null) {
				menuItem = PlainText.menuItem(item.mnemonic() + "  " + item.label());
				menuItem.setMnemonic(item.mnemonic());
			} else {
				menuItem = PlainText.menuItem(item.label());
			}
			String id = item.id();
			menuItem.addActionListener(e -> onSelect.accept(id));
			add(menuItem);
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		Edt.check("RecentMenu");
		rebuild();
	}
}
