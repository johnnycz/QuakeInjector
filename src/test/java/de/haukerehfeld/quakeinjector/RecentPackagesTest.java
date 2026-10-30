package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.model.Configuration;
import de.haukerehfeld.quakeinjector.model.RecentPackages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The recent packages list on its own: order, moving to the top, the size limit, removing, change
 * events and the settings value.
 */
public class RecentPackagesTest {

	private static RecentPackages recent(String... newestFirst) {
		RecentPackages recent = new RecentPackages();
		for (int i = newestFirst.length - 1; i >= 0; i--) {
			recent.add(newestFirst[i]);
		}
		return recent;
	}

	/** Counts the change events of the list. */
	private static int[] countEvents(RecentPackages recent) {
		int[] events = {0};
		recent.addChangeListener(e -> {
			assertSame(recent, e.getSource());
			events[0]++;
		});
		return events;
	}

	// --- adding ---

	@Test
	public void startsEmpty() {
		RecentPackages recent = new RecentPackages();

		assertEquals(List.of(), recent.ids());
		assertEquals(10, recent.maxSize());
		assertEquals(RecentPackages.MAX_SIZE, recent.maxSize());
	}

	@Test
	public void theNewestPackageIsOnTop() {
		RecentPackages recent = new RecentPackages();

		recent.add("a");
		recent.add("b");
		recent.add("c");

		assertEquals(List.of("c", "b", "a"), recent.ids());
	}

	@Test
	public void aPackageAlreadyOnTheListMovesToTheTop() {
		RecentPackages recent = recent("c", "b", "a");

		recent.add("a");

		assertEquals(List.of("a", "c", "b"), recent.ids(), "on the list once, the others moved down");
	}

	@Test
	public void addingTheTopPackageAgainChangesNothing() {
		RecentPackages recent = recent("b", "a");
		int[] events = countEvents(recent);

		recent.add("b");

		assertEquals(List.of("b", "a"), recent.ids());
		assertEquals(0, events[0], "no event");
	}

	@Test
	public void nullOrBlankIdsAreIgnored() {
		RecentPackages recent = recent("a");
		int[] events = countEvents(recent);

		recent.add(null);
		recent.add("");
		recent.add("  ");

		assertEquals(List.of("a"), recent.ids());
		assertEquals(0, events[0]);
	}

	// --- size limit ---

	@Test
	public void holdsAtMostTenPackages() {
		RecentPackages recent = new RecentPackages();
		for (int i = 1; i <= 10; i++) {
			recent.add("p" + i);
		}
		assertEquals(10, recent.ids().size());

		recent.add("p11");

		List<String> expected = new ArrayList<>();
		for (int i = 11; i >= 2; i--) {
			expected.add("p" + i);
		}
		assertEquals(expected, recent.ids(), "the oldest (p1) dropped off the end");
	}

	@Test
	public void movingAPackageUpOnAFullListDropsNothing() {
		RecentPackages recent = new RecentPackages(3);
		recent.add("a");
		recent.add("b");
		recent.add("c");

		recent.add("a");

		assertEquals(List.of("a", "c", "b"), recent.ids());
	}

	@Test
	public void theSizeCanBeChosen() {
		RecentPackages recent = new RecentPackages(2);

		recent.add("a");
		recent.add("b");
		recent.add("c");

		assertEquals(List.of("c", "b"), recent.ids());
		assertEquals(2, recent.maxSize());
	}

	@Test
	public void theSizeMustBeAtLeastOne() {
		assertThrows(IllegalArgumentException.class, () -> new RecentPackages(0));
	}

	@Test
	public void aDroppedPackageCanComeBack() {
		RecentPackages recent = new RecentPackages(2);
		recent.add("a");
		recent.add("b");
		recent.add("c");

		recent.add("a");

		assertEquals(List.of("a", "c"), recent.ids());
	}

	// --- removing ---

	@Test
	public void removingTakesThePackageOffAndTheOthersMoveUp() {
		RecentPackages recent = recent("c", "b", "a");

		recent.remove("b");

		assertEquals(List.of("c", "a"), recent.ids());
		assertFalse(recent.contains("b"));
	}

	@Test
	public void removingAPackageNotOnTheListChangesNothing() {
		RecentPackages recent = recent("a");
		int[] events = countEvents(recent);

		recent.remove("x");
		recent.remove(null);

		assertEquals(List.of("a"), recent.ids());
		assertEquals(0, events[0]);
	}

