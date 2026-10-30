package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.utils.HtmlSanitizer;
import org.junit.jupiter.api.Test;

import static de.haukerehfeld.quakeinjector.utils.HtmlSanitizer.sanitize;
import static org.junit.jupiter.api.Assertions.*;

public class HtmlSanitizerTest {
	@Test
	public void keepsTheFormattingTheDatabaseUses() {
		String html = "A <b>big</b> map with <i>new</i> <u>monsters</u>, <s>bugs</s> and <code>cvars</code>.<br>"
			+ "<ul><li>one</li><li>two</li></ul>";

		assertEquals(html, sanitize(html));
	}

	@Test
	public void keepsHttpHttpsAndRelativeLinks() {
		assertEquals("<a href=\"https://example.com/a?x=1&amp;y=2\">x</a>",
			sanitize("<a href=\"https://example.com/a?x=1&amp;y=2\">x</a>"));
		assertEquals("<a href=\"http://example.com/\">x</a>", sanitize("<a href='http://example.com/'>x</a>"));
		assertEquals("<a href=\"7ad993da6c760c44\">x</a>", sanitize("<a href=7ad993da6c760c44 rel=\"nofollow\">x</a>"));
		assertEquals("<a href=\"../?include=metadata-%3Etags\">x</a>",
			sanitize("<a href=\"../?include=metadata-%3Etags\">x</a>"));
	}

	@Test
	public void dropsLinksThatCouldStartAProgram() {
		for (String href : new String[] {
			"file:///C:/Quake/id1/evil.bat", "file://attacker/share/x", "javascript:alert(1)",
			"ms-msdt:/id PCWDiagnostic", "search-ms:query=x", "mailto:a@b.c", "\\\\attacker\\share\\x",
			"//attacker.example/x", "https:attacker", " java\tscript:x", "FILE:///x",
			// encoded, so a plain check for ':' wouldn't see the scheme
			"file&#58;///C:/x", "file&#x3a;///C:/x", "file&colon;///C:/x", "&#102;ile:///x",
		}) {
			assertEquals("<a>x</a>", sanitize("<a href=\"" + href + "\">x</a>"), href);
		}
	}

	@Test
	public void dropsImagesStylesAndEverythingElseThatLoadsOrRuns() {
		assertEquals("ab", sanitize("a<img src=\"file://attacker/share/x.png\">b"));
		assertEquals("ab", sanitize("a<IMG SRC=http://tracker.example/x.gif>b"));
		assertEquals("ab", sanitize("a<style>body { background-image: url(file://attacker/x) }</style>b"));
		assertEquals("ab", sanitize("a<script>evil()</script>b"));
		assertEquals("a<p>b</p>", sanitize("a<p style=\"background-image: url(http://x/y)\">b</p>"));
		assertEquals("ab", sanitize("a<object classid=\"javax.swing.JButton\"><param name=text value=x></object>b"));
		assertEquals("ab", sanitize("a<link rel=stylesheet href=http://x/y.css><base href=\"file:///C:/\">b"));
		assertEquals("ab", sanitize("a<form action=x><input type=image src=http://x/y></form>b"));
		assertEquals("ab", sanitize("a<iframe src=http://x></iframe><frame src=y>b"));
		assertEquals("<b>x</b>", sanitize("<b onclick=\"x()\" id=y>x</b>"));
	}

	@Test
	public void attributesCantSneakTagsIn() {
		assertEquals("<a href=\"https://x/&quot;&gt;&lt;img src=y&gt;\">z</a>",
			sanitize("<a href='https://x/\"><img src=y>'>z</a>"));
		assertEquals("<b>x</b>", sanitize("<b title=\"><img src=y>\">x</b>"));
	}

	@Test
	public void textIsEscaped() {
		assertEquals("1 &lt; 2 &gt; 0", sanitize("1 < 2 > 0"));
		assertEquals("a &lt;img src=x b", sanitize("a <img src=x b"));
		assertEquals("Tom &amp; Jerry &amp; &lt;", sanitize("Tom & Jerry &amp; &lt;"));
		assertEquals("&quot;quoted&quot;", sanitize("\"quoted\""));
		assertEquals("", sanitize(null));
	}

	@Test
	public void commentsAndDoctypesAreDropped() {
		assertEquals("ab", sanitize("a<!-- <img src=x> -->b"));
		assertEquals("ab", sanitize("a<!DOCTYPE html>b"));
		assertEquals("a", sanitize("a<!-- not closed <img src=x>"));
	}

	@Test
	public void closingTagsWithoutOpeningOnesDontBreakAnything() {
		assertEquals("</b></a>x", sanitize("</b></a></img>x"));
		assertEquals("a<br>b<br>", sanitize("a<br/>b<BR></br>"));
	}

	@Test
	public void escapeShowsTextAsIs() {
		assertEquals("&lt;html&gt;&lt;img src=&quot;x&quot;&gt; &amp;", HtmlSanitizer.escape("<html><img src=\"x\"> &"));
	}
}
