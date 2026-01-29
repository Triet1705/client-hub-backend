package com.clienthub.core.service;

import com.clienthub.core.domain.enums.TaskPriority;
import com.clienthub.core.dto.ai.TaskDraftDto;
import com.clienthub.core.service.FileStorageService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiTaskService {

    private static final Logger logger = LoggerFactory.getLogger(AiTaskService.class);

    private final PdfExtractionService pdfExtractionService;
    private final FileStorageService fileStorageService;
    private final RestClient ollamaRestClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.ollama.model:llama3.2}")
    private String modelName;

    public AiTaskService(PdfExtractionService pdfExtractionService,
                         FileStorageService fileStorageService,
                         RestClient ollamaRestClient,
                         ObjectMapper objectMapper) {
        this.pdfExtractionService = pdfExtractionService;
        this.fileStorageService = fileStorageService;
        this.ollamaRestClient = ollamaRestClient;
        this.objectMapper = objectMapper;
    }

    public TaskDraftDto extractTaskFromPdf(MultipartFile file) {
        String storedPath = fileStorageService.uploadFile(file, "requirements");
        logger.info("PDF uploaded to storage: {}", storedPath);

        String rawText = pdfExtractionService.extractTextFromPdf(file);

        if(rawText.length() > 15000) {
            rawText = rawText.substring(0, 15000) + "...[truncated]";
        }
        return callOllama(rawText);
    }

    private TaskDraftDto callOllama(String documentText) {
        String systemPrompt = """
            You are a Project Manager Assistant.\s
            Analyze the document text and return a JSON object with:
            - title: Concise summary.
            - description: Detailed requirements.
            - priority: One of [LOW, MEDIUM, HIGH, URGENT]. Default: MEDIUM.
            - estimatedHours: Integer estimate (0 if unknown).
            - confidenceScore: Float 0.0-1.0.
           \s
            Return ONLY raw JSON. No markdown.
           \s""";
        var request = new OllamaRequest(
                modelName,
                systemPrompt + "\n\nTEXT:\n" + documentText,
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
                logger.debug("AI Raw JSON: {}", response.response());
                return objectMapper.readValue(response.response(), TaskDraftDto.class);
            }
        } catch (Exception e) {
            logger.error("AI Service Error: {}", e.getMessage());
        }

        return new TaskDraftDto(
            "Draft Task (AI Unavailable)", 
            "Please review the attached PDF manually.", 
            TaskPriority.LOW,
            0, 
            null,             
            0.0
        );
    }
    public record OllamaRequest(String model, String prompt, boolean stream, String format) {}
    public record OllamaResponse(String model, @JsonProperty("response") String response, boolean done) {}
}
