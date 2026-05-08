package com.clienthub.infrastructure.ai;

import com.clienthub.domain.enums.TaskPriority;

import com.clienthub.infrastructure.storage.FileStorageService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class AiTaskService {

    private static final Logger logger = LoggerFactory.getLogger(AiTaskService.class);

    private final PdfExtractionService pdfExtractionService;
    private final FileStorageService fileStorageService;
    private final RestClient ollamaRestClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.ollama.model:llama3.2}")
    private String modelName;

    @Value("${ai.extraction.max-document-chars:15000}")
    private int maxDocumentChars;

    @Value("${ai.extraction.max-retries:2}")
    private int maxRetries;

    @Value("${ai.extraction.confidence-threshold:0.65}")
    private double confidenceThreshold;

    public AiTaskService(PdfExtractionService pdfExtractionService,
                         FileStorageService fileStorageService,
                         RestClient ollamaRestClient,
                         ObjectMapper objectMapper) {
        this.pdfExtractionService = pdfExtractionService;
        this.fileStorageService = fileStorageService;
        this.ollamaRestClient = ollamaRestClient;
        this.objectMapper = objectMapper;
    }

    public TaskExtractionResult extractTaskFromPdf(MultipartFile file) {
        long startTime = System.nanoTime();
        
        // File archival is best-effort; extraction should not fail if storage is unavailable
        try {
            String storedPath = fileStorageService.uploadFile(file, "requirements");
            logger.info("PDF uploaded to storage: {}", storedPath);
        } catch (Exception e) {
            logger.warn("File storage unavailable, skipping archival: {}", e.getMessage());
        }

        String rawText = pdfExtractionService.extractTextFromPdf(file);
        String truncatedText = intelligentTruncate(rawText, maxDocumentChars);

        TaskExtractionResult primaryResult = callOllamaWithRetry(truncatedText, maxRetries);
        
        TaskExtractionResult finalResult = primaryResult;
        if (primaryResult.getOverallConfidence() < confidenceThreshold && primaryResult.getOverallConfidence() > 0) {
            logger.info("Confidence {} is below threshold {}, triggering review pass", primaryResult.getOverallConfidence(), confidenceThreshold);
            finalResult = callReviewPass(rawText, primaryResult);
        }

        long endTime = System.nanoTime();
        finalResult.setProcessingTimeMs((endTime - startTime) / 1000000);
        return finalResult;
    }

    private String intelligentTruncate(String rawText, int maxChars) {
        if (rawText.length() <= maxChars) {
            return rawText;
        }

        int headSize = (int) (maxChars * 0.25);
        int tailSize = (int) (maxChars * 0.25);
        int midBudget = maxChars - headSize - tailSize;

        String head = rawText.substring(0, headSize);
        String tail = rawText.substring(rawText.length() - tailSize);
        
        String middle = rawText.substring(headSize, rawText.length() - tailSize);
        int lastParagraphBreak = middle.lastIndexOf("\n\n", midBudget);
        String truncatedMiddle = lastParagraphBreak > 0 
            ? middle.substring(0, lastParagraphBreak) 
            : middle.substring(0, midBudget);

        return head + "\n\n[...middle section summarized...]\n\n" + truncatedMiddle + "\n\n" + tail;
    }

    private TaskExtractionResult callOllamaWithRetry(String documentText, int maxRetries) {
        String systemPrompt = """
            You are a Project Manager Assistant. 
            STEP 1: Read the document and identify ALL distinct actionable requirements.
            STEP 2: For each requirement, create a separate task object.
            STEP 3: Review your task list — ensure no requirement is missing and no tasks are duplicated.
            
            Return ONLY raw JSON with the following schema:
            {
              "documentSummary": "Concise overview of the document",
              "overallConfidence": 0.0 to 1.0 float,
              "tasks": [
                {
                  "title": "Concise summary",
                  "description": "Detailed requirements",
                  "priority": "One of [LOW, MEDIUM, HIGH, URGENT]. Default: MEDIUM",
                  "estimatedHours": Integer estimate (0 if unknown),
                  "confidenceScore": 0.0 to 1.0 float
                }
              ]
            }
            """;
        
        var request = new OllamaRequest(
                modelName,
                systemPrompt + "\n\nTEXT:\n" + documentText,
                false,
                "json"
        );

        int maxAttempts = maxRetries + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                OllamaResponse response = ollamaRestClient.post()
                        .uri("/api/generate")
                        .body(request)
                        .retrieve()
                        .body(OllamaResponse.class);

                if (response != null && response.response() != null) {
                    logger.debug("AI Raw JSON (Attempt {}): {}", attempt, response.response());
                    TaskExtractionResult result = objectMapper.readValue(response.response(), TaskExtractionResult.class);
                    
                    if (result.getTasks() == null || result.getTasks().isEmpty()) {
                        logger.warn("Attempt {}: AI returned empty task list, retrying", attempt);
                        continue;
                    }
                    
                    boolean valid = true;
                    for (TaskDraftDto task : result.getTasks()) {
                        if (task.getTitle() == null || task.getTitle().length() < 3) {
                            logger.warn("Attempt {}: Task title too short or null: {}", attempt, task.getTitle());
                            valid = false;
                            break;
                        }
                    }
                    
                    if (!valid) {
                        continue;
                    }
                    
                    return result;
                }
            } catch (JsonProcessingException e) {
                logger.warn("Attempt {}: JSON parse failed — {}", attempt, e.getMessage());
            } catch (RestClientException e) {
                logger.error("AI Service Unavailable: {}", e.getMessage());
                throw new com.clienthub.infrastructure.exception.AiServiceUnavailableException("AI Service is unreachable", e);
            } catch (Exception e) {
                logger.error("AI Service Error on attempt {}: {}", attempt, e.getMessage());
            }
        }
        
        return TaskExtractionResult.fallback("AI could not parse this document after " + maxAttempts + " attempts");
    }

    private TaskExtractionResult callReviewPass(String originalText, TaskExtractionResult primaryResult) {
        String truncatedOriginal = originalText.length() > 8000 ? originalText.substring(0, 8000) : originalText;
        String existingTasksJson = "";
        try {
            existingTasksJson = objectMapper.writeValueAsString(primaryResult.getTasks());
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize tasks for review pass: {}", e.getMessage());
            return primaryResult;
        }

        String reviewPrompt = """
            You are a Quality Reviewer. Compare these AI-generated tasks against the original document.
            
            ORIGINAL DOCUMENT:
            %s
            
            AI-GENERATED TASKS:
            %s
            
            Instructions:
            1. Identify any requirements in the document that are MISSING from the task list.
            2. Identify any tasks that are DUPLICATES or too vague.
            3. Return a corrected JSON object with the same schema, adding missing tasks and removing duplicates.
            
            Return ONLY raw JSON with the following schema:
            {
              "documentSummary": "Concise overview",
              "overallConfidence": 0.0 to 1.0 float,
              "tasks": [
                {
                  "title": "Concise summary",
                  "description": "Detailed requirements",
                  "priority": "One of [LOW, MEDIUM, HIGH, URGENT]. Default: MEDIUM",
                  "estimatedHours": Integer estimate (0 if unknown),
                  "confidenceScore": 0.0 to 1.0 float
                }
              ]
            }
            """.formatted(truncatedOriginal, existingTasksJson);

        var request = new OllamaRequest(
                modelName,
                reviewPrompt,
                false,
                "json"
        );

        try {
            OllamaResponse response = ollamaRestClient.post()
                    .uri("/api/generate")
                    .body(request)
                    .retrieve()
                    .body(OllamaResponse.class);

            if (response != null && response.response() != null) {
                TaskExtractionResult reviewedResult = objectMapper.readValue(response.response(), TaskExtractionResult.class);
                if (reviewedResult.getTasks() != null && !reviewedResult.getTasks().isEmpty()) {
                    reviewedResult.setReviewPassTriggered(true);
                    return reviewedResult;
                }
            }
        } catch (Exception e) {
            logger.warn("Review pass failed: {}", e.getMessage());
        }
        
        return primaryResult;
    }

    public record OllamaRequest(String model, String prompt, boolean stream, String format) {}
    public record OllamaResponse(String model, @JsonProperty("response") String response, boolean done) {}
}
