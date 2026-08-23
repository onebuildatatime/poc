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
public class MessageService {
    private static final String INDEX_NAME = "messages";

    private final RestClient elasticsearch;

    public MessageService(
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

    public MessageController.MessageResponse create(String message) {
        JsonNode response = elasticsearch.post()
                .uri("/" + INDEX_NAME + "/_doc?refresh=wait_for")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", message))
                .retrieve()
                .body(JsonNode.class);

        return new MessageController.MessageResponse(response.path("_id").asText(), message);
    }

    public List<MessageController.MessageResponse> search(String query) {
        JsonNode response = elasticsearch.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/" + INDEX_NAME + "/_search")
                        .queryParam("q", query)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        List<MessageController.MessageResponse> messages = new ArrayList<>();

        for (JsonNode hit : response.path("hits").path("hits")) {
            messages.add(new MessageController.MessageResponse(
                    hit.path("_id").asText(),
                    hit.path("_source").path("message").asText()
            ));
        }

        return messages;
    }
}
