package de.haukerehfeld.quakeinjector.guimodel;

import de.haukerehfeld.quakeinjector.model.JobTracker;
import de.haukerehfeld.quakeinjector.model.JobTrackerProvider;
import de.haukerehfeld.quakeinjector.utils.ChangeListenerList;

import de.haukerehfeld.quakeinjector.utils.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
		listeners.notifyChangeListeners(job);
	}

	public List<JobVm> jobs = new ArrayList<>();

	private AtomicInteger jobCounter = new AtomicInteger(0);

	public class JobVm {
		private final int id;
		private final String description;
		private final Runnable cancelAction;
		private boolean finished = false;
		private int progress = 0;
		private String finishedMessage;

		public JobVm(int id, Runnable cancelAction, String description) {
			this.id = id;
			this.description = description;
			this.cancelAction = cancelAction;
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

		public void cancel() {
			cancelAction.run();
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
		public void start() {
		}

		@Override
		public void finish(String message) {
			jobs.get(jobId).finish(message);
		}

		@Override
		public void setProgress(int progress) {
			jobs.get(jobId).setProgress(progress);
		}
	}

	public synchronized JobTracker addJob(String description, Runnable cancelAction) {
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
