package com.upload;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class Nexus2To3Sync {

    private final Predicate<String> urlFilter;
    private final String nexus2RepositoryBase;
    private final String nexus2RepositoryName;
    private final String nexus3RepositoryBase;
    private final String nexus3RepositoryName;
    private final String nexus3RestApiBase;
    private final Function<String, String> urlStripper;
    private final Uploader uploader;

    private static final String NEXUS3_USERNAME = "username"; // or your username
    private static final String NEXUS3_PASSWORD = "password"; // or your password

    private final HttpClient client;

    private static int numSkips, numSuccessfulUploads, numHackedUploads, numFailedUploads;
    static List<String> failedUploads = new LinkedList<>();

    private static final Predicate<String> MAVEN2_FILTER = url -> !url.endsWith(".sha1") && !url.endsWith(".md5") && !url.endsWith(".xml");
    private static final Predicate<String> NUGET_FILTER = url -> true;


    public Nexus2To3Sync(Predicate<String> urlFilter, String nexus2RepositoryBase, String nexus2RepositoryName, String nexus3RepositoryBase, String nexus3RepositoryName, String nexus3RestApiBase, Function<String, String> urlStripper, Uploader uploader) {
        this.urlFilter = urlFilter;
        this.nexus2RepositoryBase = nexus2RepositoryBase;
        this.nexus2RepositoryName = nexus2RepositoryName;
        this.nexus3RepositoryBase = nexus3RepositoryBase;
        this.nexus3RepositoryName = nexus3RepositoryName;
        this.nexus3RestApiBase = nexus3RestApiBase;
        this.urlStripper = urlStripper;
        this.uploader = uploader;

        this.client = HttpClient.newHttpClient();
        this.client.followRedirects();
    }

    public static void main(String[] args) throws Exception {

        Nexus2To3Sync instance = makeInstance(args);
        Set<String> allFiles = instance.crawlNexus2(); // crawl root path

        //chance allFiles from absolute URLs to relative URLs so they can be concatenated with the Nexus 3 base
        Set<String> strippedUrls = instance.getStrippedNexus2Urls(allFiles);

        Set<String> relativePaths = new TreeSet<>();
        for (String url : strippedUrls) {
            if (instance.urlFilter.test(url)) {
                relativePaths.add(url);
            }
        }

        for (String relativePath : relativePaths) {
            System.out.println("Checking: " + relativePath);
            if (instance.existsInNexus3(relativePath)) {
                System.out.println("Exists in Nexus 3, skipping.");
                numSkips++;
            } else {
                System.out.println("Missing in Nexus 3, syncing...");
                Path downloadedFile = instance.downloadFromNexus2(relativePath);
                instance.uploadToNexus3(downloadedFile, relativePath.replaceAll("/Artifact", "/Artifact"));
                Files.delete(downloadedFile);
            }
        }

        System.out.println("\n\nResults:\n");
        System.out.println("Num skips:  " + numSkips);
        System.out.println("Num successful uploads:  " + numSuccessfulUploads);
        System.out.println("Num hacked uploads:  " + numHackedUploads);
        System.out.println("Num failed uploads:  " + numFailedUploads);
        System.out.println("\nHere are the failed uploads:\n\n");
        failedUploads.forEach(System.out::println);
    }

    private static Nexus2To3Sync makeInstance(String[] args) {
        final String repositoryFormat = args[0]; // e.g. "maven2" or "nuget"
        final String nexus2RepositoryBase = args[1]; // e.g. "https://nexus.company.com:8443/content/repositories/"
        final String nexus2RepositoryName = args[2]; // e.g. "Repository"
        final String nexus3RepositoryBase = args[3]; // e.g. "https://nexus.company.com/repository/"
        final String nexus3RepositoryName = args[4]; // e.g. "Repository"
        final String nexus3RestApiBase = args[5]; // e.g. "https://nexus.company.com/service/rest/v1/components?repository="

        final Predicate<String> urlFilter;
        final Function<String, String> urlStripper;
        final Uploader uploader;

        String restApiEndpoint = nexus3RestApiBase + nexus3RepositoryName;
        if ("maven2".equals(repositoryFormat)) {
            urlFilter = MAVEN2_FILTER;
            urlStripper = Function.identity();
            uploader = new NexusUploaderMaven(restApiEndpoint, NEXUS3_USERNAME, NEXUS3_PASSWORD);
        } else if ("nuget".equals(repositoryFormat)) {
            urlFilter = NUGET_FILTER;
            urlStripper = url -> url.substring(0, url.lastIndexOf("/"));
            uploader = new NexusUploaderNuget(restApiEndpoint);
        } else {
            throw new IllegalArgumentException("only maven2 and nuget are supported repository formats");
        }

        return new Nexus2To3Sync(
                urlFilter,
                nexus2RepositoryBase,
                nexus2RepositoryName,
                nexus3RepositoryBase,
                nexus3RepositoryName,
                nexus3RestApiBase,
                urlStripper,
                uploader);
    }

    private String repoPath() {
        return this.nexus2RepositoryBase + this.nexus2RepositoryName;
    }

    private Set<String> getStrippedNexus2Urls(Set<String> absoluteUrls) {
        String repoPath = repoPath();
        Set<String> strippedNexus2Urls = new HashSet<>();
        for (String nexus2Url : absoluteUrls) {
            if (nexus2Url.startsWith(repoPath)) {
                String strippedUrl = nexus2Url.substring(repoPath().length());
                strippedNexus2Urls.add(strippedUrl);
            }
        }
        return strippedNexus2Urls;
    }

    // Recursively crawl Nexus 2 directory listings for files
    private Set<String> crawlNexus2() throws IOException, InterruptedException {
        return new Nexus2Crawler(repoPath()).crawlNexus();
    }

    // Check if a file exists in Nexus 3 using REST API
    private boolean existsInNexus3(String relativePath) throws IOException, InterruptedException {
        // Simplified: check existence by querying components with groupId, artifactId, version, filename?
        // Nexus 3 REST API search by component uses GAV coordinates, but here we have just the path.
        // Let's do a HEAD request to the raw URL in Nexus 3 to check existence (simpler).

        String url = this.urlStripper.apply(this.nexus3RepositoryBase + this.nexus3RepositoryName + relativePath);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                // HEAD requests might not work... maybe HEAD doesn't work properly if the cert is invalid
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .header("Authorization", basicAuth(NEXUS3_USERNAME, NEXUS3_PASSWORD))
                .build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        return response.statusCode() == 200;
    }

    // Download a file from Nexus 2 to local temp folder
    private Path downloadFromNexus2(String relativePath) throws IOException, InterruptedException {
        String url = this.nexus2RepositoryBase + this.nexus2RepositoryName + relativePath;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("failed to download, response code: " + code);
        }

        Path tempFile = Files.createTempFile("nexus2-", "-" + Paths.get(relativePath).getFileName());
        Files.copy(response.body(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Downloaded " + relativePath + " to " + tempFile);
        return tempFile;
    }

    // Upload the file to Nexus 3 using REST API
    private void uploadToNexus3(Path file, String relativePath) {

         boolean success = this.uploader.upload(file, relativePath);

        if (success) {
            System.out.println("Uploaded " + relativePath + " successfully.");
            numSuccessfulUploads++;
        } else if (relativePath.contains("/Artifact-") || relativePath.contains("/Artifact")) {
            System.out.println("Considering " + relativePath + " as 'hacked', even though we got a bad response.  This artifact has a history of a letter changing its case, which Nexus has problems with, but it's probably fine.");
            numHackedUploads++;
        } else {
            System.err.println("Failed to upload " + relativePath);
            failedUploads.add(relativePath);
            numFailedUploads++;
        }
    }
    // Helper: basic auth header
    private static String basicAuth(String user, String pass) {
        String cred = user + ":" + pass;
        return "Basic " + Base64.getEncoder().encodeToString(cred.getBytes());
    }
}
