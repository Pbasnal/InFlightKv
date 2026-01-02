package com.bcorp.InFlightKv.controller;

import com.bcorp.InFlightKv.service.ClusterService;
import com.bcorp.InFlightKv.service.KeyRoutingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cluster")
public class ClusterController {

    @Autowired
    private ClusterService clusterService;

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
