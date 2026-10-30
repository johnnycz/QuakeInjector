package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.guimodel.PackageHistoryViewModel;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * The Back and Forward buttons, Alt+Left / Alt+Right and the mouse's side buttons, going through the
 * history of viewed packages. Also tells the history when a selection has settled: when the package stayed
 * selected for {@link #SETTLE_DELAY_MS}.
 */
public class HistoryNavigation implements ChangeListener {
	public static final int SETTLE_DELAY_MS = 1000;
	/** {@link MouseEvent#getButton()} of the side buttons (XBUTTON1 and XBUTTON2 on Windows) */
	private static final int MOUSE_BACK = 4;
	private static final int MOUSE_FORWARD = 5;

	private final PackageHistoryViewModel vm;
	private final Consumer<String> showPackage;
	private final JButton backButton;
	private final JButton forwardButton;
	private final Timer settleTimer;

	/**
	 * @param showPackage selects a package by id and scrolls to it
	 */
	public HistoryNavigation(PackageHistoryViewModel vm, Consumer<String> showPackage) {
		this.vm = vm;
		this.showPackage = showPackage;
		backButton = QuakeInjectorView.iconButton("/arrow_back.svg", "Back (Alt+Left)");
		backButton.addActionListener(e -> back());
		forwardButton = QuakeInjectorView.iconButton("/arrow_forward.svg", "Forward (Alt+Right)");
		forwardButton.addActionListener(e -> forward());

		settleTimer = new Timer(SETTLE_DELAY_MS, e -> vm.settle());
		settleTimer.setRepeats(false);

		vm.addChangeListener(this);
		update();
	}

	public JButton backButton() {
		return backButton;
	}

	public JButton forwardButton() {
		return forwardButton;
	}

	/**
	 * Alt+Left / Alt+Right anywhere in the window, and the mouse's side buttons over it.
	 *
	 * @return the mouse listener; remove it from the toolkit when the window goes away
	 */
	public AWTEventListener install(JFrame frame) {
		JRootPane root = frame.getRootPane();
		InputMap keys = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "historyBack");
		keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), "historyForward");
		root.getActionMap().put("historyBack", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				back();
			}
		});
		root.getActionMap().put("historyForward", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				forward();
			}
		});

		// mouse events go to the component under the pointer, so listen for the whole window
		AWTEventListener mouse = event -> {
			if (event.getID() != MouseEvent.MOUSE_PRESSED || !(event instanceof MouseEvent e)) {
				return;
			}
			Window window = e.getComponent() instanceof Window w ? w : SwingUtilities.getWindowAncestor(e.getComponent());
			if (window != frame) {
				return;
			}
			if (e.getButton() == MOUSE_BACK) {
				back();
			}
			else if (e.getButton() == MOUSE_FORWARD) {
				forward();
			}
		};
		Toolkit.getDefaultToolkit().addAWTEventListener(mouse, AWTEvent.MOUSE_EVENT_MASK);
		return mouse;
	}

	private void back() {
		show(vm.back());
	}

	private void forward() {
		show(vm.forward());
	}

	private void show(String packageId) {
		if (packageId != null) {
			showPackage.accept(packageId);
		}
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		Edt.check("HistoryNavigation");
		update();
	}

	private void update() {
		backButton.setEnabled(vm.canGoBack());
		forwardButton.setEnabled(vm.canGoForward());
		if (vm.hasPending()) {
			settleTimer.restart();
		}
		else {
			settleTimer.stop();
		}
	}
}
