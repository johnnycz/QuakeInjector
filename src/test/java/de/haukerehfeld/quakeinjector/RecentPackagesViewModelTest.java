package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.guimodel.Column;
import de.haukerehfeld.quakeinjector.guimodel.PackageInteractionViewModel;
import de.haukerehfeld.quakeinjector.guimodel.PackageListViewModel;
import de.haukerehfeld.quakeinjector.guimodel.RecentPackagesViewModel;
import de.haukerehfeld.quakeinjector.guimodel.RecentPackagesViewModel.Item;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.model.PackageList;
import de.haukerehfeld.quakeinjector.model.RecentPackages;
import de.haukerehfeld.quakeinjector.model.Requirement;
import de.haukerehfeld.quakeinjector.model.RequirementList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Recent menu's view model: which packages it shows, their labels and numbers, and when it tells the
 * menu to rebuild. Also {@link PackageListViewModel#rowIndexOf}, which the menu uses to find the row.
 */
public class RecentPackagesViewModelTest {
	private static final int NAME = Column.getColumnNumber(Column.NAME);

	private final RequirementList requirements = new RequirementList();
	private final PackageList packages = new PackageList(requirements);
	private final RecentPackages recent = new RecentPackages();

	private static Package pkg(String id, String title) {
		return new Package(id, "sha", id + ".zip", List.of("file:///" + id + ".zip"), "author", title,
			0, null, false, 0f, "description", new ExtractMapping(), "", List.of(), List.of());
	}

	/** Replace the package list, as loading the package database does. */
	private void load(Package... list) {
		requirements.setRequirements(new ArrayList<Requirement>(List.of(list)));
		requirements.notifyChangeListeners();
	}

	private void recent(String... newestFirst) {
		for (int i = newestFirst.length - 1; i >= 0; i--) {
			recent.add(newestFirst[i]);
		}
	}

	private static List<String> ids(List<Item> items) {
		return items.stream().map(Item::id).toList();
	}

	private static List<String> labels(List<Item> items) {
		return items.stream().map(Item::label).toList();
	}

	private static List<Character> mnemonics(List<Item> items) {
		return items.stream().map(Item::mnemonic).toList();
	}

	/** Counts the change events of the view model. */
	private static int[] countEvents(RecentPackagesViewModel vm) {
		int[] events = {0};
		vm.addChangeListener(e -> {
			assertSame(vm, e.getSource());
			events[0]++;
		});
		return events;
	}

	// --- items ---

	@Test
	public void showsTheRecentPackagesNewestFirst() {
		load(pkg("a", "Alpha"), pkg("b", "Beta"), pkg("c", "Gamma"));
		recent("c", "a", "b");

		RecentPackagesViewModel vm = new RecentPackagesViewModel(recent, packages);

		assertEquals(List.of("c", "a", "b"), ids(vm.items()));
		assertEquals(List.of("Gamma (c)", "Alpha (a)", "Beta (b)"), labels(vm.items()));
		assertEquals(List.of('1', '2', '3'), mnemonics(vm.items()));
	}

	@Test
	public void aPackageWithoutATitleShowsItsId() {
		load(pkg("a", ""), pkg("b", null), pkg("c", "  Spaced  "));
		recent("a", "b", "c");

		RecentPackagesViewModel vm = new RecentPackagesViewModel(recent, packages);

		assertEquals(List.of("a", "b", "Spaced (c)"), labels(vm.items()));
	}

	@Test
	public void nothingRecentShowsNothing() {
		load(pkg("a", "Alpha"));

		assertEquals(List.of(), new RecentPackagesViewModel(recent, packages).items());
	}

	@Test
	public void idsNotInThePackageListAreLeftOut() {
		load(pkg("a", "Alpha"), pkg("c", "Gamma"));
		recent("gone", "c", "removed", "a");

		RecentPackagesViewModel vm = new RecentPackagesViewModel(recent, packages);

		assertEquals(List.of("c", "a"), ids(vm.items()));
		assertEquals(List.of('1', '2'), mnemonics(vm.items()), "numbered without gaps");
	}

	@Test
	public void beforeTheDatabaseIsLoadedNothingShows() {
		recent("a", "b");

		assertEquals(List.of(), new RecentPackagesViewModel(recent, packages).items());
	}

	@Test
	public void theTenthItemIsNumberedZeroAndLaterOnesNotAtAll() {
		RecentPackages twelve = new RecentPackages(12);
		List<Package> list = new ArrayList<>();
		for (int i = 1; i <= 12; i++) {
			list.add(pkg("p" + i, "P" + i));
			twelve.add("p" + i);
		}
		load(list.toArray(new Package[0]));

		List<Item> items = new RecentPackagesViewModel(twelve, packages).items();

		assertEquals(12, items.size());
		assertEquals(List.of('1', '2', '3', '4', '5', '6', '7', '8', '9', '0'), mnemonics(items).subList(0, 10));
		assertNull(items.get(10).mnemonic());
		assertNull(items.get(11).mnemonic());
	}

	// --- updates ---

	@Test
	public void aChangedRecentListSendsAnEvent() {
		load(pkg("a", "Alpha"), pkg("b", "Beta"));
		RecentPackagesViewModel vm = new RecentPackagesViewModel(recent, packages);
		int[] events = countEvents(vm);

		recent.add("a");
		recent.add("b");
		recent.remove("a");

		assertEquals(3, events[0]);
		assertEquals(List.of("b"), ids(vm.items()));
	}

	@Test
	public void loadingTheDatabaseShowsTheRecentPackages() {
		recent("b", "a");
		RecentPackagesViewModel vm = new RecentPackagesViewModel(recent, packages);
		int[] events = countEvents(vm);

		load(pkg("a", "Alpha"), pkg("b", "Beta"));

		assertEquals(1, events[0]);
		assertEquals(List.of("b", "a"), ids(vm.items()));
	}

	@Test
	public void aReloadedDatabaseShowsTheNewTitles() {
		load(pkg("a", "Old title"));
		recent("a");
		RecentPackagesViewModel vm = new RecentPackagesViewModel(recent, packages);

		load(pkg("a", "New title"));

		assertEquals(List.of("New title (a)"), labels(vm.items()));
	}

	@Test
	public void aPackageGoneFromTheReloadedDatabaseDisappears() {
		load(pkg("a", "Alpha"), pkg("b", "Beta"));
		recent("a", "b");
		RecentPackagesViewModel vm = new RecentPackagesViewModel(recent, packages);

		load(pkg("b", "Beta"));

		assertEquals(List.of("b"), ids(vm.items()));
		assertEquals(List.of("a", "b"), recent.ids(), "it stays on the recent list");
	}

	@Test
	public void aRemovedListenerGetsNoMoreEvents() {
		RecentPackagesViewModel vm = new RecentPackagesViewModel(recent, packages);
		int[] events = {0};
		de.haukerehfeld.quakeinjector.utils.ChangeListener listener = e -> events[0]++;
		vm.addChangeListener(listener);
		recent.add("a");

		vm.removeChangeListener(listener);
		recent.add("b");

		assertEquals(1, events[0]);
	}

	// --- finding the row ---

	@Test
	public void rowIndexOfFindsThePackagesRow() {
		load(pkg("a", "Alpha"), pkg("b", "Beta"), pkg("c", "Gamma"));
		PackageListViewModel listVm = new PackageListViewModel(new PackageInteractionViewModel(null, null));
		listVm.setMapList(packages);

		for (String id : List.of("a", "b", "c")) {
			int row = listVm.rowIndexOf(id);
			assertTrue(row >= 0, id);
			assertEquals(id, listVm.getColumnData(NAME, row), "the row shows " + id);
		}
	}

	@Test
	public void rowIndexOfAnUnknownPackageIsMinusOne() {
		load(pkg("a", "Alpha"));
		PackageListViewModel listVm = new PackageListViewModel(new PackageInteractionViewModel(null, null));

		assertEquals(-1, listVm.rowIndexOf("a"), "no list yet");

		listVm.setMapList(packages);
		assertEquals(-1, listVm.rowIndexOf("x"));
		assertEquals(-1, listVm.rowIndexOf(null));
	}

	@Test
	public void rowIndexOfFollowsAReloadedList() {
		load(pkg("a", "Alpha"), pkg("b", "Beta"));
		PackageListViewModel listVm = new PackageListViewModel(new PackageInteractionViewModel(null, null));
		listVm.setMapList(packages);

		load(pkg("b", "Beta"));

		assertEquals(-1, listVm.rowIndexOf("a"));
		assertEquals("b", listVm.getColumnData(NAME, listVm.rowIndexOf("b")));
	}

	/**
	 * As in the app: the package table's view model and this one both listen to the package list. The
	 * table's view model registers itself again while the list notifies, which used to throw a
	 * ConcurrentModificationException at startup.
	 */
	@Test
	public void loadingTheDatabaseWorksWithTheTableListeningToo() {
		recent("a");
		PackageListViewModel listVm = new PackageListViewModel(new PackageInteractionViewModel(null, null));
		listVm.setMapList(packages);
		RecentPackagesViewModel vm = new RecentPackagesViewModel(recent, packages);

		load(pkg("a", "Alpha"));
		load(pkg("a", "Alpha"), pkg("b", "Beta"));

		assertEquals(List.of("a"), ids(vm.items()));
		assertEquals(2, listVm.size());
	}
}
