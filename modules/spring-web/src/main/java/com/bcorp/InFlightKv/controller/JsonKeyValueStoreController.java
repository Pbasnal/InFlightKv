package com.bcorp.InFlightKv.controller;

import com.bcorp.InFlightKv.pojos.CacheError;
import com.bcorp.InFlightKv.pojos.CacheResponse;
import com.bcorp.InFlightKv.service.ClusterKeyService;
import com.bcorp.InFlightKv.service.ClusterService;
import com.bcorp.InFlightKv.service.KeyRoutingResult;
import com.bcorp.InFlightKv.service.KeyValueStoreService;
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
    public Mono<ResponseEntity<String>> getAllKeys(@RequestParam(required = false) boolean skipOtherNodes) {
        return Mono.fromCallable(() -> clusterKeyService.getAllKeysFromCluster(skipOtherNodes)
                        .thenApply(this::formatAsNDJSON))
                .flatMap(Mono::fromFuture);
    }

    private ResponseEntity<String> formatAsNDJSON(List<ClusterKeyService.KeyNodeInfo> keyNodeInfos) {
        StringBuilder ndjson = new StringBuilder();
        for (ClusterKeyService.KeyNodeInfo info : keyNodeInfos) {
            ndjson.append("{\"key\":\"")
                  .append(escapeJsonString(info.key()))
                  .append("\",\"node\":\"")
                  .append(escapeJsonString(info.node()))
                  .append("\"}\n");
        }
        return ResponseEntity.ok()
                .header("Content-Type", "application/x-ndjson")
                .body(ndjson.toString());
    }

    private String escapeJsonString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
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
