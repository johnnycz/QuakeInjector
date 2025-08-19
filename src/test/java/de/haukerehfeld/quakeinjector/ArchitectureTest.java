package de.haukerehfeld.quakeinjector;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

public class ArchitectureTest {

	@Test
	public void testArchitecture() {
		JavaClasses importedClasses = new ClassFileImporter().importPackages("de.haukerehfeld.quakeinjector");

		ArchRule r1 = layeredArchitecture()
				.consideringAllDependencies()
				.layer("gui").definedBy("..gui..")
				.layer("main").definedBy("de.haukerehfeld.quakeinjector");

		ArchRule rule = classes().that().resideInAPackage("..gui..")
						.should().onlyDependOnClassesThat().resideInAnyPackage(
						"..gui..", "..guimodel..", "de.haukerehfeld.quakeinjector.utils",
						"java..", "javax..",
						"com.github.weisj.darklaf..", "edu.stanford.ejalbert..")
				;


		rule.check(importedClasses);
	}
}
