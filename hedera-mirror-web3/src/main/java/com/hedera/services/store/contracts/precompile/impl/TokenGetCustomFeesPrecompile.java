/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;

import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenGetCustomFeesWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmTokenGetCustomFeesPrecompile;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.CustomFeesResult;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class TokenGetCustomFeesPrecompile extends AbstractReadOnlyPrecompile
        implements EvmTokenGetCustomFeesPrecompile {

    public TokenGetCustomFeesPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils) {
        super(syntheticTxnFactory, encoder, evmEncoder, pricingUtils);
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();

        final var tokenGetCustomFeesWrapper = decodeTokenGetCustomFees(frame.getInputData());
        final var tokenID = tokenGetCustomFeesWrapper.token();
        final var tokenAddress = Id.fromGrpcToken(tokenID).asEvmAddress();
        final var token = store.getToken(tokenAddress, OnMissing.DONT_THROW);
        final var customFeees = token.getCustomFees();

        return new CustomFeesResult(customFeees);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES);
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        final var customFeesResult = (CustomFeesResult) runResult;
        final var customFees = customFeesResult.customFees();
        validateTrue(customFees != null, ResponseCodeEnum.INVALID_TOKEN_ID);

        return evmEncoder.encodeTokenGetCustomFees(customFees);
    }

    public static TokenGetCustomFeesWrapper<TokenID> decodeTokenGetCustomFees(final Bytes input) {
        final var rawTokenGetCustomFeesWrapper = EvmTokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(input);
        return new TokenGetCustomFeesWrapper<>(convertAddressBytesToTokenID(rawTokenGetCustomFeesWrapper.token()));
    }
}
