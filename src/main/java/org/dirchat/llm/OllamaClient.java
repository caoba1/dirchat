// Communicates with Ollama API
package org.dirchat.llm;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class OllamaClient {

    private final String baseUrl;
    private final String modelName;

    public OllamaClient(String baseUrl, String modelName) {
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    public String generate(String prompt) {
        try {
            URL url = new URL(baseUrl + "/api/generate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String payload = String.format("{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false}", modelName, prompt);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes());
                os.flush();
            }

            Scanner scanner = new Scanner(conn.getInputStream());
            StringBuilder response = new StringBuilder();
            while (scanner.hasNextLine()) {
                response.append(scanner.nextLine());
            }
            scanner.close();

            return response.toString(); // You can parse JSON if needed
        } catch (org.dirchat.util.SliteErrorException se) {
            org.dirchat.util.LogMessages.logWarning(se);
            System.err.println("SLITE error communicating with Ollama: " + se.getMessage());
            return null;
        } catch (Exception e) {
            org.dirchat.util.LogMessages.logWarning(e);
            System.err.println("Error communicating with Ollama: " + e.getMessage());
            return null;
        }
    }
}
