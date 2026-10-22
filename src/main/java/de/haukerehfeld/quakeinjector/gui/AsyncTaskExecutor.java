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

import java.beans.PropertyChangeListener;

/**
 * Factory and helper for executing AsyncTasks on the Swing event dispatch thread.
 * Provides fluent API for constructing and executing tasks with optional progress tracking.
 *
 * Usage examples:
 * {@code
 * // Simple task without progress
 * AsyncTaskExecutor.execute(task, listener);
 *
 * // Task with progress tracking
 * AsyncTaskExecutor.executeWithProgress(task, listener)
 *     .addProgressListener((current, total) -> updateUI(current, total))
 *     .execute();
 *
 * // Manual construction with progress listener
 * AsyncProgressListener progress = AsyncTaskExecutor.createProgressListener();
 * AsyncTask<Result> task = new MyTask(progress);
 * AsyncTaskExecutor.execute(task, listener);
 * }
 */
public class AsyncTaskExecutor {
	private AsyncTaskExecutor() {
		// Utility class, not instantiable
	}

	/**
	 * Execute a simple task without progress tracking.
	 *
	 * @param task the task to execute
	 * @param listener callback for results
	 * @param <T> the result type
	 */
	public static <T> void execute(AsyncTask<T> task, AsyncListener<T> listener) {
		new SwingWorkerAdapter<>(task, listener).execute();
	}

	/**
	 * Create a progress listener for use with AsyncTask implementations.
	 * This is useful when you need to pass a progress listener to the task constructor.
	 *
	 * @return a progress listener (initially dormant until connected to an adapter)
	 */
	public static AsyncProgressListener createProgressListener() {
		return (current, total) -> {
			// Dormant listener - does nothing until wrapped by an adapter
		};
	}

	/**
	 * Build an executor with progress tracking support.
	 * The returned builder allows you to add progress listeners before execution.
	 *
	 * @param task the task to execute
	 * @param listener callback for results
	 * @param <T> the result type
	 * @return a builder for fluent configuration
	 */
	public static <T> ProgressBuilder<T> executeWithProgress(AsyncTask<T> task, AsyncListener<T> listener) {
		return new ProgressBuilder<>(task, listener);
	}

	/**
	 * Fluent builder for async task execution with progress tracking.
	 *
	 * @param <T> the result type
	 */
	public static class ProgressBuilder<T> {
		private final AsyncTask<T> task;
		private final AsyncListener<T> listener;
		private PropertyChangeListener progressListener;

		ProgressBuilder(AsyncTask<T> task, AsyncListener<T> listener) {
			this.task = task;
			this.listener = listener;
		}

		/**
		 * Add a PropertyChangeListener that receives progress updates.
		 * The listener will receive "progress" property changes with Integer values (0-100).
		 *
		 * @param listener the property change listener
		 * @return this builder for chaining
		 */
		public ProgressBuilder<T> addProgressListener(PropertyChangeListener listener) {
			this.progressListener = listener;
			return this;
		}

		/**
		 * Execute the task with the configured progress tracking.
		 */
		public void execute() {
			SwingWorkerProgressAdapter<T> adapter = new SwingWorkerProgressAdapter<>(task, listener);
			if (progressListener != null) {
				adapter.addPropertyChangeListener(progressListener);
			}
			adapter.execute();
		}
	}
}
