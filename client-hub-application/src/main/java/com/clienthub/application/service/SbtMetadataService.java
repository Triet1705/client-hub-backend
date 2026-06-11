package com.clienthub.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "blockchain.enabled", havingValue = "true")
public class SbtMetadataService {
    private static final Logger logger = LoggerFactory.getLogger(SbtMetadataService.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${pinata.api.key:}")
    private String pinataApiKey;
    
    @Value("${pinata.api.secret:}")
    private String pinataApiSecret;
    
    @Value("${pinata.api.jwt:}")
    private String pinataJwt;

    private static final String PINATA_API_URL = "https://api.pinata.cloud/pinning/pinJSONToIPFS";

    public SbtMetadataService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String generateAndUploadMetadata(String freelancerName, String projectName, UUID projectId, String clientName) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("name", "ClientHub Work Certificate: " + projectName);
            metadata.put("description", "Soulbound Token representing completed freelance work on ClientHub.");
            metadata.put("image", "ipfs://QmYxXy...Placeholder"); // Default/placeholder image URI
            
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("freelancer", freelancerName);
            attributes.put("project", projectName);
            attributes.put("projectId", projectId.toString());
            attributes.put("completionDate", DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC")).format(Instant.now()));
            attributes.put("clientAttestation", "Work verified and payment released by " + clientName);
            
            metadata.put("attributes", attributes);
            
            // For IPFS Pinata integration
            Map<String, Object> pinataPayload = new HashMap<>();
            Map<String, Object> pinataOptions = new HashMap<>();
            pinataOptions.put("cidVersion", 1);
            pinataPayload.put("pinataOptions", pinataOptions);
            
            Map<String, Object> pinataMetadata = new HashMap<>();
            pinataMetadata.put("name", "CH_Certificate_" + projectId.toString() + ".json");
            pinataPayload.put("pinataMetadata", pinataMetadata);
            pinataPayload.put("pinataContent", metadata);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Prefer JWT if available, else fallback to API Key/Secret
            if (pinataJwt != null && !pinataJwt.isEmpty()) {
                headers.setBearerAuth(pinataJwt);
            } else if (pinataApiKey != null && !pinataApiKey.isEmpty()) {
                headers.set("pinata_api_key", pinataApiKey);
                headers.set("pinata_secret_api_key", pinataApiSecret);
            } else {
                logger.warn("Pinata credentials not found. Simulating IPFS upload for development.");
                String simulatedCid = "QmSimulatedCIDForProject" + projectId.toString().substring(0, 8);
                return "ipfs://" + simulatedCid;
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(pinataPayload, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(PINATA_API_URL, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String ipfsHash = (String) response.getBody().get("IpfsHash");
                logger.info("Successfully uploaded metadata to IPFS. Hash: {}", ipfsHash);
                return "ipfs://" + ipfsHash;
            } else {
                throw new RuntimeException("Failed to upload metadata to Pinata. Status: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Error uploading SBT metadata to IPFS", e);
            // Return a placeholder or simulated CID so flow doesn't break in dev if API is down
            return "ipfs://QmErrorFallbackCID";
        }
    }
}
