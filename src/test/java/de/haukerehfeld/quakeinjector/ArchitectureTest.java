package de.haukerehfeld.quakeinjector;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

public class ArchitectureTest {

	@Test
	public void layeredArchitectureTest() {
		JavaClasses importedClasses = new ClassFileImporter().importPackages("de.haukerehfeld.quakeinjector");

		var rule = layeredArchitecture()
				.consideringOnlyDependenciesInLayers()
				.layer("gui").definedBy("de.haukerehfeld.quakeinjector.gui")
				.layer("main").definedBy("de.haukerehfeld.quakeinjector")
				.layer("guimodel").definedBy("de.haukerehfeld.quakeinjector.guimodel")
				.layer("model").definedBy("de.haukerehfeld.quakeinjector.model")

				.whereLayer("gui").mayOnlyAccessLayers("gui", "guimodel")
				.whereLayer("guimodel").mayOnlyAccessLayers("guimodel", "model")
				.whereLayer("model").mayOnlyAccessLayers("model")
		;
		rule.check(importedClasses);
	}
}
