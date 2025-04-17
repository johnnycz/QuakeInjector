/*
This file is part of QuakeInjector.

QuakeInjector is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

QuakeInjector is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with QuakeInjector.  If not, see <http://www.gnu.org/licenses/>.
*/
package de.haukerehfeld.quakeinjector;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Parses the output of <a href="https://api.quaddicted.com/jsons">https://api.quaddicted.com/jsons</a>
 * which is the new Quaddicted API introduced in 2024
 *
 * <p>https://github.com/hrehfeld/QuakeInjector/issues/152</p>
 *
 * <p>The main differences from the old API (besides being JSON instead of XML):</p>
 * <ul>
 *     <li>Rating is not present (will ask for it to be added)</li>
 *     <li>User rating is not present (will ask for it to be added)</li>
 *     <li>Old identifier is not present (might not be needed, but see below)</li>
 *     <li>Dependency references are identifiers like this: "ad_v1_80p1final", however this identifier is not present
 *     in the actual dependency. Closest we get is filename: "ad_v1_80p1final.zip" which works fine, just a bit sketchy</li>
 *     <li>Contains significantly more data (10x) and takes longer to download (10x). We don't need "files" or "tags_text" for example.</li>
 * </ul>
 */
public class PackageDatabaseJsonParser implements PackageDatabaseParser {

    private final SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Configuration configuration;

    public PackageDatabaseJsonParser(Configuration configuration) {
        this.configuration = configuration;
    }

    private static class JsonPackage {
        public String sha256;
        public Metadata metadata;
    }

    private static class Metadata {
        public List<String> tags;
        public List<String> urls;
        public long bytes;
        public List<String> notes;
        public String title;
        public List<String> authors;
        public Install install;
        public String description;
        public String release_date;
    }

    private static class Install {
        public String extract;
    }

    private static class ProcessedTags {
        public String filename;
        public List<String> dependencies = new ArrayList<>();
        public String commandLine;
        public List<String> startMaps = new ArrayList<>();
        public String zipbasedir;
        public List<String> links = new ArrayList<>();
    }

    @Override
    public List<Requirement> parse(InputStream is) {
        List<JsonPackage> jsonPackages = parseJsonPackages(is);

        List<Requirement> result = new ArrayList<>(jsonPackages.size());
        HashMap<String, Requirement> packages = new HashMap<>();
        Map<Package,List<String>> unresolvedRequirements = new HashMap<>();

        for (JsonPackage jsonPackage : jsonPackages) {
            Package pkg;
            try {
                pkg = getPackage(jsonPackage, unresolvedRequirements);
            } catch (Exception e) {
                System.err.printf("Cannot process package %s (%s):%n%s%n%n",
                        jsonPackage.metadata.title, jsonPackage.sha256, e.getMessage());
                continue;
            }
            result.add(pkg);
            packages.put(pkg.getId(), pkg);
        }

        resolveRequirements(unresolvedRequirements, packages);

        return result;
    }

    private Package getPackage(JsonPackage jsonPackage, Map<Package,List<String>> unresolvedRequirements) {
        Date releaseDate = null;
        try {
            releaseDate = dateParser.parse(jsonPackage.metadata.release_date);
        } catch (ParseException e) {
            throw new PackageDatabaseParseException("Cannot parse date '" + jsonPackage.metadata.release_date + "': " + e.getMessage());
        }

        var processedTags = processTags(jsonPackage.metadata.tags);

        var zipbasedir = getZipbasedir(jsonPackage, processedTags);

        StringBuilder description = getDescription(jsonPackage, processedTags);

        List<String> urls = getDownloadUrls(jsonPackage, processedTags);

        var pkg = new Package(
                stripExtension(processedTags.filename),
                jsonPackage.sha256,
                processedTags.filename,
                urls,
                String.join(", ", jsonPackage.metadata.authors),
                jsonPackage.metadata.title,
                (int) (jsonPackage.metadata.bytes/1000L),
                releaseDate,
                false,
                Package.Rating.Average, // TODO
                4, // TODO
                description.toString(),
                zipbasedir,
                processedTags.commandLine,
                processedTags.startMaps,
                Collections.emptyList()
        );
        unresolvedRequirements.put(pkg, processedTags.dependencies);
        return pkg;
    }

