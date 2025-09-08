package de.haukerehfeld.quakeinjector.gui;

import de.haukerehfeld.quakeinjector.*;
import de.haukerehfeld.quakeinjector.guimodel.PackageListModel;
import de.haukerehfeld.quakeinjector.utils.Utils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QuakeInjectorView extends JFrame {
	/**
	 * Window title
	 */
	private static final String ICON_URL = "/Inject2_SIZE.png";
	private static final String ICON_SIZE_PLACEHOLDER = "SIZE";
	private static final int[] ICON_SIZES = { 16, 32, 48, 256 };

	private static final String applicationName = "Quake Injector";
	private static final int minWidth = 1024;
	private static final int minHeight = 768;

	private final PackageListModel maplist;
	private final Component packageInteractionPanelView;
	private final Component installQueuePanel;

	private final Menu menu;
	private JButton randomMapButton;
	private PackageTable packageTable;
	private final List<ActionListener> showEngineConfigListeners = new ArrayList<>();
	private PackageDetailPanelView packageDetailPanelView;

	public QuakeInjectorView(PackageListModel maplist, Component packageInteractionPanelView,
	                         Component installQueuePanel) {
		super(applicationName);

		this.maplist = maplist;
		this.packageInteractionPanelView = packageInteractionPanelView;
		this.installQueuePanel = installQueuePanel;

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		setLayout(new BoxLayout(getContentPane(),
				BoxLayout.PAGE_AXIS));

		setIconImages(createIconList(ICON_SIZES, ICON_URL, ICON_SIZE_PLACEHOLDER));

		menu = new Menu();
		setJMenuBar(menu);

		setMinimumSize(new Dimension(minWidth, minHeight));

		//config needed here
		addMainPane(getContentPane());

	}

	public Menu getMenu() {
		return menu;
	}

	public void selectRowInMainTable(int mapTableRowIdx) {
		packageTable.setRowSelectionInterval(mapTableRowIdx, mapTableRowIdx);
		packageTable.scrollRectToVisible(new Rectangle(packageTable.getCellRect(mapTableRowIdx, 0, true)));
	}

	public void addRandomMapButtonActionListener(ActionListener listener) {
		randomMapButton.addActionListener(listener);
	}

	private void addMainPane(Container panel) {
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new GridBagLayout());

		//create a table
		packageTable = new PackageTable(maplist);
		maplist.size(packageTable);

		{
			JPanel filterPanel = new JPanel();
			filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.LINE_AXIS));
			JLabel filterText = new JLabel("Filter: ", SwingConstants.TRAILING);
			filterPanel.add(filterText);
			filterPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

			final JButton clearFilter = new JButton("Clear");
			clearFilter.setEnabled(false);  // disabled until there's text in filter textfield

			final JTextField filter = new JTextField();
			filter.getDocument().addDocumentListener(
					new DocumentListener() {
						public void changedUpdate(DocumentEvent e) { filter(); }
						public void insertUpdate(DocumentEvent e) { filter(); }
						public void removeUpdate(DocumentEvent e) { filter(); }

						private void filter() {
							packageTable.getRowSorter().setRowFilter(maplist.filter(filter.getText()));

							// https://stackoverflow.com/questions/21522902/how-disable-button-when-nothing-in-textfield
							if (filter.getText().equals("")) {
								clearFilter.setEnabled(false);
							} else {
								clearFilter.setEnabled(true);
							}
						}
					});
			filterText.setLabelFor(filter);
			filterPanel.add(filter);

			mainPanel.add(filterPanel, new GridBagConstraints() {{
				anchor = LINE_START;
				fill = HORIZONTAL;
				weightx = 1;
				weighty = 0;
			}});

			// https://stackoverflow.com/questions/5328945/how-to-clear-the-jtextfield-by-clicking-jbutton
			clearFilter.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					filter.setText("");
				}
			});

			filterPanel.add(clearFilter, new GridBagConstraints() {{
				anchor = LINE_END;
			}});

			randomMapButton = new JButton("Install Random Map");


			filterPanel.add(randomMapButton, new GridBagConstraints() {{
				anchor = LINE_END;
			}});
		}

		//Create the scroll pane and add the table to it.
		JScrollPane scrollPane = new JScrollPane(packageTable);

		mainPanel.add(scrollPane, new GridBagConstraints() {{
			anchor = CENTER;
			fill = BOTH;
			gridx = 0;
			gridy = 1;
			gridwidth = 1;
			gridheight = 1;
			weightx = 1;
			weighty = 1;
		}});


		JPanel infoPanel = new JPanel(new GridBagLayout());

		packageDetailPanelView = new PackageDetailPanelView();

		infoPanel.add(packageDetailPanelView, new GridBagConstraints() {{
			anchor = PAGE_START;
			fill = BOTH;
			weightx = 1;
			weighty = 1;
		}});

		infoPanel.add(packageInteractionPanelView, new GridBagConstraints() {{
			gridy = 1;
			fill = BOTH;
			weightx = 1;
		}});

