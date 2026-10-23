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
import de.haukerehfeld.quakeinjector.utils.CancelledException;

import javax.swing.SwingWorker;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * Adapter that bridges AsyncTask to SwingWorker for EDT-aware execution.
 * This adapter handles the threading complexity so that business logic (AsyncTask)
 * remains decoupled from Swing.
 *
 * Usage:
 * {@code
 * AsyncTask<Result> task = new MyTask();
 * AsyncListener<Result> listener = new MyListener();
 * new SwingWorkerAdapter<>(task, listener).execute();
 * }
 *
 * @param <T> the type of result produced by the task
 */
public class SwingWorkerAdapter<T> extends SwingWorker<T, Void> {
	private final AsyncTask<T> task;
	private final AsyncListener<T> listener;

	/**
	 * Create an adapter without progress tracking.
	 *
	 * @param task the AsyncTask to execute
	 * @param listener callback for success/failure
	 */
	public SwingWorkerAdapter(AsyncTask<T> task, AsyncListener<T> listener) {
		this.task = task;
		this.listener = listener;
	}

	@Override
	protected T doInBackground() throws Exception {
		return task.execute();
	}

	@Override
	protected void done() {
		try {
			T result = get();
			listener.onSuccess(result);
		} catch (CancellationException e) {
			// cancel() was called; still tell the listener, so it can e.g. close a progress popup
			listener.onFailure(new CancelledException());
		} catch (ExecutionException e) {
			// Unwrap the exception thrown by task.execute()
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
