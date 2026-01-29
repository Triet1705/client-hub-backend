package com.clienthub.core.service;

import com.clienthub.core.exception.PdfProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class PdfExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(PdfExtractionService.class);

    /**
     * Extract text content from PDF file with security checks
     * @param file PDF file to extract text from
     * @return Extracted text content
     * @throws PdfProcessingException if file is invalid, encrypted, or scanned
     */
    public String extractTextFromPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PdfProcessingException("PDF file is empty or null");
        }

        if (!"application/pdf".equals(file.getContentType())) {
            throw new PdfProcessingException("Invalid file type. Only PDF is supported. Got: " + file.getContentType());
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            
            if (document.isEncrypted()) {
                throw new PdfProcessingException("Encrypted/Password-protected PDFs are not supported");
            }
            
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            
            String text = stripper.getText(document);
            
            if (text == null || text.trim().isEmpty()) {
                throw new PdfProcessingException(
                    "No text found in PDF. This might be a scanned document (OCR not supported yet)."
                );
            }
            
            logger.debug("Extracted {} chars from {} pages in PDF: {}",
                text.length(), document.getNumberOfPages(), file.getOriginalFilename());
            
            return text.trim();
            
        } catch (IOException e) {
            logger.error("Failed to extract text from PDF: {}", file.getOriginalFilename(), e);
            throw new PdfProcessingException("Failed to extract text from PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Extract text from specific page range with security checks
     * @param file PDF file to extract text from
     * @param startPage Starting page (1-indexed)
     * @param endPage Ending page (inclusive)
     * @return Extracted text from specified pages
     * @throws PdfProcessingException if file is invalid or page range is out of bounds
     */
    public String extractTextFromPages(MultipartFile file, int startPage, int endPage) {
        if (file == null || file.isEmpty()) {
            throw new PdfProcessingException("PDF file is empty or null");
        }

        if (!"application/pdf".equals(file.getContentType())) {
            throw new PdfProcessingException("Invalid file type. Only PDF is supported. Got: " + file.getContentType());
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            
            if (document.isEncrypted()) {
                throw new PdfProcessingException("Encrypted/Password-protected PDFs are not supported");
            }
            
            int totalPages = document.getNumberOfPages();
            if (startPage < 1 || endPage > totalPages || startPage > endPage) {
                throw new PdfProcessingException(
                    String.format("Invalid page range: %d-%d (total pages: %d)", 
                                 startPage, endPage, totalPages));
            }
            
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(startPage);
            stripper.setEndPage(endPage);
            
            String text = stripper.getText(document);
            
            logger.debug("Extracted {} chars from pages {}-{} in PDF: {}",
                text != null ? text.length() : 0, startPage, endPage, file.getOriginalFilename());
            
            return text != null ? text.trim() : "";
            
        } catch (IOException e) {
            logger.error("Failed to extract text from PDF pages: {}", file.getOriginalFilename(), e);
            throw new PdfProcessingException("Failed to extract text from PDF pages: " + e.getMessage(), e);
        }
    }
}
