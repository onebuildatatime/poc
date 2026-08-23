import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElasticsearchIT {
    private static final String INDEX_NAME = "junit-poc";
    private static final String ELASTICSEARCH_URL = System.getenv()
            .getOrDefault("ELASTICSEARCH_URL", "http://localhost:9200");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private boolean elasticsearchAvailable;

    @AfterEach
    void deleteIndex() throws Exception {
        if (elasticsearchAvailable) {
            send("DELETE", "/" + INDEX_NAME, null, 200, 404);
        }
    }

    @Test
    void indexesAndSearchesForRahul() throws Exception {
        // Check if Elasticsearch is available
        try {
            send("GET", "/", null, 200);
            elasticsearchAvailable = true;
        } catch (ConnectException e) {
            Assumptions.abort("Elasticsearch not available at " + ELASTICSEARCH_URL + ". Skipping integration test.");
        }
        send("DELETE", "/" + INDEX_NAME, null, 200, 404);
        send("PUT", "/" + INDEX_NAME, "{}", 200);
        send(
                "POST",
                "/" + INDEX_NAME + "/_doc?refresh=wait_for",
                "{\"message\":\"Hello Rahul\"}",
                201
        );

        HttpResponse<String> searchResponse = send(
                "GET",
                "/" + INDEX_NAME + "/_search?q=Rahul",
                null,
                200
        );
        JsonNode hits = OBJECT_MAPPER.readTree(searchResponse.body()).path("hits").path("hits");

        assertEquals(1, hits.size());
        assertEquals("Hello Rahul", hits.get(0).path("_source").path("message").asText());
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String body,
            int... expectedStatuses
    ) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher bodyPublisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(ELASTICSEARCH_URL + path))
                .header("Content-Type", "application/json")
                .method(method, bodyPublisher)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        for (int expectedStatus : expectedStatuses) {
            if (response.statusCode() == expectedStatus) {
                return response;
            }
        }

        throw new AssertionError(
                method + " " + path + " returned " + response.statusCode() + ": " + response.body()
        );
    }
}
