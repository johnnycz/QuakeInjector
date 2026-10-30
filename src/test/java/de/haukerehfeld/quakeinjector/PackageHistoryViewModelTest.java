package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.guimodel.PackageHistoryViewModel;
import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageList;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The history of viewed packages behind Back and Forward: what is recorded, browser-like Back/Forward,
 * the Forward entries dropped by a new package, and packages missing from the list.
 */
public class PackageHistoryViewModelTest {
	private final RequirementList requirements = new RequirementList();
	private final PackageList packages = new PackageList(requirements);
	private final PackageInteractionViewModel selection = new PackageInteractionViewModel(null, null);
	private PackageHistoryViewModel vm;

	private static Package pkg(String id) {
		return new Package(id, "sha", id + ".zip", List.of("file:///" + id + ".zip"), "author", id,
			0, null, false, 0f, "description", new ExtractMapping(), "", List.of(), List.of());
	}

	/** Replace the package list, as loading the package database does. */
	private void load(String... ids) {
		List<Requirement> list = new ArrayList<>();
		for (String id : ids) {
			list.add(pkg(id));
		}
		requirements.setRequirements(list);
		requirements.notifyChangeListeners();
	}

	@BeforeEach
	public void setUp() {
		load("a", "b", "c", "d");
		vm = new PackageHistoryViewModel(selection, packages);
	}

	private void select(String id) {
		selection.setSelectedPackage(packages.get(id));
	}

	/** Select and let the selection settle, as staying on a package does. */
	private void view(String... ids) {
		for (String id : ids) {
			select(id);
			vm.settle();
		}
	}

	/** Go back and show the package, as the view does. */
	private String back() {
		String id = vm.back();
		if (id != null) {
			select(id);
		}
		return id;
	}

	private String forward() {
		String id = vm.forward();
		if (id != null) {
			select(id);
		}
		return id;
	}

	@Test
	public void emptyHistoryCannotGoAnywhere() {
		assertFalse(vm.canGoBack());
		assertFalse(vm.canGoForward());
		assertNull(vm.back());
		assertNull(vm.forward());
	}

	@Test
	public void firstPackageAloneHasNoBack() {
		view("a");
		assertFalse(vm.canGoBack());
		assertFalse(vm.canGoForward());
	}

	@Test
	public void backAndForwardLikeABrowser() {
		view("a", "b", "c");

		assertTrue(vm.canGoBack());
		assertFalse(vm.canGoForward());
		assertEquals("b", back());
		assertEquals("a", back());
		assertFalse(vm.canGoBack());
		assertTrue(vm.canGoForward());
		assertEquals("b", forward());
		assertEquals("c", forward());
		assertFalse(vm.canGoForward());
	}

	@Test
	public void showingAHistoryEntryDoesNotRecordIt() {
		view("a", "b");
		back();
		vm.settle();

		assertFalse(vm.hasPending());
		assertTrue(vm.canGoForward());
		assertEquals("b", forward());
	}

	@Test
	public void newPackageDropsTheForwardEntries() {
		view("a", "b", "c");
		back();
		back();
		view("d");

		assertFalse(vm.canGoForward());
		assertEquals("a", back());
		assertFalse(vm.canGoBack());
	}

	@Test
	public void selectionIsRecordedOnlyWhenItSettles() {
		view("a");
		select("b");
		select("c");
		assertTrue(vm.hasPending());
		vm.settle();

		// b was only passed over
		assertEquals("a", back());
		assertFalse(vm.canGoBack());
	}

	@Test
	public void backRecordsThePendingPackageFirst() {
		view("a", "b");
		back();
		select("c");

		// going back from c returns to a, and drops b from Forward
		assertTrue(vm.canGoBack());
		assertFalse(vm.canGoForward());
		assertEquals("a", back());
		assertEquals("c", forward());
		assertFalse(vm.canGoForward());
	}

	@Test
	public void reselectingTheCurrentPackageIsNoNewEntry() {
		view("a", "b");
		select("a");
		select("b");
		assertFalse(vm.hasPending());
		vm.settle();

		assertEquals("a", back());
		assertFalse(vm.canGoBack());
	}

	@Test
	public void reloadedListKeepsTheHistory() {
		view("a", "b");
		// new Package objects with the same ids; the list view model selects the same id again
		load("a", "b", "c", "d");
		select("b");

		assertFalse(vm.hasPending());
		assertEquals("a", back());
	}

	@Test
	public void packagesMissingFromTheListAreSkipped() {
		view("a", "b", "c");
		load("a", "c", "d");

		assertEquals("a", back());
		assertEquals("c", forward());

		load("c", "d");
		assertFalse(vm.canGoBack());
		load("a", "b", "c", "d");
		assertTrue(vm.canGoBack());
	}

	@Test
	public void oldestEntriesGoFirst() {
		load(ids(PackageHistoryViewModel.MAX_SIZE + 1));
		vm = new PackageHistoryViewModel(selection, packages);
		view(ids(PackageHistoryViewModel.MAX_SIZE + 1));

		int steps = 0;
		while (back() != null) {
			steps++;
		}
		assertEquals(PackageHistoryViewModel.MAX_SIZE - 1, steps);
		assertEquals("p1", selection.getSelectedPackage().getId());
	}

	@Test
	public void backWithPendingWhenTheHistoryIsFull() {
		String[] ids = ids(PackageHistoryViewModel.MAX_SIZE + 1);
		load(ids);
		vm = new PackageHistoryViewModel(selection, packages);
		String[] first = java.util.Arrays.copyOf(ids, PackageHistoryViewModel.MAX_SIZE);
		view(first);
		select(ids[PackageHistoryViewModel.MAX_SIZE]);

		assertEquals(ids[PackageHistoryViewModel.MAX_SIZE - 1], back());
		assertEquals(ids[PackageHistoryViewModel.MAX_SIZE], forward());
	}

	@Test
	public void notifiesWhenAPackageBecomesPendingOrIsRecorded() {
		int[] events = {0};
		vm.addChangeListener(e -> {
			assertSame(vm, e.getSource());
			events[0]++;
		});

		select("a");
		assertEquals(1, events[0]);
		vm.settle();
		assertEquals(2, events[0]);
		// settling with nothing pending changes nothing
		vm.settle();
		assertEquals(2, events[0]);
	}

	private static String[] ids(int count) {
		String[] ids = new String[count];
		for (int i = 0; i < count; i++) {
			ids[i] = "p" + i;
		}
		return ids;
	}
}
