package de.haukerehfeld.quakeinjector;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

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
		var basePackage = "de.haukerehfeld.quakeinjector";
		JavaClasses importedClasses = new ClassFileImporter().importPackages(basePackage, "java.awt", "javax.swing");

		DescribedPredicate<JavaClass> swingWithoutUtils = DescribedPredicate.describe("Swing without utils", new SwingGuiClassPredicate().and(new SwingUtilPredicate().negate()));
		var rule = layeredArchitecture()
				.consideringOnlyDependenciesInLayers()
				.ensureAllClassesAreContainedInArchitecture()
				.layer("main").definedBy(basePackage)
				.layer("gui").definedBy("..gui..")
				.layer("guimodel").definedBy("..guimodel..")
				.layer("model").definedBy("..model..")
				.layer("feature.install").definedBy(basePackage + ".feature.install..")
				.layer("feature.list").definedBy(basePackage + ".feature.list..")
				.layer("feature.play").definedBy(basePackage + ".feature.play..")
				.layer("utils").definedBy(basePackage + ".utils..")
				.layer("swing").definedBy(swingWithoutUtils)
				.layer("swingutils").definedBy(new SwingUtilPredicate())

				.whereLayer("gui").mayOnlyAccessLayers("guimodel", "utils", "swing", "swingutils")
				.whereLayer("guimodel").mayOnlyAccessLayers("model", "utils", "swingutils")
				.whereLayer("model").mayOnlyAccessLayers("utils", "swingutils")
				.whereLayer("feature.play").mayOnlyAccessLayers("model", "utils", "swingutils")
				.whereLayer("feature.install").mayOnlyAccessLayers("model", "utils", "swingutils")
				.whereLayer("feature.list").mayOnlyAccessLayers("model", "utils", "swingutils")
				.whereLayer("utils").mayOnlyAccessLayers("swingutils")
		;


		rule.check(importedClasses);
	}

	private static class SwingUtilPredicate extends DescribedPredicate<JavaClass> {
		public SwingUtilPredicate() {
			super("Utility Swing classes like SwingWorker" );
		}
		@Override
		public boolean test(JavaClass javaClass) {
			String fullName = javaClass.getFullName();
			return
					fullName.startsWith("javax.swing.SwingWorker")
							|| fullName.equals("javax.swing.event.ChangeListener")
							|| fullName.equals("javax.swing.event.ChangeEvent")
							|| fullName.equals("java.awt.event.ActionEvent")
							|| fullName.equals("java.awt.event.ActionListener")
					;
		}

		@Override
		public Predicate<JavaClass> negate() {
			return super.negate();
		}
	}

	private static class SwingGuiClassPredicate extends DescribedPredicate<JavaClass> {
		public SwingGuiClassPredicate() {
			super("Swing and AWT classes");
		}

		@Override
		public boolean test(JavaClass javaClass) {
			String fullName = javaClass.getFullName();
			return
					   fullName.startsWith("javax.swing")
					|| fullName.startsWith("java.awt")
			;
		}

		@Override
		public Predicate<JavaClass> negate() {
			return super.negate();
		}
	}
}
