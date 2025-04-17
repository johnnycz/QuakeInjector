package de.haukerehfeld.quakeinjector;

import java.io.InputStream;
import java.util.List;

public interface PackageDatabaseParser {
    final static Package.Rating[] ratingTable = {
            Package.Rating.Unrated,
            Package.Rating.Crap,
            Package.Rating.Poor,
            Package.Rating.Average,
            Package.Rating.Nice,
            Package.Rating.Excellent
    };

    List<Requirement> parse(InputStream is);
}
