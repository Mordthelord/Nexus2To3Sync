package com.upload;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

public class Nexus2Crawler {

    private final String basePath;
    private final HttpClient client;

    public Nexus2Crawler(String basePath) {
        this.basePath = basePath;
        this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    }

    public Set<String> crawlNexus() throws IOException, InterruptedException {
        return doCrawlNexus(basePath, new HashSet<>());
    }

    private Set<String> doCrawlNexus(String path, Set<String> files) throws IOException, InterruptedException {

        System.out.println("Crawling: " + path);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(path))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Document doc = Jsoup.parse(response.body());

        // Nexus 2 directory listing has links to files and folders
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.attr("href");
            if (href.equals("../") || href.startsWith("?")) continue;

            String absoluteHref = absolutify(path, href);
            if (href.endsWith("/")) {
                files.addAll(doCrawlNexus(absoluteHref, files));
            } else {
                files.add(absoluteHref);
            }
        }
        return files;
    }

    private static String absolutify(String absolutePrefix, String href) {
        String retVal;
        if (isAbsoluteUrl(href)) {
            retVal = href;
        } else {
            retVal = absolutePrefix + href;
        }
        return retVal;
    }


    private static boolean isAbsoluteUrl(String href) {
        return (href.startsWith("http:") || href.startsWith("https:"));
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Crawling Nexus 2...");
        Nexus2Crawler nexus2Crawler = new Nexus2Crawler("https://nexus.company:8443/content/repositories/repository/file/subfile/artifactID/version/");
        Set<String> nexus2Files = nexus2Crawler.crawlNexus();

        nexus2Files.stream()
                .sorted()
                .forEach(System.out::println);
    }

}
