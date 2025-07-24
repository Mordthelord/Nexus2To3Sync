package com.upload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

@SuppressWarnings("SameParameterValue")
public class NexusUploaderMaven implements Uploader {


    private final String repoUrl;
    private final String username;
    private final String password;
    private final String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();


    public NexusUploaderMaven(String repoUrl, String username, String password) {
        this.repoUrl = repoUrl;
        this.username = username;
        this.password = password;
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        NexusUploaderMaven nexusUploaderMaven = new NexusUploaderMaven(
                "https://nexus.company.com/service/rest/v1/components?repository=",
                "username",
                "password"
        );

        System.out.println("success? " + nexusUploaderMaven.upload(
                Path.of("C:\\Users\\USER\\Downloads\\artifactID-version-classifier.package"),
                "/file/subfile/artifactID/version/artifactID-version-classifier.package"
        ));
    }

    public boolean upload(Path file, String relativePath) {
        String[] parts = relativePath.split("/");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid Maven path: " + relativePath);
        }

        String version = parts[parts.length - 2];
        String artifactId = parts[parts.length - 3];
        String groupId = String.join(".", Arrays.copyOf(parts, parts.length - 3));

        String filename = parts[parts.length - 1];
        String packaging = filename.substring(filename.lastIndexOf('.') + 1);

        // Determine classifier if present
        String baseName = artifactId + "-" + version;
        String classifier = null;
        if (filename.startsWith(baseName + "-")) {
            String afterBase = filename.substring((baseName + "-").length());
            int dotIdx = afterBase.lastIndexOf('.');
            if (dotIdx > 0) {
                classifier = afterBase.substring(0, dotIdx);
            }
        }
        try {
            byte[] multipartBody = buildMultipartBody(file, groupId, artifactId, version, packaging, classifier);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(repoUrl))
                    .header("Authorization", "Basic " + encodeCredentials(username, password))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            return code >= 200 && code < 300;

        } catch (IOException | InterruptedException ex) {
            System.out.println("Failed to upload");
            ex.printStackTrace();
            return false;
        }
    }

    private byte[] buildMultipartBody(Path jar, String groupId, String artifactId, String version, String packaging, String classifer) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        var writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8), true);

        // Form fields
        writeFormField(writer, "maven2.groupId", groupId);
        writeFormField(writer, "maven2.artifactId", artifactId);
        writeFormField(writer, "maven2.version", version);

        // Binary file part - JAR
        writeFileFieldHeader(writer, "maven2.asset1", jar.getFileName().toString(), "application/java-archive");
        baos.write(Files.readAllBytes(jar));
        writer.write("\r\n");
        if (packaging != null) {
            writeFormField(writer, "maven2.asset1.extension", packaging);
        }
        if (classifer != null) {
            writeFormField(writer, "maven2.asset1.classifier", classifer);
        }

        // Finish boundary
        writer.write("--" + boundary + "--\r\n");
        writer.flush();

        return baos.toByteArray();
    }

    private void writeFormField(PrintWriter writer, String name, String value) {
        writer.write("--" + boundary + "\r\n");
        writer.write("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writer.write(value + "\r\n");
    }

    private void writeFileFieldHeader(PrintWriter writer, String fieldName, String filename, String contentType) {
        writer.write("--" + boundary + "\r\n");
        writer.write("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename + "\"\r\n");
        writer.write("Content-Type: " + contentType + "\r\n\r\n");
        writer.flush(); // important: flush header before writing binary bytes
    }

    private String encodeCredentials(String username, String password) {
        return Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }
}
 