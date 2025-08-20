package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.Requirement;

import java.io.InputStream;
import java.util.List;

public interface PackageDatabaseParser {
    List<Requirement> parse(InputStream is);
}
