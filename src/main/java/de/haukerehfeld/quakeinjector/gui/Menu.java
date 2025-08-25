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

//import java.awt.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.*;

public class Menu extends JMenuBar {
	private final JMenuItem reparseDatabase;
	private final JCheckBoxMenuItem enableOfflineMode;
	private final JMenuItem checkInstalled;
	private final JMenuItem engine;
	private final JMenuItem quit;

	public Menu() {
		setOpaque(true);
		// 		setPreferredSize(new Dimension(200, 20));

		JMenu fileMenu = new JMenu("File");
		add(fileMenu);

		reparseDatabase = new JMenuItem("Reload database", KeyEvent.VK_R);
		fileMenu.add(reparseDatabase);

		checkInstalled = new JMenuItem("Check for installed maps", KeyEvent.VK_C);
		fileMenu.add(checkInstalled);

		enableOfflineMode = new JCheckBoxMenuItem("Offline Mode");
		fileMenu.add(enableOfflineMode);

		engine = new JMenuItem("Settings...");
		fileMenu.add(engine);

		quit = new JMenuItem("Quit", KeyEvent.VK_T);
		quit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, ActionEvent.ALT_MASK));
		quit.getAccessibleContext().setAccessibleDescription("This doesn't really do anything");
		fileMenu.add(quit);
	}

	public void addReparseDatabaseActionListener(ActionListener actionListener) {
		reparseDatabase.addActionListener(actionListener);
	}

	public void addCheckInstalledActionListener(ActionListener actionListener) {
			checkInstalled.addActionListener(actionListener);
	}

	public void addEnableOfflineModeActionListener(ActionListener actionListener) {
			enableOfflineMode.addActionListener(actionListener);
	}

	public void addEngineActionListener(ActionListener actionListener) {
			engine.addActionListener(actionListener);
	}

	public void addQuitActionListener(ActionListener actionListener) {
			quit.addActionListener(actionListener);
	}

	public void setOfflineMode(boolean offline) {
				enableOfflineMode.setSelected(offline);
		}
}