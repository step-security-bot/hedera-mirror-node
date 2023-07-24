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

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createTokenInfoWrapperForNonFungibleToken;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createTokenInfoWrapperForToken;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungibleId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.serialNumber;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.token;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenMerkleId;
import static com.hedera.services.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_IS_TOKEN;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.IsTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.UnfreezeTokenPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenPrecompileReadOperationsTest {

    @InjectMocks
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private EvmInfrastructureFactory infrastructureFactory;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private Store store;

    private HTSPrecompiledContract subject;
    private MockedStatic<IsTokenPrecompile> staticIsTokenPrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);
        IsTokenPrecompile isTokenPrecompile =
                new IsTokenPrecompile(syntheticTxnFactory, encoder, evmEncoder, precompilePricingUtils, store);
        PrecompileMapper precompileMapper = new PrecompileMapper(Set.of(isTokenPrecompile));
        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, evmHTSPrecompiledContract);

        staticIsTokenPrecompile = Mockito.mockStatic(IsTokenPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        staticIsTokenPrecompile.close();
    }

    @Test
    void computeCallsCorrectImplementationForIsTokenFungibleToken() {
        // given
        final Bytes pretendArguments =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_IS_TOKEN)), fungibleTokenAddr);
        givenMinimalFrameContext();
        givenMinimalContextForSuccessfulCall();
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        staticIsTokenPrecompile
                .when(() -> IsTokenPrecompile.decodeIsToken(pretendArguments))
                .thenReturn(TokenInfoWrapper.forToken(fungible));
        given(evmEncoder.encodeIsToken(true)).willReturn(successResult);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final var tokenInfoWrapper = createTokenInfoWrapperForToken(fungible);
        given(IsTokenPrecompile.decodeIsToken(any())).willReturn(tokenInfoWrapper);
        var token = new Token(Id.fromGrpcToken(fungible));
        var address = Id.fromGrpcToken(fungible).asEvmAddress();
        given(store.getToken(address, OnMissing.DONT_THROW)).willReturn(token);

        // when
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        final var result = subject.computeInternal(frame);

        // then
        assertEquals(successResult, result);
    }

    @Test
    void computeCallsCorrectImplementationForIsTokenNonFungibleToken() {
        // given
        final Bytes pretendArguments =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_IS_TOKEN)), nonFungibleTokenAddr);
        givenMinimalFrameContext();
        givenMinimalContextForSuccessfulCall();
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        staticIsTokenPrecompile
                .when(() -> IsTokenPrecompile.decodeIsToken(pretendArguments))
                .thenReturn(TokenInfoWrapper.forNonFungibleToken(nonFungible, serialNumber));
        given(evmEncoder.encodeIsToken(true)).willReturn(successResult);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final var tokenInfoWrapper = createTokenInfoWrapperForToken(nonFungible);
        given(IsTokenPrecompile.decodeIsToken(any())).willReturn(tokenInfoWrapper);
        var token = new Token(Id.fromGrpcToken(nonFungible));
        var address = Id.fromGrpcToken(nonFungible).asEvmAddress();
        given(store.getToken(address, OnMissing.DONT_THROW)).willReturn(token);

        // when
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        final var result = subject.computeInternal(frame);

        // then
        assertEquals(successResult, result);
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
    }

    private void givenMinimalContextForSuccessfulCall() {
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getRemainingGas()).willReturn(30000000L);
    }
}
