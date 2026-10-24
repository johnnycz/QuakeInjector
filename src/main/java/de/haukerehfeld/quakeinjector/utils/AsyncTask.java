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
 * Represents an asynchronous operation that produces a result of type T.
 * Implementations must not depend on any UI framework (Swing, AWT, etc).
 * The execute() method is expected to be called on a background thread.
 *
 * @param <T> the type of result produced by this task
 */
public interface AsyncTask<T> {
	/**
	 * Execute the task and return the result.
	 * This method may throw checked exceptions; the caller reports them.
	 *
	 * @return the result of the operation
	 * @throws Exception if the operation fails
	 */
	T execute() throws Exception;
}
