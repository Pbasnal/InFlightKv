package InFlightKv.controller;

import com.bcorp.CacheError;
import com.bcorp.CacheResponse;
import com.bcorp.api.KeyValueStoreEngine;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/kv")
public class JsonKeyValueStoreController {

    private final KeyValueStoreEngine kvEngine;

    public JsonKeyValueStoreController(KeyValueStoreEngine kvEngine) {
        this.kvEngine = kvEngine;
    }

    @GetMapping("/{key}")
    public Mono<ResponseEntity<CacheResponse<String>>> get(@PathVariable String key) {
        return Mono.fromCallable(() -> kvEngine.<String, CacheResponse<String>>getCache(key))
                .map(this::convertToControllerResponse);
    }

    @PutMapping("/{key}")
    public Mono<ResponseEntity<CacheResponse<String>>> put(@PathVariable String key,
                                                           @RequestBody Mono<String> jsonBody,
                                                           @RequestParam(required = false) Long ifVersion) {
        return jsonBody.map(strBody -> kvEngine.<String, String, CacheResponse<String>>setCache(key, strBody, ifVersion))
                .map(this::convertToControllerResponse);
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
