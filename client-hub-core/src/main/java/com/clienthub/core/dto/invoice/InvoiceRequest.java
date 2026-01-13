package com.clienthub.core.dto.invoice;

import com.clienthub.core.domain.enums.PaymentMethod;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.UUID;

public class InvoiceRequest {

    @NotBlank(message = "Invoice title is required")
    private String title;

    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Amount must be greater than 0")
    private BigInteger amount;

    @NotNull(message = "Due date is required")
    @Future(message = "Due date must be in the future")
    private LocalDate dueDate;

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotNull(message = "Client ID is required")
    private UUID clientId;

    private PaymentMethod paymentMethod;

    private String clientWalletAddress;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigInteger getAmount() {
        return amount;
    }

    public void setAmount(BigInteger amount) {
        this.amount = amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getClientWalletAddress() {
        return clientWalletAddress;
    }

    public void setClientWalletAddress(String clientWalletAddress) {
        this.clientWalletAddress = clientWalletAddress;
    }

    @Override
    public String toString() {
        return "InvoiceRequest{" +
                "title='" + title + '\'' +
                ", amount=" + amount +
                ", dueDate=" + dueDate +
                ", projectId=" + projectId +
                ", clientId=" + clientId +
                ", paymentMethod=" + paymentMethod +
                '}';
    }
}
