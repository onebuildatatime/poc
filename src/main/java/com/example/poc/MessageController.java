package com.example.poc;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/messages")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse create(@RequestBody CreateMessageRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        return messageService.create(request.message());
    }

    @GetMapping("/search")
    public List<MessageResponse> search(@RequestParam String q) {
        if (q.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q is required");
        }
        return messageService.search(q);
    }

    public record CreateMessageRequest(String message) {
    }

    public record MessageResponse(String id, String message) {
    }
}
