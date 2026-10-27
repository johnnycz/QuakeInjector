package de.haukerehfeld.quakeinjector.model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * How missing prerequisites are listed in messages: an official expansion by its full name and where it
 * was looked for, anything else by its id.
 */
public final class MissingPrerequisites {
	private MissingPrerequisites() {}

	/** One prerequisite per line, each starting with "- ". */
	public static String describe(List<Requirement> missing) {
		return missing.stream().map(r -> "- " + describe(r)).collect(Collectors.joining("\n"));
	}

	public static String describe(Requirement missing) {
		if (missing instanceof OfficialExpansionRequirement expansion) {
			return expansion.getName() + ": official expansion, not found in your Quake directory";
		}
		return missing.getId();
	}
}
