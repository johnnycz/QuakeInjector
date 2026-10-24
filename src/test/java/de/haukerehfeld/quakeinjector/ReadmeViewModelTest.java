package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.guimodel.ReadmeViewModel;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.ReadmeSource;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Switching between a package's text files, and reads that finish late or fail.
 */
public class ReadmeViewModelTest {
	/** Hands out reads the test completes by hand. */
	private static class ManualSource implements ReadmeSource {
		final Map<String, List<CompletableFuture<String>>> reads = new HashMap<>();

		@Override
		public List<String> textFiles(Package pkg) {
			return List.of();
		}

		@Override
		public CompletableFuture<String> read(String path) {
			CompletableFuture<String> read = new CompletableFuture<>();
			reads.computeIfAbsent(path, p -> new ArrayList<>()).add(read);
			return read;
		}

		void finish(String path, String text) {
			reads.get(path).get(reads.get(path).size() - 1).complete(text);
		}
	}

	private final ManualSource source = new ManualSource();
	private int changes = 0;

	private ReadmeViewModel vm(String... files) {
		ReadmeViewModel vm = new ReadmeViewModel(source, "pkg", "Title", List.of(files));
		vm.addChangeListener(e -> changes++);
		vm.start();
		return vm;
	}

	@Test
	public void showsTheFirstFileOnceRead() {
		ReadmeViewModel vm = vm("a.txt", "b.txt");
		assertEquals("a.txt", vm.getCurrentFile());
		assertEquals("Loading...", vm.getText());

		source.finish("a.txt", "A");

		assertEquals("A", vm.getText());
		assertEquals(2, changes);
	}

	@Test
	public void nextAndPreviousWrapAround() {
		ReadmeViewModel vm = vm("a.txt", "b.txt", "c.txt");

		vm.previous();
		assertEquals("c.txt", vm.getCurrentFile());
		assertEquals(2, vm.getCurrentIndex());
		vm.next();
		assertEquals("a.txt", vm.getCurrentFile());
		vm.next();
		assertEquals("b.txt", vm.getCurrentFile());
	}

	@Test
	public void aReadFinishingAfterSwitchingIsIgnored() {
		ReadmeViewModel vm = vm("a.txt", "b.txt");
		vm.next();
		source.finish("b.txt", "B");

		source.finish("a.txt", "A, too late");

		assertEquals("b.txt", vm.getCurrentFile());
		assertEquals("B", vm.getText());
	}

	@Test
	public void unreadableFileShowsAMessage() {
		ReadmeViewModel vm = vm("a.txt");

		source.reads.get("a.txt").get(0).completeExceptionally(new FileNotFoundException("a.txt"));

		assertEquals("File not found: a.txt", vm.getText());
	}

	@Test
	public void noFiles() {
		ReadmeViewModel vm = vm();

		assertEquals("This package has no text files.", vm.getText());
		assertEquals("", vm.getCurrentFile());
		assertEquals(-1, vm.getCurrentIndex());
		vm.next();
		assertEquals(-1, vm.getCurrentIndex());
	}
}