    private String getZipbasedir(JsonPackage jsonPackage, ProcessedTags processedTags) {
        var zipbasedir = processedTags.zipbasedir;
        if (zipbasedir == null) {
            if (jsonPackage.metadata.install == null) {
                throw new PackageDatabaseParseException("metadata.install missing");
            }
            if (jsonPackage.metadata.install.extract == null) {
                throw new PackageDatabaseParseException("metadata.install.extract is missing");
            }
            zipbasedir = jsonPackage.metadata.install.extract;
        }
        if (zipbasedir.startsWith("{base}")) {
            zipbasedir = zipbasedir.substring("{base}".length());
        }
        if (zipbasedir.startsWith("/")) {
            zipbasedir = zipbasedir.substring(1);
        }
        return zipbasedir;
    }

    private List<String> getDownloadUrls(JsonPackage jsonPackage, ProcessedTags processedTags) {
        List<String> urls = jsonPackage.metadata.urls;
        if (urls == null || urls.isEmpty()) {
            urls = Collections.singletonList(configuration.RepositoryBasePath.getRepositoryUrl(processedTags.filename, jsonPackage.sha256));
        }
        return urls;
    }

    private StringBuilder getDescription(JsonPackage jsonPackage, ProcessedTags processedTags) {
        StringBuilder description = new StringBuilder(jsonPackage.metadata.description);
        if (jsonPackage.metadata.notes != null && !jsonPackage.metadata.notes.isEmpty()) {
            description.append("<br /><br />Notes:<br />");
            description.append(String.join("<br /><br />", jsonPackage.metadata.notes));
        }
        description.append("<br /><br />Links:<ul>");
        description.append("<li><a href=\"")
                .append(configuration.mapWebpageBaseUrl.get())
                .append(jsonPackage.sha256)
                .append("\">Quaddicted</a></li>");

        if (processedTags.links != null && !processedTags.links.isEmpty()) {
            for (String link: processedTags.links) {
                int separatorIndex = link.indexOf("](");
                if (link.startsWith("[") && separatorIndex > 0 && link.endsWith(")")) {
                    String desc = link.substring(1, separatorIndex);
                    String href = link.substring(separatorIndex + 2, link.length() - 1);
                    description.append("<li><a href=\"").append(href).append("\">").append(desc).append("</a></li>");
                }
            }
        }
        description.append("</ul>");
        return description;
    }

    private List<JsonPackage> parseJsonPackages(InputStream is) {
        CollectionType type = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, JsonPackage.class);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<JsonPackage> jsonPackages;
        try {
            jsonPackages = objectMapper.readValue(is, type);
        } catch (IOException e) {
            throw new PackageDatabaseParseException(e);
        }
        return jsonPackages;
    }

    private String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index == -1) {
            return filename;
        }
        else {
            return filename.substring(0, index);
        }
    }

    private ProcessedTags processTags(List<String> tags) {
        ProcessedTags processed = new ProcessedTags();
        for (String tag: tags) {
            if (tag.startsWith("filename=")) {
                processed.filename = tag.substring("filename=".length());
            }
            if (tag.startsWith("dependency=")) {
                processed.dependencies.add(tag.substring("dependency=".length()));
            }
            if (tag.startsWith("commandline=")) {
                processed.commandLine = tag.substring("commandline=".length());
            }
            if (tag.startsWith("startmap=")) {
                processed.startMaps.add(tag.substring("startmap=".length()));
            }
            if (tag.startsWith("zipbasedir=")) {
                processed.zipbasedir = tag.substring("zipbasedir=".length());
            }
            if (tag.startsWith("link=")) {
                processed.links.add(tag.substring("link=".length()));
            }
        }
        return processed;
    }

    private void resolveRequirements(Map<Package,List<String>> unresolvedRequirements,
                                     Map<String, Requirement> packages) {
        for (Map.Entry<Package,List<String>> entry: unresolvedRequirements.entrySet()) {
            Package current = entry.getKey();
            List<String> reqs = entry.getValue();

            List<Requirement> resolvedRequirements = new ArrayList<>(reqs.size());
            for (String id: reqs) {
                Requirement resolved = packages.get(id);
                if (resolved == null) {
                    resolved = new UnavailableRequirement(id);
                    packages.put(id, resolved);
                }
                resolvedRequirements.add(resolved);
            }
            current.setRequirements(resolvedRequirements);
        }
    }
}
