package com.bcorp.InFlightKv.controller;

import com.bcorp.InFlightKv.service.ClusterService;
import com.bcorp.InFlightKv.service.KeyRoutingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for cluster key routing
 */
@RestController
@RequestMapping("/api/cluster")
public class ClusterController {

    @Autowired
    private ClusterService clusterService;

    /**
     * Route a key to determine which node should handle it
     */
    @GetMapping("/route/{key}")
    public ResponseEntity<KeyRoutingResult> routeKey(@PathVariable String key) {
        try {
            KeyRoutingResult result = clusterService.routeKey(key);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
