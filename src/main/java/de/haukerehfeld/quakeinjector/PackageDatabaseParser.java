package de.haukerehfeld.quakeinjector;

import java.io.InputStream;
import java.util.List;

public interface PackageDatabaseParser {
    List<Requirement> parse(InputStream is);
}
