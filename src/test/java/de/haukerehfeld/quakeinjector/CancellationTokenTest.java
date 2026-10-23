package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.utils.CancellationToken;
import de.haukerehfeld.quakeinjector.utils.CancelledException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class CancellationTokenTest {
	@Test
	public void startsNotCancelled() {
		CancellationToken token = new CancellationToken();

		assertFalse(token.isCancelled());
		assertDoesNotThrow(token::throwIfCancelled);
	}

	@Test
	public void throwsOnceCancelled() {
		CancellationToken token = new CancellationToken();
		token.cancel();

		assertTrue(token.isCancelled());
		assertThrows(CancelledException.class, token::throwIfCancelled);
	}

	@Test
	public void runsCallbackOnceOnCancel() {
		CancellationToken token = new CancellationToken();
		AtomicInteger calls = new AtomicInteger();
		token.onCancel(calls::incrementAndGet);

		token.cancel();
		token.cancel();

		assertEquals(1, calls.get());
	}

	@Test
	public void runsCallbackImmediatelyWhenAlreadyCancelled() {
		CancellationToken token = new CancellationToken();
		token.cancel();
		AtomicInteger calls = new AtomicInteger();

		token.onCancel(calls::incrementAndGet);

		assertEquals(1, calls.get());
	}

	@Test
	public void closedRegistrationIsNotCalled() {
		CancellationToken token = new CancellationToken();
		AtomicInteger calls = new AtomicInteger();
		CancellationToken.Registration registration = token.onCancel(calls::incrementAndGet);

		registration.close();
		token.cancel();

		assertEquals(0, calls.get());
	}

	@Test
	public void failingCallbackDoesNotStopOtherCallbacks() {
		CancellationToken token = new CancellationToken();
		AtomicInteger calls = new AtomicInteger();
		token.onCancel(() -> { throw new IllegalStateException("boom"); });
		token.onCancel(calls::incrementAndGet);

		token.cancel();

		assertEquals(1, calls.get());
		assertTrue(token.isCancelled());
	}
}
