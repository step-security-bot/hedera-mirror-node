/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

package com.hedera.mirror.web3.evm.properties;

import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.EVM_VERSION;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import java.time.Duration;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Setter
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.web3.evm")
public class MirrorNodeEvmProperties implements EvmProperties {
    @Getter
    private boolean allowanceEnabled = false;

    @Getter
    private boolean approvedForAllEnabled = false;

    private boolean directTokenCall = true;

    private boolean dynamicEvmVersion = true;

    @NotBlank
    private String evmVersion = EVM_VERSION;

    @NotBlank
    private String fundingAccount = "0x0000000000000000000000000000000000000062";

    @Min(1)
    @Max(100)
    private int maxGasRefundPercentage = 20;

    @Getter
    @NotNull
    @DurationMin(seconds = 1)
    private Duration expirationCacheTime = Duration.ofMinutes(10L);

    @Getter
    @NotNull
    @DurationMin(seconds = 100)
    private Duration rateLimit = Duration.ofSeconds(100L);

    @Getter
    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;

    @Getter
    private long diffBetweenIterations = 1200L;

    @Override
    public boolean isRedirectTokenCallsEnabled() {
        return directTokenCall;
    }

    @Override
    public boolean dynamicEvmVersion() {
        return dynamicEvmVersion;
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

    @Getter
    @RequiredArgsConstructor
    public enum HederaNetwork {
        MAINNET(unhex("00")),
        TESTNET(unhex("01")),
        PREVIEWNET(unhex("02")),
        OTHER(unhex("03"));

        private final byte[] ledgerId;
    }
}
