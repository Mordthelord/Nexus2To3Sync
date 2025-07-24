package com.upload;

import com.fasterxml.jackson.databind.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

    public class NexusFileComparator {

        private static final String localPath = "C:\\storage\\repository";
        private static final String nexusRepoUrl = "https://nexus.company.com/service/rest/v1/assets?repository=";
        private static final String nexusUsername = "username"; // or null if anonymous
        private static final String nexusPassword = "password"; // or null if anonymous

        public static void main(String[] args) throws Exception {
            Set<String> localFiles = listLocalFiles(Paths.get(localPath));
            Set<String> nexusFiles = listNexusFiles();

            Set<String> inBoth = new HashSet<>(localFiles);
            inBoth.retainAll(nexusFiles);

            Set<String> onlyInLocal = new HashSet<>(localFiles);
            onlyInLocal.removeAll(nexusFiles);

            Set<String> onlyInNexus = new HashSet<>(nexusFiles);
            onlyInNexus.removeAll(localFiles);

            System.out.println("=== Summary ===");
            System.out.println("Local file count: " + localFiles.size());
            System.out.println("Nexus file count: " + nexusFiles.size());
            System.out.println("In both: " + inBoth.size());
            System.out.println("Only in local: " + onlyInLocal.size());
            System.out.println("Only in Nexus: " + onlyInNexus.size());

            System.out.println("\n=== Only in Local ===");
            onlyInLocal.forEach(System.out::println);

            System.out.println("\n=== Only in Nexus ===");
            onlyInNexus.forEach(System.out::println);
        }

        private static Set<String> listLocalFiles(Path path) throws IOException {
            try (Stream<Path> files = Files.walk(path)) {
                return files.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.toSet());
            }
        }

        private static Set<String> listNexusFiles() throws IOException {
            Set<String> fileNames = new HashSet<>();
            String continuationToken = null;

            do {
                String urlStr = nexusRepoUrl + (continuationToken != null ? "&continuationToken=" + continuationToken : "");
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                if (nexusUsername != null && nexusPassword != null) {
                    String auth = Base64.getEncoder().encodeToString((nexusUsername + ":" + nexusPassword).getBytes());
                    conn.setRequestProperty("Authorization", "Basic " + auth);
                }

                conn.setRequestMethod("GET");

                if (conn.getResponseCode() != 200) {
                    throw new IOException("Failed to connect to Nexus: HTTP " + conn.getResponseCode());
                }

                InputStream input = conn.getInputStream();
                ObjectMapper mapper = new ObjectMapper();
                //String abeDebug = new String(input.readAllBytes());
                JsonNode root = mapper.readTree(input);

                for (JsonNode asset : root.get("items")) {
                    String path = asset.get("path").asText();
                    String fileName = path.substring(path.lastIndexOf('/') + 1);
                    fileNames.add(fileName);
                }

                continuationToken = root.has("continuationToken") && !root.get("continuationToken").isNull()
                        ? root.get("continuationToken").asText()
                        : null;

                conn.disconnect();
            } while (continuationToken != null);

            return fileNames;
        }
    }
