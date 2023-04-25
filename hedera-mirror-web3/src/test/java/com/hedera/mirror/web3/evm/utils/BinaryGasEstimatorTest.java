package com.hedera.mirror.web3.evm.utils;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;

@ExtendWith(SoftAssertionsExtension.class)
class BinaryGasEstimatorTest {

    @InjectSoftAssertions
    private SoftAssertions softly;

    @Test
    void search(){
        var goal = 20l;
        var low = 25000L;
        var high = 120_000L;

            final var result = BinaryGasEstimator.search(
                    (a, b) -> {} ,gas -> createTxnResult(gas),
                    low,high);

            softly.assertThat(result)
                    .as("value must not go out of bounds")
                    .isBetween(low, high);

    }

    private HederaEvmTransactionProcessingResult createTxnResult(long gasUsed){
        return HederaEvmTransactionProcessingResult.successful(null, gasUsed, 0, 0, null, Address.ZERO);
    }
}
