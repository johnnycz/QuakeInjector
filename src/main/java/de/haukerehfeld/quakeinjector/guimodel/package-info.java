/**
 * View models: the state and actions the Swing views in {@code gui} show and trigger.
 *
 * <h2>Threading</h2>
 * Everything in this package runs on the Swing event dispatch thread (EDT):
 * <ul>
 * <li>Call view model methods only on the EDT.</li>
 * <li>View models notify their views (change listeners) on the EDT, synchronously.</li>
 * <li>Background work reports back through the UI executor it was given
 *     ({@code SwingUtilities::invokeLater}), e.g. {@code InstallListener} or
 *     {@code Installer.UninstallErrorHandler}, so its callbacks already arrive on the EDT.</li>
 * </ul>
 * Views check this with {@code gui.Edt.check}, which logs a violation once per view.
 */
package de.haukerehfeld.quakeinjector.guimodel;
