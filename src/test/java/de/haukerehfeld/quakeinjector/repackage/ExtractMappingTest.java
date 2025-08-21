package de.haukerehfeld.quakeinjector.repackage;

import de.haukerehfeld.quakeinjector.Configuration;
import de.haukerehfeld.quakeinjector.model.ExtractMapping;
import de.haukerehfeld.quakeinjector.model.Package;
import de.haukerehfeld.quakeinjector.feature.list.PackageDatabaseParser;
import de.haukerehfeld.quakeinjector.feature.list.PackageDatabaseSolrJsonParser;
import de.haukerehfeld.quakeinjector.model.Requirement;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ExtractMappingTest {

    private final PackageDatabaseParser parser = new PackageDatabaseSolrJsonParser(new Configuration(new File("nonexistent")));
    private ExtractMapping mapping;

    @Test
    public void movesMisplacedFile() {
        parse("""
            "install": {
              "extract": "{base}/ad/",
              "extractmapping": {
                "/foo/bar/m2.bsp": "{base}/ad/maps/m2.bsp"
              }
            }
        """, "zipbasedir=ad/");

        verifyMapping().from("maps/m1.bsp").to("ad/maps/m1.bsp");
        verifyMapping().from("foo/bar/m2.bsp").to("ad/maps/m2.bsp");
    }

    @Test
    public void remapsAllToId1() {
        parse("""
            "install": {
              "extract": "{base}/id1/"
            }
        """);

        verifyMapping().from("/maps/mymap.bsp").to("id1/maps/mymap.bsp");
        verifyMapping().from("/sounds/mysound.bsp").to("id1/sounds/mysound.bsp");
    }

    @Test
    public void handlesZipbasedirFormat() {
        parse("""
            "install": {
            }
        """, "zipbasedir=id1/");

        verifyMapping().from("/maps/mymap.bsp").to("id1/maps/mymap.bsp");
        verifyMapping().from("/sounds/mysound.bsp").to("id1/sounds/mysound.bsp");
    }

    @Test
    public void supportsInstallExtractFormat() {
        parse("""
            "install": {
              "extract": "{base}/id1/"
            }
        """);

        verifyMapping().from("/maps/mymap.bsp").to("id1/maps/mymap.bsp");
        verifyMapping().from("/sounds/mysound.bsp").to("id1/sounds/mysound.bsp");
    }

    @Test
    public void mapToRootFolder() {
        parse("""
            "install": {
              "extract": "{base}/"
            }
        """);

        verifyMapping().from("mymod/maps/mymap.bsp").to("mymod/maps/mymap.bsp");
        verifyMapping().from("mymod/sounds/mysound.bsp").to("mymod/sounds/mysound.bsp");
    }

    @Test
    public void supportExtractMappingFormat() {
        parse("""
            "install": {
              "extractmapping": {
                "idX": "{base}/id1/",
                "idY": "{base}/id1/"
              }
            }
        """);

        verifyMapping().from("/idX/maps/mymap.bsp").to("id1/maps/mymap.bsp");
        verifyMapping().from("/idY/sounds/mysound.bsp").to("id1/sounds/mysound.bsp");
    }

    @Test
    public void workWithRootContainerDir() {
        parse("""
            "install": {
              "extractmapping": {
                "mypackage": "{base}/id1"
              }
            }
        """);

        verifyMapping().from("mypackage/maps/mymap.bsp").to("id1/maps/mymap.bsp");
    }

    @Test
    public void renamesFiles() {
        parse("""
            "install": {
              "extractmapping": {
                "/": "{base}/id1/",
                "/readme.txt": "{base}/id1/maps/mymap.txt"
              }
            }
        """);

        verifyMapping().from("maps/mymap.bsp").to("id1/maps/mymap.bsp");
        verifyMapping().from("readme.txt").to("id1/maps/mymap.txt");
    }

    @Test
    public void leadingSlashIsOptional() {
        parse("""
            "install": {
              "extractmapping": {
                "": "{base}/id1/maps/",
                "/idX": "{base}/id1/",
                "idY": "id1"
              }
            }
        """);

        verifyMapping().from("idX/maps/mymap.bsp").to("id1/maps/mymap.bsp");
        verifyMapping().from("idY/sounds/mysound.bsp").to("id1/sounds/mysound.bsp");
        verifyMapping().from("myreadme.txt").to("id1/maps/myreadme.txt");
    }

    @Test
    public void emptyMappingKeepsThingsUnchanged() {
        parse("""
            "install": {
            }
        """);

        verifyMapping().from("id1/maps/mymap.bsp").to("id1/maps/mymap.bsp");
    }

    @Test
    public void missingInstallKeepsThingsUnchanged() {
        parse("""
            "foo": "bar"
        """);

        verifyMapping().from("id1/maps/mymap.bsp").to("id1/maps/mymap.bsp");
    }

    @Test
    public void skipsWhenMappedToNull() {
        parse("""
            "install": {
              "extractmapping": {
                "junkfile": null,
                "notjunk.bsp": "{base}/id1/maps/notjunk.bsp"
              }
            }
        """);

        verifyMapping().from("/junkfile").to(null);
        verifyMapping().from("/notjunk.bsp").to("id1/maps/notjunk.bsp");
    }

    @Test
    public void specificFolderOverridesDefault() {
        parse("""
            "install": {
              "extract": "{base}/id1",
              "extractmapping": {
                "foobar": "{base}/id1/maps/"
              }
            }
        """);

        verifyMapping().from("foobar/mymap.bsp").to("id1/maps/mymap.bsp");
    }

    @Test
    public void longestRuleWins() {
        parse("""
            "install": {
              "extractmapping": {
                "foo": "{base}/id0/",
                "foo/bar/xyz": "{base}/id1/",
                "foo/bar": "{base}/id2/"
              }
            }
        """);

        verifyMapping().from("foo/bar/xyz/maps/mymap.bsp").to("id1/maps/mymap.bsp");
    }

    @Test
    public void noPartialMatches() {
        parse("""
            "install": {
               "extractmapping": {
                    "": "{base}/",
                    "foo": "{base}/id2/"
               }
            }
        """);

        verifyMapping().from("foobar/maps/mymap.bsp").to("foobar/maps/mymap.bsp");
    }

    @Test
    public void skipsFileInValidDirectory() {
        parse("""
            "install": {
              "extractmapping": {
                "mypackage/": "{base}/id1/",
                "mypackage/junkfile.txt": null
              }
            }
        """);

        verifyMapping().from("mypackage/maps/mymap.bsp").to("id1/maps/mymap.bsp");
        verifyMapping().from("mypackage/junkfile.txt").to(null);
    }

    @Test
    public void skipsEntireDirectory() {
        parse("""
            "install": {
              "extractmapping": {
                "/": "{base}/id1/",
                "/junkdir": null
              }
            }
        """);

        verifyMapping().from("maps/mymap.bsp").to("id1/maps/mymap.bsp");
        verifyMapping().from("junkdir/junkfile.txt").to(null);
    }

    @Test
    public void extractMappingIsHighestPriority() {
        parse("""
            "install": {
              "extract": "{base}/ad",
              "extractmapping": {
                "": "{base}/copper"
              }
            }
        """, "zipbasedir=id1");

        verifyMapping().from("maps/mymap.bsp").to("copper/maps/mymap.bsp");
    }

    private class MappingVerifier {
        private String from;
        private String to;
        private boolean fromSet = false;
        private boolean toSet = false;

        public MappingVerifier from(String from) {
            this.from = from;
            fromSet = true;

            verify();
            return this;
        }

        public MappingVerifier to(String to) {
            this.to = to;
            toSet = true;

            verify();
            return this;
        }

        private void verify() {
            if (fromSet && toSet) {
                var toReplaced = to != null ? to.replace('/', File.separatorChar) : null;
                assertEquals(toReplaced, mapping.remap(from));
            }
        }
    }

    private MappingVerifier verifyMapping() {
        return new MappingVerifier();
    }

    private void parse(String snippet) {
        parse(snippet, "foobar=foobar");
    }

    private void parse(String snippet, String extratag) {
        var json = """
                [{
                "description": "",
                "sha256": "aaaaaaaaaaaaaaaa",
                "tags": [
                  "release_date=2025-08-09",
                  "filename=xxx.zip",
                """ + "\"" + extratag + "\"" + """
                ],
                """ + snippet + "}]";
        System.out.println(json);
        var is = new ByteArrayInputStream(json.getBytes(Charset.defaultCharset()));
        List<Requirement> rs = parser.parse(is);
        assertNotNull(rs);
        assertEquals(1, rs.size());
        Requirement r = rs.get(0);
        assertInstanceOf(Package.class, r);
        Package p = (Package) r;
        mapping = p.getExtractMapping();
    }
}
