package de.haukerehfeld.quakeinjector;

import de.haukerehfeld.quakeinjector.utils.RelativePath;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RelativePathTest {

    @Test
    public void producesRelativePaths() {
        assertRelative("d/e", "/a/b/c", "/a/b/c/d/e");
        assertRelative("../c2/d/e", "/a/b/c", "/a/b/c2/d/e");
        assertRelative("e", "/a/b/c/d", "/a/b/c/d/e");
        assertRelative("../../../x/y/z", "/a/b/c", "/x/y/z");
        assertRelative("..", "/foo/bar", "/foo");
    }

    private void assertRelative(String expectedPath, String home, String file) {
        var relative = RelativePath.getRelativePath(new File(home), new File(file));
        expectedPath = expectedPath.replace('/', File.separatorChar);
        assertEquals(expectedPath, relative.getPath());
    }
}
