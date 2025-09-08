package de.haukerehfeld.quakeinjector;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

public class ArchitectureTest {

	/**
	 * <pre>
	 * +------+     +-----------+     +-------+
	 * | gui  |---->| guimodel  |---->| model |
	 * +------+     +-----------+     +-------+
	 * </pre>
	 */
	@Test
	public void layeredArchitectureTest() {
		JavaClasses importedClasses = new ClassFileImporter().importPackages("de.haukerehfeld.quakeinjector");

		var basePackage = "de.haukerehfeld.quakeinjector";
		var rule = layeredArchitecture()
				.consideringOnlyDependenciesInLayers()
				.ensureAllClassesAreContainedInArchitecture()
				.layer("gui").definedBy(basePackage + ".gui")
				.layer("main").definedBy(basePackage)
				.layer("guimodel").definedBy(basePackage + ".guimodel")
				.layer("model").definedBy(basePackage + ".model")
				.layer("feature.install").definedBy(basePackage + ".feature.install")
				.layer("feature.list").definedBy(basePackage + ".feature.list")
				.layer("feature.play").definedBy(basePackage + ".feature.play")
				.layer("utils").definedBy(basePackage + ".utils")
				// TODO .layer("swing").definedBy("javax.swing..")

				.whereLayer("gui").mayOnlyAccessLayers("guimodel", "utils")
				.whereLayer("guimodel").mayOnlyAccessLayers("model", "utils")
				.whereLayer("model").mayOnlyAccessLayers("utils")
				.whereLayer("feature.play").mayOnlyAccessLayers("model", "utils")
				.whereLayer("feature.install").mayOnlyAccessLayers("model", "utils")
				.whereLayer("feature.list").mayOnlyAccessLayers("model", "utils")
				.whereLayer("utils").mayNotAccessAnyLayer()
		;
		rule.check(importedClasses);
	}
}
