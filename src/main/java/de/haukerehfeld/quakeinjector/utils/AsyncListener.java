/*


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

package de.haukerehfeld.quakeinjector.utils;

/**
 * Listener for completion and failure events of an AsyncTask.
 * Implementations should be prepared to receive callbacks on any thread.
 * If UI updates are needed, the implementation must handle thread marshalling
 * (e.g., posting to the Event Dispatch Thread in Swing applications).
 *
 * @param <T> the type of result produced by the associated task
 */
public interface AsyncListener<T> {
	/**
	 * Called when the task completes successfully.
	 *
	 * @param result the result of the task execution
	 */
	void onSuccess(T result);

	/**
	 * Called when the task fails.
	 *
	 * @param exception the exception that caused the failure
	 */
	void onFailure(Exception exception);
}
