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

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.*;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import de.haukerehfeld.quakeinjector.guimodel.Column;
import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.guimodel.PackageListViewModel;

public class PackageTableModel extends AbstractTableModel implements ChangeListener {
	private final PackageListViewModel vm;

	public PackageTableModel(PackageListViewModel vm, PackageInteractionViewModel packageInteractionViewModel) {
		this.vm = vm;
		vm.addChangeListener(this);
	}

	public void size(JTable table) {
		TableColumnModel m = table.getColumnModel();

		m.getColumn(Column.getColumnNumber(Column.NAME)).setPreferredWidth(80);
		m.getColumn(Column.getColumnNumber(Column.TITLE)).setPreferredWidth(150);
		m.getColumn(Column.getColumnNumber(Column.AUTHOR)).setPreferredWidth(100);
		m.getColumn(Column.getColumnNumber(Column.INSTALLED)).setResizable(false);
		m.getColumn(Column.getColumnNumber(Column.INSTALLED)).setMaxWidth(16);
		m.getColumn(Column.getColumnNumber(Column.INSTALLED)).setMinWidth(16);

		m.getColumn(Column.getColumnNumber(Column.SIZE)).setMinWidth(90);
		m.getColumn(Column.getColumnNumber(Column.SIZE)).setMaxWidth(90);
		m.getColumn(Column.getColumnNumber(Column.SIZE)).setResizable(true);

		m.getColumn(Column.getColumnNumber(Column.RELEASEDATE)).setMinWidth(75);
		m.getColumn(Column.getColumnNumber(Column.RELEASEDATE)).setPreferredWidth(80);
	}
	
    @Override
	public String getColumnName(int col) {
        return Column.getColumn(col).header;
    }

	@Override
    public int getRowCount() {
        return vm.size();
    }

	@Override
	public int getColumnCount() {
		return Column.count();
	}

	@Override
	public Class<? extends Object> getColumnClass(int c) {
		return Column.getColumn(c).getColumnClass();
    }

	@Override
    public Object getValueAt(int row, int col) {
        return vm.getColumnData(col, row);
    }

    @Override
	public boolean isCellEditable(int row, int col) {
		return false;
    }

	/**
	 * Update the row filter regular expression from the expression in
	 * the text box.
	 */
	public RowFilter<PackageTableModel, Integer> filter(final String filterText) {
		final int[] columnsToCheck = { Column.getColumnNumber(Column.NAME),
									   Column.getColumnNumber(Column.AUTHOR),
		                               Column.getColumnNumber(Column.TITLE),
									   Column.getColumnNumber(Column.RELEASEDATE)
		};

		String[] filterTexts = filterText.split(" ");

		final List<Pattern> patterns = new ArrayList<Pattern>(filterTexts.length);
		for (String filter: filterTexts) {
			try {
				patterns.add(Pattern.compile(".*" + filter + ".*",
											 Pattern.CASE_INSENSITIVE + Pattern.UNICODE_CASE));
			} catch (java.util.regex.PatternSyntaxException e) {
				continue;
			}
		}

		RowFilter<PackageTableModel, Integer> rf = new RowFilter<PackageTableModel,Integer>() {
			@Override
			public boolean include(Entry<? extends PackageTableModel, ? extends Integer> entry) {
				//match all patters in at least one column
				for (Pattern pattern: patterns) {
					boolean matches = false;
					for (int i: columnsToCheck) {
						if (pattern.matcher(entry.getStringValue(i)).matches()) {
							matches = true;
						}
					}
					if (!matches) {
						return false;
					}
				}
				return true;
			}
		};

		return rf;
	}

	public void selectPackageByRowIndex(int modelRowIndex) {
		vm.selectPackageByRowIndex(modelRowIndex);
	}

	/** @return the model row of the selected package, or -1 */
	public int selectedRowIndex() {
		return vm.selectedRowIndex();
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		Edt.check("PackageTableModel");
		if (e.getSource() instanceof Integer rowIndex) {
			if (rowIndex >= 0) {
				fireTableRowsUpdated(rowIndex, rowIndex);
			} else {
				fireTableChanged(new TableModelEvent(this));
			}
		}
	}

	public static class BarRenderer extends JProgressBar implements TableCellRenderer {
		private final Color[] colors = new Color[101];

		public BarRenderer() {
			super(0, 100); // Min and max values for the bar
			setStringPainted(true);
			setOpaque(true);
			initColors();
		}

		private void initColors() {
			for (int i = 0; i <= 100; i++) {
				int r = (int) (200 * ((100.0 - i) / 100.0));
				int g = (int) (200 * (1.0 * i / 100.0));
				colors[i] = new Color(r, g, 0);
			}
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
													   boolean isSelected, boolean hasFocus,
													   int row, int column) {
			if (value instanceof Float) {
				float rating = (Float) value;
				rating = Math.max(0, rating);
				rating = Math.min(5, rating);
				int percentage = (int) (100 * (rating / 5.0));
				setValue(percentage);
				setForeground(colors[percentage]);
				setString(String.format("%.1f", rating));
			}
			return this;
		}
	}


//     /*
//      * Don't need to implement this method unless your table's
//      * data can change.
//      */
//     public void setValueAt(Object value, int row, int col) {
//         data[row][col] = value;
//         fireTableCellUpdated(row, col);
//     }{

}
