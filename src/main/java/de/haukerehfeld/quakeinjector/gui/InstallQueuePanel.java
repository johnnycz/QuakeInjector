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

import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel;
import de.haukerehfeld.quakeinjector.guimodel.InstallQueueViewModel.JobVm;

import java.awt.*;
import java.util.*;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Scrollable;
import de.haukerehfeld.quakeinjector.utils.ChangeEvent;
import de.haukerehfeld.quakeinjector.utils.ChangeListener;


public class InstallQueuePanel extends JPanel implements Scrollable, ChangeListener {
	/** How many rows the queue shows before it scrolls, unless the user drags the divider above it. */
	private final static int VISIBLE_ROWS = 4;
	private final static int MARGIN = 3;
	private final InstallQueueViewModel vm;
	/** The height of one row: a Cancel button and its margins. */
	private final int rowHeight = new JButton("Cancel").getPreferredSize().height + 2 * MARGIN;

	private GridBagLayout layout = new GridBagLayout();
	
	private Map<Integer, JobUi> jobs = new HashMap<>();
	
	public InstallQueuePanel(InstallQueueViewModel vm) {
		this.vm = vm;
		vm.addChangeListener(this);

		setLayout(layout);
	}

	private void updateJob(JobVm jobVm) {
		if (!jobs.containsKey(jobVm.getId())) {
			var jobUi = new JobUi(jobVm);
			jobs.put(jobVm.getId(), jobUi);
		}
		JobUi jobUi = jobs.get(jobVm.getId());
		jobUi.update();

		revalidate();
		repaint();

		scrollRectToVisible(new Rectangle(0, getHeight(), 0, 100));
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return new Dimension(getPreferredSize().width, VISIBLE_ROWS * rowHeight);
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect,
	                                      int orientation,
	                                      int direction) {
		return rowHeight;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect,
	                                       int orientation,
	                                       int direction) {
		return rowHeight;
	}

	@Override
	public boolean getScrollableTracksViewportWidth() { return true; }
	@Override
	public boolean getScrollableTracksViewportHeight() { return false; }

	@Override
	public void stateChanged(ChangeEvent e) {
		Edt.check("InstallQueuePanel");
		Object sourceObject = e.getSource();
		if (sourceObject instanceof JobVm job) {
			updateJob(job);
		}
	}

	/** The text of a row, e.g. "Installing jam1: 42%". */
	private static String progressString(String description, String status) {
		return description + ": " + status;
	}

	private static class RowConstraints extends GridBagConstraints {{
		anchor = CENTER;
		fill = HORIZONTAL;
		insets = new java.awt.Insets(MARGIN, MARGIN, MARGIN, MARGIN);
	}}

	private static class ProgressBarConstraints extends RowConstraints {{
		weightx = 1;
		weighty = 1;
	}}

	private static class CancelButtonConstraints extends RowConstraints {{
		gridx = 1;
	}}

	private static class FinishedLabelConstraints extends RowConstraints {{
		weightx = 1;
		gridwidth = 2;
	}}

	private class JobUi {
		private final JobVm jobVm;
		private JProgressBar progressBar;
		private JButton cancelButton;
		private JLabel finishedLabel;
		private boolean finished = false;

		public JobUi(JobVm jobVm) {
			this.jobVm = jobVm;

			progressBar = new JProgressBar();
			progressBar.setStringPainted(true);
			
			cancelButton = new JButton("Cancel");
			cancelButton.addActionListener((e) -> jobVm.cancel());

			finishedLabel = PlainText.disableHtml(new JLabel());

			InstallQueuePanel.this.add(progressBar, new ProgressBarConstraints() {{ gridy = jobVm.getId(); }});
			InstallQueuePanel.this.add(cancelButton, new CancelButtonConstraints() {{ gridy = jobVm.getId(); }});

			update();
		}

		public void update() {
			if (!jobVm.isFinished()) {
				if (jobVm.isProgressUnknown()) {
					progressBar.setIndeterminate(true);
					progressBar.setString(jobVm.getDescription());
				}
				else {
					progressBar.setIndeterminate(false);
					progressBar.setString(progressString(jobVm.getDescription(), jobVm.getProgress() + "%"));
					progressBar.setValue(jobVm.getProgress());
				}
			}
			else if (jobVm.isFinished() && !finished){
				finished = true;
				InstallQueuePanel.this.remove(progressBar);
				InstallQueuePanel.this.remove(cancelButton);

				InstallQueuePanel.this.add(finishedLabel, new FinishedLabelConstraints() {{ gridy = jobVm.getId(); }});
				finishedLabel.setText(progressString(jobVm.getDescription(), jobVm.getFinishedMessage()));
				finishedLabel.setPreferredSize(new Dimension((int) finishedLabel
						.getPreferredSize().getWidth(),
						(int) cancelButton.getSize().getHeight()));
			}

		}
	}
}