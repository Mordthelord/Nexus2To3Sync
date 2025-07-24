package com.upload;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Base64;

public class NexusUploaderNuget implements Uploader {

    // Default values for quick standalone testing
    private final String restApiEndpoint; // e.g. "https://nexus.company.com/service/rest/v1/components?repository=";
    private static final String DEFAULT_USERNAME = "username";
    private static final String DEFAULT_PASSWORD = "password";

    public NexusUploaderNuget(String restApiEndpoint) {
        this.restApiEndpoint = restApiEndpoint;
    }

    public static void main(String[] args) throws IOException {
        String restApiEndpoint = "https://nexus.company.com/service/rest/v1/components?repository=";
        Path nugetPath = Path.of("C:\\Users\\USER\\Downloads\\artifactID-version.package");
        new NexusUploaderNuget(restApiEndpoint).upload(nugetPath, "This/does/not/matter/for/nuget");
    }

    public boolean upload(Path path, String relativePath) {
        return uploadNuGet(path.toFile(), restApiEndpoint, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    public static boolean uploadNuGet(File nugetFile, String nexusUrl, String username, String password) {
        String boundary = "===" + System.currentTimeMillis() + "===";
        try {
            // Build the multipart body as bytes
            byte[] requestBody = buildMultipartBody("nuget.asset", nugetFile, boundary);

            // Build authorization header
            String authHeader = "Basic " +
                    Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

            // Create HttpRequest
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(nexusUrl))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            // Send request
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response code: " + response.statusCode());
            System.out.println("Response body: " + response.body());

            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ex) {
            System.out.println("Failed to upload NuGet package to Nexus");
            ex.printStackTrace();
            return false;
        }
    }

    /** Helper to build the multipart/form-data body as a byte array */
    private static byte[] buildMultipartBody(String fieldName, File file, String boundary) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String fileName = file.getName();

        // File header
        outputStream.write(("--" + boundary + "\r\n").getBytes());
        outputStream.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n").getBytes());
        outputStream.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes());

        // File content
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        outputStream.write("\r\n".getBytes());

        // End boundary
        outputStream.write(("--" + boundary + "--\r\n").getBytes());

        return outputStream.toByteArray();
    }
}
