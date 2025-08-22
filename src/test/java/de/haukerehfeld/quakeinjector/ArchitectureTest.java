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
				.ensureAllClassesAreContainedInArchitecture()
				.layer("gui").definedBy("de.haukerehfeld.quakeinjector.gui")
				.layer("main").definedBy("de.haukerehfeld.quakeinjector")
				.layer("guimodel").definedBy("de.haukerehfeld.quakeinjector.guimodel")
				.layer("model").definedBy("de.haukerehfeld.quakeinjector.model")
				.layer("feature.install").definedBy("de.haukerehfeld.quakeinjector.feature.install")
				.layer("feature.list").definedBy("de.haukerehfeld.quakeinjector.feature.list")
				.layer("feature.play").definedBy("de.haukerehfeld.quakeinjector.feature.play")
				.layer("utils").definedBy("de.haukerehfeld.quakeinjector.utils")

				.whereLayer("gui").mayOnlyAccessLayers("guimodel", "utils")
				.whereLayer("guimodel").mayOnlyAccessLayers("model", "utils")
				.whereLayer("model").mayOnlyAccessLayers("utils")
				.whereLayer("feature.play").mayOnlyAccessLayers("main", "model", "gui", "guimodel", "utils")
				.whereLayer("feature.install").mayOnlyAccessLayers("main", "model", "gui", "guimodel", "utils")
				.whereLayer("feature.list").mayOnlyAccessLayers("main", "model", "gui", "guimodel", "utils")
				.whereLayer("utils").mayNotAccessAnyLayer()
		;
		rule.check(importedClasses);
	}
}
