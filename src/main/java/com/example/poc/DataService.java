package com.example.poc;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class DataService {
    private static final String INDEX_NAME = "data";

    private final RestClient elasticsearch;

    public DataService(
            RestClient.Builder restClientBuilder,
            @Value("${elasticsearch.url}") String elasticsearchUrl,
            @Value("${elasticsearch.username:}") String username,
            @Value("${elasticsearch.password:}") String password
    ) {
        // Build RestClient with optional Basic Auth for Elasticsearch security
        var builder = restClientBuilder.baseUrl(elasticsearchUrl);

        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            String credentials = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials);
        }

        this.elasticsearch = builder.build();
    }

    public SeedDataResponse seedData(List<String> items) {
        if (items == null || items.isEmpty()) {
            items = getDefaultItems();
        }

        List<String> insertedIds = new ArrayList<>();
        for (String item : items) {
            JsonNode response = elasticsearch.post()
                    .uri("/" + INDEX_NAME + "/_doc?refresh=wait_for")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("item", item, "timestamp", System.currentTimeMillis()))
                    .retrieve()
                    .body(JsonNode.class);

            insertedIds.add(response.path("_id").asText());
        }

        return new SeedDataResponse(insertedIds.size(), insertedIds);
    }

    public DataCountResponse getDataCount() {
        JsonNode response = elasticsearch.get()
                .uri("/" + INDEX_NAME + "/_count")
                .retrieve()
                .body(JsonNode.class);

        return new DataCountResponse(response.path("count").asInt());
    }

    private List<String> getDefaultItems() {
        return List.of(
                "OpenShift cluster running successfully",
                "Elasticsearch deployed and ready",
                "Spring Boot application integrated",
                "Data ingestion pipeline working",
                "POC validation complete"
        );
    }

    public record SeedDataResponse(int inserted, List<String> ids) {
    }

    public record DataCountResponse(int count) {
    }
}
