package de.haukerehfeld.quakeinjector.feature.list;

import de.haukerehfeld.quakeinjector.model.Requirement;

import java.util.List;

/**
 * The parsed package database.
 *
 * @param packages  all packages and requirements in the database
 * @param fromCache true if downloading failed and the previously downloaded copy was used
 */
public record LoadedPackageDatabase(List<Requirement> packages, boolean fromCache) {
}
