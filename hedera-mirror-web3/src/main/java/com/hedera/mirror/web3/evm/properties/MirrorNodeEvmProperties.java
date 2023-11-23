/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.evm.properties;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes32;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

@Setter
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.web3.evm")
public class MirrorNodeEvmProperties implements EvmProperties {

    @Getter
    private boolean allowTreasuryToOwnNfts = true;

    @NotNull
    private Set<EntityType> autoRenewTargetTypes = new HashSet<>();

    @Getter
    @Positive
    private long estimateGasIterationThreshold = 7300L;

    private boolean directTokenCall = true;

    private boolean dynamicEvmVersion = true;

    @Min(1)
    private long exchangeRateGasReq = 100;

    @NotBlank
    private String evmVersion = EVM_VERSION;

    @Getter
    @NotNull
    private EvmSpecVersion evmSpecVersion = EvmSpecVersion.SHANGHAI;

    @Getter
    @NotNull
    @DurationMin(seconds = 1)
    private Duration expirationCacheTime = Duration.ofMinutes(10L);

    @NotBlank
    private String fundingAccount = "0x0000000000000000000000000000000000000062";

    @Getter
    private long htsDefaultGasCost = 10000;

    @Getter
    private boolean limitTokenAssociations = false;

    @Getter
    @Min(1)
    private long maxAutoRenewDuration = 10000L;

    @Getter
    @Min(1)
    private int maxBatchSizeBurn = 10;

    @Getter
    @Min(1)
    private int maxBatchSizeMint = 10;

    @Getter
    @Min(1)
    private int maxBatchSizeWipe = 10;

    private int maxCustomFeesAllowed = 10;

    // maximum iteration count for estimate gas' search algorithm
    @Getter
    private int maxGasEstimateRetriesCount = 20;

    // used by eth_estimateGas only
    @Min(1)
    @Max(100)
    private int maxGasRefundPercentage = 100;

    @Getter
    @Min(1)
    private int maxMemoUtf8Bytes = 100;

    @Getter
    private int maxNftMetadataBytes = 100;

    @Getter
    @Min(1)
    private int maxTokenNameUtf8Bytes = 10;

    @Getter
    @Min(1)
    private int maxTokensPerAccount = 1000;

    @Getter
    @Min(1)
    private int maxTokenSymbolUtf8Bytes = 10;

    @Getter
    @Min(1)
    private long minAutoRenewDuration = 1000L;

    @Getter
    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;

    @Getter
    @NotNull
    @DurationMin(seconds = 100)
    private Duration rateLimit = Duration.ofSeconds(100L);

    @Getter
    private Map<String, Long> systemPrecompiles = new HashMap<>();

    @Getter
    private TreeMap<String, Long> evmVersions = new TreeMap<>();

    @Getter
    private Map<String, List<String>> evmPrecompiles;

    public boolean shouldAutoRenewAccounts() {
        return autoRenewTargetTypes.contains(EntityType.ACCOUNT);
    }

    public boolean shouldAutoRenewContracts() {
        return autoRenewTargetTypes.contains(EntityType.CONTRACT);
    }

    public boolean shouldAutoRenewSomeEntityType() {
        return !autoRenewTargetTypes.isEmpty();
    }

    @Override
    public boolean isRedirectTokenCallsEnabled() {
        return directTokenCall;
    }

    @Override
    public boolean isLazyCreationEnabled() {
        return true;
    }

    @Override
    public boolean isCreate2Enabled() {
        return true;
    }

    @Override
    public boolean dynamicEvmVersion() {
        return dynamicEvmVersion;
    }

    @Override
    public Bytes32 chainIdBytes32() {
        return network.getChainId();
    }

    @Override
    public String evmVersion() {
        return evmVersion;
    }

    @Override
    public Address fundingAccountAddress() {
        return Address.fromHexString(fundingAccount);
    }

    @Override
    public int maxGasRefundPercentage() {
        return maxGasRefundPercentage;
    }

    public int maxCustomFeesAllowed() {
        return maxCustomFeesAllowed;
    }

    public long exchangeRateGasReq() {
        return exchangeRateGasReq;
    }

    @Getter
    @RequiredArgsConstructor
    public enum HederaNetwork {
        MAINNET(unhex("00"), Bytes32.fromHexString("0x0127")),
        TESTNET(unhex("01"), Bytes32.fromHexString("0x0128")),
        PREVIEWNET(unhex("02"), Bytes32.fromHexString("0x0129")),
        OTHER(unhex("03"), Bytes32.fromHexString("0x012A"));

        private final byte[] ledgerId;
        private final Bytes32 chainId;
    }

    /**
     * Determines the suitable EVM version based on the given block number.
     * Returns the highest version whose block number is less than or equal to the input block number.
     *
     * @param blockNumber The block number to check.
     * @return The key of the suitable EVM version for the given block number, or null if none found.
     */
    public String getEvmVersionForBlock(Long blockNumber) {
        String closestVersion = evmVersions.firstEntry().getKey(); // Default to the lowest version

        for (Map.Entry<String, Long> entry : evmVersions.entrySet()) {
            // If the block number of the version is greater than the block number we are looking for,
            // break the loop as we have found the closest version.
            if (entry.getValue() > blockNumber) {
                break;
            }
            closestVersion = entry.getKey();
        }

        return closestVersion;
    }

    /**
     * Determines the precompiles available at the specified block number based on the
     * evm version that is used at the specified block timestamp
     * Returns the list of available precompiles at the specified block
     *
     * @param blockNumber The block number for which to search
     * @param evmVersion The evm version that will be used
     * @return List of precompiles available at the specified block number
     */
    public List<String> getPrecompilesAvailableAtBlock(Long blockNumber, String evmVersion) {
        if (CollectionUtils.isEmpty(evmPrecompiles)) {
            return new ArrayList<>();
        }
        List<String> precompilesForEvm = evmPrecompiles.get(evmVersion);
        return precompilesForEvm.stream()
                .filter(p -> (systemPrecompiles.get(p) <= blockNumber))
                .toList();
    }
}
