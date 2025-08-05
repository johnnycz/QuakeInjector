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
import de.haukerehfeld.quakeinjector.repackage.ExtractMapping;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Parses the output of <a href="https://www.quaddicted.com/api/v1/">https://www.quaddicted.com/api/v1/</a>
 * which is a slightly updated API for Quaddicted website, superseding the previous "jsons". It is
 * also commonly referred to as Solr
 */
public class PackageDatabaseSolrJsonParser implements PackageDatabaseParser {

    private final SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Configuration configuration;

    public PackageDatabaseSolrJsonParser(Configuration configuration) {
        this.configuration = configuration;
    }

    private static class JsonPackage {
        public String sha256;
        public List<String> tags;
        public List<String> urls;
        public long bytes;
        public List<String> notes;
        public Install install;
        public String description;
    }

    private static class Install {
        public String extract;
        public Map<String, String> extractmapping;
    }

    private static class ProcessedTags {
        public String release_date;
        public String title;
        public String filename;
        public List<String> dependencies = new ArrayList<>();
        public String commandLine;
        public List<String> startMaps = new ArrayList<>();
        public String zipbasedir;
        public List<String> links = new ArrayList<>();
        public List<String> authors = new ArrayList<>();
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
            } catch (PackageDatabaseParseException e) {
                System.err.printf("Cannot process package %s:%n%s%n%n",
                        jsonPackage.sha256, e.getMessage());

                continue;
            } catch (Exception e) {
                System.err.printf("Cannot process package %s:%n%s%n%n",
                        jsonPackage.sha256, e.getMessage());
                e.printStackTrace();
                continue;
            }
            result.add(pkg);
            packages.put(pkg.getId(), pkg);
        }

        resolveRequirements(unresolvedRequirements, packages);

        return result;
    }

    private Package getPackage(JsonPackage jsonPackage, Map<Package,List<String>> unresolvedRequirements) {
        var processedTags = processTags(jsonPackage.tags);

        Date releaseDate = null;
        try {
            releaseDate = dateParser.parse(processedTags.release_date);
        } catch (ParseException e) {
            throw new PackageDatabaseParseException("Cannot parse date '" + processedTags.release_date + "': " + e.getMessage());
        }

         var extractMapping = getExtractMapping(jsonPackage, processedTags);

        StringBuilder description = getDescription(jsonPackage, processedTags);

        List<String> urls = getDownloadUrls(jsonPackage, processedTags);

        String legacyId = stripExtension(processedTags.filename);

        List<String> startMaps = reorderStartMaps(processedTags.startMaps);

        if (startMaps.isEmpty()) {
            startMaps.add(legacyId);
        }

        var pkg = new Package(
                legacyId,
                jsonPackage.sha256,
                processedTags.filename,
                urls,
                String.join(", ", processedTags.authors),
                processedTags.title,
                (int) (jsonPackage.bytes/1000L),
                releaseDate,
                false,
                (float) Math.random()*5, // TODO
                description.toString(),
                extractMapping,
                processedTags.commandLine,
                startMaps,
                Collections.emptyList()
        );
        unresolvedRequirements.put(pkg, processedTags.dependencies);
        return pkg;
    }

	/**
	 * Move maps that contain the word "start" to the beginning of the list
	 */
	private List<String> reorderStartMaps(List<String> startMaps) {
		List<String> preferredStartMaps = new ArrayList<>(startMaps.size());
		List<String> otherStartMaps = new ArrayList<>(startMaps.size());

		for (String map: startMaps) {
			if (map.contains("start")) {
				preferredStartMaps.add(map);
			} else {
				otherStartMaps.add(map);
			}
		}

		List<String> result = new ArrayList<>(preferredStartMaps.size() + otherStartMaps.size());
		result.addAll(preferredStartMaps);
		result.addAll(otherStartMaps);
		return result;
	}

	private ExtractMapping getExtractMapping(JsonPackage jsonPackage, ProcessedTags processedTags) {
		ExtractMapping mapping = new ExtractMapping();

		if (processedTags.zipbasedir != null) {
			mapping.addMapping("/", processedTags.zipbasedir);
		}

		Install install = jsonPackage.install;
		if (install != null) {
			if (install.extract != null) {
				mapping.addMapping("/", install.extract);
			}
			if (install.extractmapping != null) {
				for (Map.Entry<String, String> entry : install.extractmapping.entrySet()) {
					mapping.addMapping(entry.getKey(), entry.getValue());
				}
			}
		}

		return mapping;
	}

    private List<String> getDownloadUrls(JsonPackage jsonPackage, ProcessedTags processedTags) {
        List<String> urls = jsonPackage.urls;
        if (urls == null || urls.isEmpty()) {
            urls = Collections.singletonList(configuration.RepositoryBasePath.getRepositoryUrl(processedTags.filename, jsonPackage.sha256));
        }
        return urls;
    }

    private StringBuilder getDescription(JsonPackage jsonPackage, ProcessedTags processedTags) {
        StringBuilder description = new StringBuilder(jsonPackage.description);
        if (jsonPackage.notes != null && !jsonPackage.notes.isEmpty()) {
            description.append("<br /><br />Notes:<br />");
            description.append(String.join("<br /><br />", jsonPackage.notes));
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
            if (tag.startsWith("title=")) {
                processed.title = tag.substring("title=".length());
            }
            if (tag.startsWith("author=")) {
                processed.authors.add(tag.substring("author=".length()));
            }
            if (tag.startsWith("release_date")) {
                processed.release_date = tag.substring("release_date=".length());
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
