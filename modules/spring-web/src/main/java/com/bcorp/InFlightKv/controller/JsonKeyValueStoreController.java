package com.bcorp.InFlightKv.controller;

import com.bcorp.InFlightKv.pojos.CacheError;
import com.bcorp.InFlightKv.pojos.CacheResponse;
import com.bcorp.InFlightKv.service.ClusterKeyService;
import com.bcorp.InFlightKv.service.ClusterService;
import com.bcorp.InFlightKv.service.KeyRoutingResult;
import com.bcorp.InFlightKv.service.KeyValueStoreService;
import com.bcorp.pojos.DataKey;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/kv")
public class JsonKeyValueStoreController {

    private final KeyValueStoreService keyValueStoreService;
    private final ClusterService clusterService;
    private final ClusterKeyService clusterKeyService;

    public JsonKeyValueStoreController(KeyValueStoreService keyValueStoreService,
                                     ClusterService clusterService,
                                     ClusterKeyService clusterKeyService) {
        this.keyValueStoreService = keyValueStoreService;
        this.clusterService = clusterService;
        this.clusterKeyService = clusterKeyService;
    }

    @GetMapping("/{key}")
    public Mono<ResponseEntity<?>> get(@PathVariable String key) {
        try {
            KeyRoutingResult routing = clusterService.routeKey(key);

            if (routing.isShouldRedirect()) {
                // Redirect to the correct node
                String redirectUrl = routing.getExternalUrl() + "/kv/" + key;
                return Mono.just(ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                        .header("Location", redirectUrl)
                        .build());
            } else {
                // Handle locally
                return Mono.fromCallable(() ->
                                keyValueStoreService.get(key)
                                        .thenApply(this::convertToControllerResponse)
                        )
                        .flatMap(Mono::fromFuture);
            }
        } catch (Exception e) {
            return Mono.just(ResponseEntity.internalServerError().build());
        }
    }

    @PutMapping("/{key}")
    public Mono<ResponseEntity<?>> put(@PathVariable String key,
                                       @RequestBody Mono<String> jsonBody,
                                       @RequestParam(required = false) Long ifVersion) {
        try {
            KeyRoutingResult routing = clusterService.routeKey(key);

            if (routing.isShouldRedirect()) {
                // Redirect to the correct node
                return jsonBody.map(strBody -> {
                    String redirectUrl = routing.getExternalUrl() + "/kv/" + key +
                            (ifVersion != null ? "?ifVersion=" + ifVersion : "");
                    return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                            .header("Location", redirectUrl)
                            .header("X-Redirect-Reason", "Key belongs to different node")
                            .build();
                });
            } else {
                // Handle locally
                return jsonBody.map(strBody ->
                                keyValueStoreService.set(key, strBody, ifVersion, false)
                                        .thenApply(this::convertToControllerResponse)
                        )
                        .flatMap(Mono::fromFuture);
            }
        } catch (Exception e) {
            return Mono.just(ResponseEntity.internalServerError().build());
        }
    }

    @PatchMapping("/{key}")
    public Mono<ResponseEntity<?>> patch(@PathVariable String key,
                                         @RequestBody Mono<String> jsonBody,
                                         @RequestParam(required = false) Long ifVersion) {
        try {
            KeyRoutingResult routing = clusterService.routeKey(key);

            if (routing.isShouldRedirect()) {
                // Redirect to the correct node
                return jsonBody.map(strBody -> {
                    String redirectUrl = routing.getExternalUrl() + "/kv/" + key +
                            (ifVersion != null ? "?ifVersion=" + ifVersion : "");
                    return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                            .header("Location", redirectUrl)
                            .header("X-Redirect-Reason", "Key belongs to different node")
                            .build();
                });
            } else {
                // Handle locally
                return jsonBody
                        .map(strBody ->
                                keyValueStoreService.set(key, strBody, ifVersion, true)
                                        .thenApply(this::convertToControllerResponse)
                        )
                        .flatMap(Mono::fromFuture);
            }
        } catch (Exception e) {
            return Mono.just(ResponseEntity.internalServerError().build());
        }
    }

    @DeleteMapping("/{key}")
    public Mono<ResponseEntity<?>> delete(@PathVariable String key) {
        try {
            KeyRoutingResult routing = clusterService.routeKey(key);

            if (routing.isShouldRedirect()) {
                // Redirect to the correct node
                String redirectUrl = routing.getExternalUrl() + "/kv/" + key;
                return Mono.just(ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                        .header("Location", redirectUrl)
                        .header("X-Redirect-Reason", "Key belongs to different node")
                        .build());
            } else {
                // Handle locally
                return Mono.fromCallable(() ->
                                keyValueStoreService.remove(key)
                                        .thenApply(this::convertToControllerResponse)
                        )
                        .flatMap(Mono::fromFuture);
            }
        } catch (Exception e) {
            return Mono.just(ResponseEntity.internalServerError().build());
        }
    }

    @GetMapping("")
    public Mono<ResponseEntity<List<DataKey>>> getAllKeys() {
        return Mono.fromCallable(() -> clusterKeyService.getAllKeysFromCluster()
                        .thenApply(ResponseEntity::ok))
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
