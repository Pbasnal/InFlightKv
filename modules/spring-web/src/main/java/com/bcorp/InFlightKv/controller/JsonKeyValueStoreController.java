package com.bcorp.InFlightKv.controller;

import com.bcorp.InFlightKv.kvengine.CustomCacheRequestMethod;
import com.bcorp.InFlightKv.pojos.CacheError;
import com.bcorp.InFlightKv.pojos.CacheResponse;
import com.bcorp.InFlightKv.service.KeyValueStoreService;
import com.bcorp.api.CacheRequestMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/kv")
public class JsonKeyValueStoreController {

    private final KeyValueStoreService keyValueStoreService;

    public JsonKeyValueStoreController(KeyValueStoreService keyValueStoreService) {
        this.keyValueStoreService = keyValueStoreService;
    }

    @GetMapping("/{key}")
    public Mono<ResponseEntity<CacheResponse<String>>> get(@PathVariable String key) {
        return Mono.fromCallable(() ->
                        keyValueStoreService.get(key)
                                .thenApply(this::convertToControllerResponse)
                )
                .flatMap(Mono::fromFuture);
    }

    @PutMapping("/{key}")
    public Mono<ResponseEntity<CacheResponse<String>>> put(@PathVariable String key,
                                                           @RequestBody Mono<String> jsonBody,
                                                           @RequestParam(required = false) Long ifVersion) {
        return jsonBody.map(strBody ->
                        keyValueStoreService.set(key, strBody, ifVersion, false)
                                .thenApply(this::convertToControllerResponse)
                )
                .flatMap(Mono::fromFuture);
    }

    @PatchMapping("/{key}")
    public Mono<ResponseEntity<CacheResponse<String>>> patch(@PathVariable String key,
                                                             @RequestBody Mono<String> jsonBody,
                                                             @RequestParam(required = false) Long ifVersion) {
        return jsonBody
                .map(strBody ->
                        keyValueStoreService.set(key, strBody, ifVersion, true)
                                .thenApply(this::convertToControllerResponse)
                )
                .flatMap(Mono::fromFuture);
    }

    @DeleteMapping("/{key}")
    public Mono<ResponseEntity<CacheResponse<String>>> delete(@PathVariable String key) {
        return Mono.fromCallable(() ->
                        keyValueStoreService.remove(key)
                                .thenApply(this::convertToControllerResponse)
                )
                .flatMap(Mono::fromFuture);
    }


    private ResponseEntity<CacheResponse<String>> convertToControllerResponse(CacheResponse<String> response) {
        if (response.data() != null) {
            return ResponseEntity.ok(response);
        }
        return handleError(response.error());
    }

    private ResponseEntity<CacheResponse<String>> handleError(CacheError error) {
        return switch (error.errorCode()) {
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            default -> ResponseEntity.internalServerError().build();
        };
    }
}