	@Test
	public void afterRemovingThereIsRoomForAnotherPackage() {
		RecentPackages recent = new RecentPackages(2);
		recent.add("a");
		recent.add("b");
		recent.remove("b");

		recent.add("c");

		assertEquals(List.of("c", "a"), recent.ids(), "a isn't dropped: the list isn't over the limit");
	}

	// --- events and copies ---

	@Test
	public void eachChangeSendsOneEvent() {
		RecentPackages recent = new RecentPackages();
		int[] events = countEvents(recent);

		recent.add("a");
		recent.add("b");
		recent.add("a");
		recent.remove("b");

		assertEquals(4, events[0]);
	}

	@Test
	public void listenersSeeTheChangedList() {
		RecentPackages recent = recent("a");
		List<List<String>> seen = new ArrayList<>();
		recent.addChangeListener(e -> seen.add(recent.ids()));

		recent.add("b");
		recent.remove("a");

		assertEquals(List.of(List.of("b", "a"), List.of("b")), seen);
	}

	@Test
	public void aRemovedListenerGetsNoMoreEvents() {
		RecentPackages recent = new RecentPackages();
		int[] events = {0};
		de.haukerehfeld.quakeinjector.utils.ChangeListener listener = e -> events[0]++;
		recent.addChangeListener(listener);
		recent.add("a");

		recent.removeChangeListener(listener);
		recent.add("b");

		assertEquals(1, events[0]);
	}

	@Test
	public void idsIsACopy() {
		RecentPackages recent = recent("a");
		List<String> ids = recent.ids();

		recent.add("b");

		assertEquals(List.of("a"), ids, "later changes don't show in it");
		assertThrows(UnsupportedOperationException.class, () -> ids.add("x"));
	}

	// --- settings value ---

	@Test
	public void theSettingHoldsTheIdsNewestFirst() {
		assertEquals("c,b,a", recent("c", "b", "a").toSetting());
		assertEquals("", new RecentPackages().toSetting());
	}

	@Test
	public void readingTheSettingGivesTheSameList() {
		RecentPackages saved = recent("c", "b", "a");

		RecentPackages read = RecentPackages.fromSetting(saved.toSetting());

		assertEquals(List.of("c", "b", "a"), read.ids());
		assertEquals(RecentPackages.MAX_SIZE, read.maxSize());
	}

	@Test
	public void aMissingOrBlankSettingIsAnEmptyList() {
		assertEquals(List.of(), RecentPackages.fromSetting(null).ids());
		assertEquals(List.of(), RecentPackages.fromSetting("").ids());
		assertEquals(List.of(), RecentPackages.fromSetting(" , ,").ids());
	}

	@Test
	public void aHandEditedSettingIsCleanedUp() {
		RecentPackages read = RecentPackages.fromSetting(" a , b,,a, c ");

		assertEquals(List.of("a", "b", "c"), read.ids(), "spaces, empty entries and repeats are dropped");
	}

	@Test
	public void aSettingWithTooManyIdsKeepsTheNewest() {
		RecentPackages read = RecentPackages.fromSetting("a,b,c,d", 2);

		assertEquals(List.of("a", "b"), read.ids());
	}

	@Test
	public void aListReadFromTheSettingKeepsWorking() {
		RecentPackages read = RecentPackages.fromSetting("a,b", 2);

		read.add("c");

		assertEquals(List.of("c", "a"), read.ids());
	}

	// --- bound to the settings ---

	private Configuration config(Path tmp) {
		return new Configuration(tmp.resolve("config.properties").toFile());
	}

	@Test
	public void aBoundListStartsWithTheSetting(@TempDir Path tmp) {
		Configuration config = config(tmp);
		config.RecentPackages.set("a,b");

		assertEquals(List.of("a", "b"), RecentPackages.boundTo(config.RecentPackages).ids());
	}

	@Test
	public void aBoundListUpdatesTheSetting(@TempDir Path tmp) {
		Configuration config = config(tmp);
		RecentPackages recent = RecentPackages.boundTo(config.RecentPackages);

		recent.add("a");
		recent.add("b");

		assertEquals("b,a", config.RecentPackages.get());
	}

	@Test
	public void anEmptyBoundListRemovesTheSetting(@TempDir Path tmp) {
		Configuration config = config(tmp);
		config.RecentPackages.set("a");
		RecentPackages recent = RecentPackages.boundTo(config.RecentPackages);

		recent.remove("a");

		assertNull(config.RecentPackages.get());
		assertFalse(config.RecentPackages.exists());
	}
}
