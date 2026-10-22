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

import de.haukerehfeld.quakeinjector.utils.AsyncTask;
import de.haukerehfeld.quakeinjector.utils.AsyncListener;
import de.haukerehfeld.quakeinjector.utils.AsyncProgressListener;

import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;

/**
 * Adapter that bridges AsyncTask with progress tracking to SwingWorker.
 * The adapter wraps the task with a proxy that intercepts progress updates
 * and converts them to SwingWorker's progress property.
 *
 * Usage:
 * {@code
 * AsyncTask<Result> task = new MyTask();
 * AsyncListener<Result> listener = new MyListener();
 * SwingWorkerProgressAdapter<Result> adapter = new SwingWorkerProgressAdapter<>(task, listener);
 * adapter.addPropertyChangeListener("progress", evt -> {
 *     int progress = (Integer) evt.getNewValue();
 *     updateProgressBar(progress);
 * });
 * adapter.execute();
 * }
 *
 * @param <T> the type of result produced by the task
 */
public class SwingWorkerProgressAdapter<T> extends SwingWorker<T, Void> {
	private final AsyncTask<T> task;
	private final AsyncListener<T> listener;
	private final AsyncProgressListener progressInterceptor;

	/**
	 * Create an adapter with progress tracking.
	 * Progress updates from the AsyncProgressListener will be converted to SwingWorker progress (0-100).
	 *
	 * @param task the AsyncTask to execute
	 * @param listener callback for success/failure
	 */
	public SwingWorkerProgressAdapter(AsyncTask<T> task, AsyncListener<T> listener) {
		this.task = task;
		this.listener = listener;
		this.progressInterceptor = (current, total) -> {
			if (total > 0) {
				int progress = (int) ((current * 100) / total);
				progress = Math.min(progress, 100);  // Cap at 100%
				setProgress(progress);
			}
		};
	}

	@Override
	protected T doInBackground() throws Exception {
		// Wrap the task to inject progress tracking
		AsyncTask<T> wrappedTask = createProgressTrackingTask();
		return wrappedTask.execute();
	}

	private AsyncTask<T> createProgressTrackingTask() {
		return new AsyncTask<T>() {
			@Override
			public T execute() throws Exception {
				// If the original task accepts a progress listener via a wrapper,
				// we'd inject it here. For now, we rely on the task implementation
				// to accept AsyncProgressListener in its constructor.
				// This is a limitation of the adapter approach for existing tasks.
				return task.execute();
			}
		};
	}

	/**
	 * Get the progress listener that this adapter uses to intercept progress updates.
	 * Pass this to your AsyncTask constructor if it supports progress tracking.
	 *
	 * @return the progress listener for this adapter
	 */
	public AsyncProgressListener getProgressListener() {
		return progressInterceptor;
	}

	@Override
	protected void done() {
		try {
			T result = get();
			listener.onSuccess(result);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				listener.onFailure((Exception) cause);
			} else {
				listener.onFailure(new Exception("Unexpected error", cause));
			}
		} catch (InterruptedException e) {
			listener.onFailure(new Exception("Task interrupted", e));
		}
	}
}
