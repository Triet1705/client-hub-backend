package com.clienthub.infrastructure.ai;

import com.clienthub.domain.enums.TaskPriority;
import com.clienthub.infrastructure.exception.AiServiceUnavailableException;
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
        finalResult.setProcessingTimeMs((endTime - startTime) / 1_000_000);
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

    private TaskExtractionResult callOllamaWithRetry(String documentText, int retries) {
        // SYSTEM prompt: defines AI role and output schema with CONCRETE examples
        // Kept separate from the user prompt to prevent the model from echoing instructions
        String systemMessage = """
            You are a Project Manager AI. You receive a document and extract actionable tasks from it.
            
            IMPORTANT RULES:
            - Extract tasks ONLY from the document content provided by the user.
            - Do NOT echo these instructions as tasks.
            - Do NOT use placeholder values. Every field must reflect the actual document.
            - If the document has 5 real requirements, return 5 tasks. If it has 1, return 1.
            
            You MUST respond with ONLY valid JSON matching this exact schema:
            {
              "documentSummary": "<a 1-2 sentence summary of what the document is about>",
              "overallConfidence": <float between 0.0 and 1.0>,
              "tasks": [
                {
                  "title": "<short actionable title from the document>",
                  "description": "<detailed description of what needs to be done>",
                  "priority": "<one of: LOW, MEDIUM, HIGH, URGENT>",
                  "estimatedHours": <integer>,
                  "confidenceScore": <float between 0.0 and 1.0>
                }
              ]
            }
            
            EXAMPLE — if the document says "Build a login page with OAuth2 and add a dashboard with charts":
            {
              "documentSummary": "Web application requiring authentication and analytics dashboard",
              "overallConfidence": 0.85,
              "tasks": [
                {
                  "title": "Implement login page with OAuth2 authentication",
                  "description": "Build a login page supporting OAuth2 providers for user authentication",
                  "priority": "HIGH",
                  "estimatedHours": 8,
                  "confidenceScore": 0.9
                },
                {
                  "title": "Build analytics dashboard with charts",
                  "description": "Create a dashboard page displaying project analytics using chart components",
                  "priority": "MEDIUM",
                  "estimatedHours": 12,
                  "confidenceScore": 0.8
                }
              ]
            }
            
            Return ONLY the JSON. No markdown fences, no explanation text.""";

        // USER prompt: contains only the document text
        String userPrompt = "Analyze the following document and extract all actionable tasks:\n\n" + documentText;

        var request = new OllamaRequest(
                modelName,
                userPrompt,
                systemMessage,
                false,
                "json"
        );

        int maxAttempts = retries + 1;
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
                throw new AiServiceUnavailableException("AI Service is unreachable", e);
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

        String systemMessage = """
            You are a Quality Reviewer. You compare AI-generated tasks against the original document.
            
            RULES:
            - Add any requirements from the document that are MISSING from the task list.
            - Remove any DUPLICATE or overly vague tasks.
            - Improve task titles and descriptions to be more specific.
            - Return the corrected result as valid JSON with the same schema.
            
            Return ONLY valid JSON. No markdown fences, no explanation.""";

        String userPrompt = """
            ORIGINAL DOCUMENT:
            %s
            
            CURRENT AI-GENERATED TASKS:
            %s
            
            Review these tasks against the document. Fix any missing, duplicate, or vague tasks. Return corrected JSON."""
            .formatted(truncatedOriginal, existingTasksJson);

        var request = new OllamaRequest(
                modelName,
                userPrompt,
                systemMessage,
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

    // Uses Ollama's separate "system" field so the model properly distinguishes role from input
    public record OllamaRequest(String model, String prompt, String system, boolean stream, String format) {}
    public record OllamaResponse(String model, @JsonProperty("response") String response, boolean done) {}
}