// 		JLabel queueLabel = new JLabel("Install Queue");
// 		infoPanel.add(queueLabel, new GridBagConstraints() {{
// 			anchor = PAGE_END;
// 			fill = BOTH;
// 			gridy = 2;
// 			weightx = 1;
// 		}});


		JScrollPane queueScroll = new JScrollPane(installQueuePanel);
		infoPanel.add(queueScroll, new GridBagConstraints() {{
			anchor = PAGE_END;
			fill = BOTH;
			gridy = 3;
			weightx = 1;
			weighty = 1;
		}});

		JSplitPane infoSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
				infoPanel,
				queueScroll);
		infoSplit.setOneTouchExpandable(true);
		infoSplit.setResizeWeight(1);
		infoSplit.setContinuousLayout(true);
		infoSplit.setDividerLocation(600);
		infoSplit.setMinimumSize(new Dimension(400, 600));



		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				mainPanel,
				infoSplit);
		splitPane.setOneTouchExpandable(true);
		splitPane.setResizeWeight(1);
		splitPane.setContinuousLayout(true);
		splitPane.setMinimumSize(new Dimension(450, 300));

		panel.add(splitPane);
	}


	public void display() {
		//pack();
		setVisible(true);
	}


	private static java.util.List<Image> createIconList(int[] iconSizes, String iconUrl, String sizeToken) {
		List<Image> icons = new ArrayList<Image>(iconSizes.length);
		for (int size: iconSizes) {
			String path = iconUrl.replace(sizeToken, Integer.toString(size));
			try {
				javax.swing.ImageIcon icon = Utils.createImageIcon(path, "Icon" + size);
				icons.add(icon.getImage());
			}
			catch (IOException e) {
				System.err.println("WARNING: Couldn't load icon file " + path);
			}
		}
		return icons;
	}

	/*

	make separate usecase for all this?
	 - should get its own vm
	 - should get the classic dialog provider
	 - and reference to the config
	 - is it its own feature?

	this should be the vm of the engine config dialog:
			                     getConfig().EnginePath,
		                             getConfig().EngineExecutable,
		                             getConfig().WorkingDirAtExecutable,
		                             getConfig().DownloadPath,
		                             getConfig().EngineCommandLine,
		                             getConfig().RogueInstalled,
		                             getConfig().HipnoticInstalled,
				             getConfig().uiTheme
	- this use case would then be also passed to other use cases so that they can execute it
	*/
	/**
	 * @return false if the user didn't open the config dialog
	 */
	public boolean enginePathNotSetDialogue() {
		String msg = "Quake directory is not set correctly.\n"
				+ "It needs to be set before trying to install (or play).";

		Object[] options = {"Open Engine Configuration",
				"Cancel"};
		int openEngineConfig =
				JOptionPane.showOptionDialog(null,
						msg,
						"Quake directory incorrect",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.ERROR_MESSAGE,
						null,
						options,
						options[0]);
		//button for engine config pressed
		if (openEngineConfig == 0) {
			//wait until maps are finished loading
			for (ActionListener listener : showEngineConfigListeners) {
				listener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "showEngineConfig"));
			}
			return true;
		}
		else {
			return false;
		}
	}

	public void addShowEngineConfigListener(ActionListener listener) {
		showEngineConfigListeners.add(listener);
	}

	public PackageDetailPanelView getPackageDetailPanelView() {
		return packageDetailPanelView;
	}

	public PackageTable getPackageTable() {
		return packageTable;
	}
}
