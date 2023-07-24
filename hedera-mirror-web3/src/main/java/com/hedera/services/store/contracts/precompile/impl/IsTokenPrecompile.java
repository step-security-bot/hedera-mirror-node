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

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_IS_TOKEN;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmIsTokenPrecompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenAddressResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of IsTokenPrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Removed class fields and adapted constructors in order to achieve stateless behaviour.
 *  2. Body method is not overridden but inherited from AbstractReadOnlyPrecompile.
 *  3. Run method returns TokenAddressResult which contains the token address (we need the address in getSuccessResultFor).
 *  4. getSuccessResultFor gets the token from the store instead of from the ledgers.
 */
public class IsTokenPrecompile extends AbstractReadOnlyPrecompile implements EvmIsTokenPrecompile {

    private final Store store;

    public IsTokenPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils,
            final Store store) {
        super(syntheticTxnFactory, encoder, evmEncoder, pricingUtils);
        this.store = store;
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_IS_TOKEN);
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        final var tokenInfoWrapper = decodeIsToken(frame.getInputData()).token();
        final var tokenAddress = Id.fromGrpcToken(tokenInfoWrapper).asEvmAddress();
        return new TokenAddressResult(tokenAddress);
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        final var readOnlyResult = (TokenAddressResult) runResult;
        final var token = store.getToken(readOnlyResult.tokenAddress(), OnMissing.DONT_THROW);
        final var isToken = !token.isEmptyToken();
        return evmEncoder.encodeIsToken(isToken);
    }

    public static TokenInfoWrapper<TokenID> decodeIsToken(final Bytes input) {
        final var rawTokenInfoWrapper = EvmIsTokenPrecompile.decodeIsToken(input);
        return TokenInfoWrapper.forToken(convertAddressBytesToTokenID(rawTokenInfoWrapper.token()));
    }
}
