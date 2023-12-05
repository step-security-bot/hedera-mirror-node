/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.config;

import static com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract.EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract.EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

@Named
@Getter
public class PrecompilesHolderErcHtsExchangeRate implements PrecompiledContractProvider {

    public final Map<String, PrecompiledContract> hederaPrecompiles;

    PrecompilesHolderErcHtsExchangeRate(
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final GasCalculatorHederaV22 gasCalculator,
            final BasicHbarCentExchange basicHbarCentExchange,
            final HTSPrecompiledContract htsPrecompiledContract) {
        hederaPrecompiles = new HashMap<>();
        hederaPrecompiles.put(EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS, htsPrecompiledContract);
        hederaPrecompiles.put(
                EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS,
                new ExchangeRatePrecompiledContract(
                        gasCalculator, basicHbarCentExchange, mirrorNodeEvmProperties, Instant.now()));
    }
}
