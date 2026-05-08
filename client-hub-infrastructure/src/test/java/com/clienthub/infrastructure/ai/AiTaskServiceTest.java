package com.clienthub.infrastructure.ai;

import com.clienthub.domain.enums.TaskPriority;

import com.clienthub.infrastructure.storage.FileStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiTaskServiceTest {

    @Mock
    private PdfExtractionService pdfExtractionService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient ollamaRestClient;

    @Mock
    private ObjectMapper objectMapper;

    private AiTaskService aiTaskService;
    private MultipartFile mockFile;

    @BeforeEach
    void setUp() {
        aiTaskService = new AiTaskService(pdfExtractionService, fileStorageService, ollamaRestClient, objectMapper);
        
        ReflectionTestUtils.setField(aiTaskService, "modelName", "llama3.2");
        ReflectionTestUtils.setField(aiTaskService, "maxDocumentChars", 15000);
        ReflectionTestUtils.setField(aiTaskService, "maxRetries", 2);
        ReflectionTestUtils.setField(aiTaskService, "confidenceThreshold", 0.65);

        mockFile = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());
        when(pdfExtractionService.extractTextFromPdf(any())).thenReturn("Test document content");
    }

    private void mockOllamaCall(AiTaskService.OllamaResponse response) {
        when(ollamaRestClient.post()
            .uri("/api/generate")
            .body(any(AiTaskService.OllamaRequest.class))
            .retrieve()
            .body(AiTaskService.OllamaResponse.class))
            .thenReturn(response);
        clearInvocations(ollamaRestClient);
    }

    private void mockOllamaCallSequence(AiTaskService.OllamaResponse... responses) {
        org.mockito.stubbing.OngoingStubbing<AiTaskService.OllamaResponse> stub = 
            when(ollamaRestClient.post()
                .uri("/api/generate")
                .body(any(AiTaskService.OllamaRequest.class))
                .retrieve()
                .body(AiTaskService.OllamaResponse.class));
        for (AiTaskService.OllamaResponse response : responses) {
            stub = stub.thenReturn(response);
        }
        clearInvocations(ollamaRestClient);
    }

    @Test
    void shouldExtractMultipleTasksFromValidJson() throws JsonProcessingException {
        AiTaskService.OllamaResponse response = new AiTaskService.OllamaResponse("llama3.2", "valid json", true);
        mockOllamaCall(response);

        TaskDraftDto task1 = new TaskDraftDto("Task 1", "Desc 1", TaskPriority.MEDIUM, 2, null, 0.9);
        TaskDraftDto task2 = new TaskDraftDto("Task 2", "Desc 2", TaskPriority.HIGH, 3, null, 0.85);
        TaskExtractionResult mockResult = new TaskExtractionResult("Summary", 0.8, false, 0, List.of(task1, task2));
        
        when(objectMapper.readValue("valid json", TaskExtractionResult.class)).thenReturn(mockResult);

        TaskExtractionResult result = aiTaskService.extractTaskFromPdf(mockFile);

        assertNotNull(result);
        assertEquals(2, result.getTasks().size());
        assertEquals("Summary", result.getDocumentSummary());
        assertFalse(result.isReviewPassTriggered());
        verify(ollamaRestClient, times(1)).post();
    }

    @Test
    void shouldRetryOnMalformedJson() throws JsonProcessingException {
        AiTaskService.OllamaResponse badResponse = new AiTaskService.OllamaResponse("llama3.2", "bad json", true);
        AiTaskService.OllamaResponse goodResponse = new AiTaskService.OllamaResponse("llama3.2", "good json", true);
        mockOllamaCallSequence(badResponse, goodResponse);

        when(objectMapper.readValue("bad json", TaskExtractionResult.class)).thenThrow(new JsonProcessingException("parse error") {});
        
        TaskDraftDto task1 = new TaskDraftDto("Task 1", "Desc 1", TaskPriority.MEDIUM, 2, null, 0.9);
        TaskExtractionResult mockResult = new TaskExtractionResult("Summary", 0.8, false, 0, List.of(task1));
        when(objectMapper.readValue("good json", TaskExtractionResult.class)).thenReturn(mockResult);

        TaskExtractionResult result = aiTaskService.extractTaskFromPdf(mockFile);

        assertNotNull(result);
        assertEquals(1, result.getTasks().size());
        verify(ollamaRestClient, times(2)).post();
    }

    @Test
    void shouldFallbackAfterAllRetriesExhausted() throws JsonProcessingException {
        AiTaskService.OllamaResponse badResponse = new AiTaskService.OllamaResponse("llama3.2", "bad json", true);
        mockOllamaCall(badResponse);
        when(objectMapper.readValue("bad json", TaskExtractionResult.class)).thenThrow(new JsonProcessingException("parse error") {});

        TaskExtractionResult result = aiTaskService.extractTaskFromPdf(mockFile);

        assertNotNull(result);
        assertEquals(1, result.getTasks().size());
        assertEquals(0.0, result.getOverallConfidence());
        assertTrue(result.getTasks().get(0).getTitle().contains("Unavailable"));
        verify(ollamaRestClient, times(3)).post(); // 1 initial + 2 retries
    }

    @Test
    void shouldTriggerReviewPassOnLowConfidence() throws JsonProcessingException {
        AiTaskService.OllamaResponse lowConfResponse = new AiTaskService.OllamaResponse("llama3.2", "low conf json", true);
        AiTaskService.OllamaResponse reviewResponse = new AiTaskService.OllamaResponse("llama3.2", "review json", true);
        mockOllamaCallSequence(lowConfResponse, reviewResponse);

        TaskDraftDto task1 = new TaskDraftDto("Task 1", "Desc 1", TaskPriority.MEDIUM, 2, null, 0.4);
        TaskExtractionResult primaryResult = new TaskExtractionResult("Summary", 0.4, false, 0, List.of(task1));
        when(objectMapper.readValue("low conf json", TaskExtractionResult.class)).thenReturn(primaryResult);
        
        TaskExtractionResult reviewedResult = new TaskExtractionResult("Summary", 0.8, true, 0, List.of(task1));
        when(objectMapper.readValue("review json", TaskExtractionResult.class)).thenReturn(reviewedResult);

        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        TaskExtractionResult result = aiTaskService.extractTaskFromPdf(mockFile);

        assertNotNull(result);
        assertTrue(result.isReviewPassTriggered());
        verify(ollamaRestClient, times(2)).post(); // 1 primary + 1 review
    }

    @Test
    void shouldSkipReviewPassOnHighConfidence() throws JsonProcessingException {
        AiTaskService.OllamaResponse highConfResponse = new AiTaskService.OllamaResponse("llama3.2", "high conf json", true);
        mockOllamaCall(highConfResponse);

        TaskDraftDto task1 = new TaskDraftDto("Task 1", "Desc 1", TaskPriority.MEDIUM, 2, null, 0.9);
        TaskExtractionResult primaryResult = new TaskExtractionResult("Summary", 0.8, false, 0, List.of(task1));
        when(objectMapper.readValue("high conf json", TaskExtractionResult.class)).thenReturn(primaryResult);

        TaskExtractionResult result = aiTaskService.extractTaskFromPdf(mockFile);

        assertNotNull(result);
        assertFalse(result.isReviewPassTriggered());
        verify(ollamaRestClient, times(1)).post();
    }

    @Test
    void shouldTruncateDocumentIntelligently() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            sb.append("A");
            if (i % 100 == 0) sb.append("\n\n");
        }
        String longText = sb.toString();
        
        when(pdfExtractionService.extractTextFromPdf(any())).thenReturn(longText);
        mockOllamaCall(new AiTaskService.OllamaResponse("llama3.2", "json", true));
        
        TaskDraftDto task1 = new TaskDraftDto("Task 1", "Desc 1", TaskPriority.MEDIUM, 2, null, 0.9);
        when(objectMapper.readValue(anyString(), eq(TaskExtractionResult.class)))
            .thenReturn(new TaskExtractionResult("Summary", 0.8, false, 0, List.of(task1)));

        aiTaskService.extractTaskFromPdf(mockFile);

        verify(ollamaRestClient.post().uri("/api/generate"), times(1)).body(argThat((Object req) -> {
            if (req instanceof AiTaskService.OllamaRequest request) {
                return request.prompt().length() < 20000 && request.prompt().contains("[...middle section summarized...]");
            }
            return false;
        }));
    }

    @Test
    void shouldThrowWhenOllamaUnavailable() {
        when(ollamaRestClient.post()
            .uri("/api/generate")
            .body(any(AiTaskService.OllamaRequest.class))
            .retrieve())
            .thenThrow(new RestClientException("Connection refused"));

        assertThrows(com.clienthub.infrastructure.exception.AiServiceUnavailableException.class, () -> aiTaskService.extractTaskFromPdf(mockFile));
    }
}
