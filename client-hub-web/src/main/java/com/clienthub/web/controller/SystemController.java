package com.clienthub.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System Configuration", description = "Endpoints for system-wide configuration flags")
public class SystemController {

    @Value("${blockchain.enabled:false}")
    private boolean blockchainEnabled;

    @Operation(summary = "Get system configuration", description = "Returns system flags including feature toggles")
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
            "blockchainEnabled", blockchainEnabled
        ));
    }
}
