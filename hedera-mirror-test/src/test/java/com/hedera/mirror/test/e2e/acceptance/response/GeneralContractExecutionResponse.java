package com.hedera.mirror.test.e2e.acceptance.response;

/**
 * This is a class that represents a response either from the consensus node (with initialized transactionId,
 * networkResponse, errorMessage) or from the mirror node (with initialized contractCallResponse).
 * This is needed for the NetworkAdapter logic.
 */
public record GeneralContractExecutionResponse(String transactionId, NetworkTransactionResponse networkResponse, String errorMessage, ContractCallResponse contractCallResponse) {
    public GeneralContractExecutionResponse(ContractCallResponse contractCallResponse) {
        this(null, null, null, contractCallResponse);
    }

    public GeneralContractExecutionResponse(String transactionId, NetworkTransactionResponse networkResponse, String errorMessage) {
        this(transactionId, networkResponse, errorMessage, null);
    }
}
