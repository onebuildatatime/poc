package com.example.poc;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/data")
public class DataController {
    private final DataService dataService;

    public DataController(DataService dataService) {
        this.dataService = dataService;
    }

    @PostMapping("/seed")
    @ResponseStatus(HttpStatus.CREATED)
    public DataService.SeedDataResponse seedData(@RequestBody(required = false) SeedRequest request) {
        List<String> items = (request != null && request.items() != null)
                ? request.items()
                : null;
        return dataService.seedData(items);
    }

    @GetMapping("/count")
    public DataService.DataCountResponse getDataCount() {
        return dataService.getDataCount();
    }

    public record SeedRequest(List<String> items) {
    }
}
