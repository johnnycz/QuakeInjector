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

		var basePackage = "de.haukerehfeld.quakeinjector";
		var rule = layeredArchitecture()
				.consideringOnlyDependenciesInLayers()
				.ensureAllClassesAreContainedInArchitecture()
				.layer("gui").definedBy(basePackage + ".gui")
				.layer("main").definedBy(basePackage + "")
				.layer("guimodel").definedBy(basePackage + ".guimodel")
				.layer("model").definedBy(basePackage + ".model")
				.layer("feature.install").definedBy(basePackage + ".feature.install")
				.layer("feature.list").definedBy(basePackage + ".feature.list")
				.layer("feature.play").definedBy(basePackage + ".feature.play")
				.layer("utils").definedBy(basePackage + ".utils")

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
