package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.JobTracker;
import de.haukerehfeld.quakeinjector.model.JobTrackerProvider;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class InstallQueueViewModel implements JobTrackerProvider {

	private final ChangeListenerList listeners = new ChangeListenerList();
	public void addChangeListener(ChangeListener l) {
		listeners.addChangeListener(l);
	}

	public void removeChangeListener(ChangeListener l) {
		listeners.removeChangeListener(l);
	}

	private void notifyChangeListeners(JobVm job) {
		listeners.notifyChangeListeners(new ChangeEvent(job));
	}

	public List<JobVm> jobs = new ArrayList<>();

	private AtomicInteger jobCounter = new AtomicInteger(0);

	public class JobVm implements PropertyChangeListener, ActionListener {
		private final int id;
		private final String description;
		private final ActionListener cancelAction;
		private boolean finished = false;
		private int progress = 0;
		private String finishedMessage;

		public JobVm(int id, ActionListener cancelAction, String description) {
			this.id = id;
			this.description = description;
			this.cancelAction = cancelAction;
		}

		public void propertyChange(PropertyChangeEvent evt) {
			if ("progress".equals(evt.getPropertyName())) {
				int p = (Integer) evt.getNewValue();
				setProgress(p);
			}
		}

		public void setProgress(int progress) {
			this.progress = progress;
			notifyChangeListeners(this);
		}

		public void finish(String message) {
			this.finished = true;
			this.finishedMessage = message;
			notifyChangeListeners(this);
		}

		public boolean isFinished() {
			return finished;
		}

		public String getDescription() {
			return description;
		}

		public int getId() {
			return id;
		}

		public int getProgress() {
			return progress;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			cancelAction.actionPerformed(e);
		}

		public String getFinishedMessage() {
			return finishedMessage;
		}
	}

	private class JobTrackerImpl implements JobTracker {
		private final int jobId;

		private JobTrackerImpl(int jobId) {
			if (jobId < 0 || jobId >= jobs.size()) {
				throw new IllegalArgumentException("Invalid jobId: " + jobId);
			}
			this.jobId = jobId;
		}

		@Override
		public void finish(String message) {
			jobs.get(jobId).finish(message);
		}

		@Override
		public void setProgress(int progress) {
			jobs.get(jobId).setProgress(progress);
		}

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			jobs.get(jobId).propertyChange(evt);
		}
	}

	public synchronized JobTracker addJob(String description, ActionListener cancelAction) {
		int jobId = jobCounter.getAndIncrement();
		if (jobs.size() != jobId) {
			throw new IllegalStateException("Job list size and job counter out of sync");
		}
		JobVm job = new JobVm(jobId, cancelAction, description);
		jobs.add(job);
		notifyChangeListeners(job);
		return new JobTrackerImpl(jobId);
	}
}
