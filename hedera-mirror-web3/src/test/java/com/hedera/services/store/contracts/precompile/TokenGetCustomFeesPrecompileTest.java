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

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidTokenIdResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.payer;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenMerkleAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenMerkleId;
import static com.hedera.services.store.contracts.precompile.impl.TokenGetCustomFeesPrecompile.decodeTokenGetCustomFees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenGetCustomFeesWrapper;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.TokenGetCustomFeesPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
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
class TokenGetCustomFeesPrecompileTest {

    @InjectMocks
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private EvmInfrastructureFactory infrastructureFactory;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private Store store;

    @Mock
    private HederaEvmContractAliases hederaEvmContractAliases;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    @Mock
    private TransactionBody.Builder transactionBody;

    @Mock
    private Token token;

    private static final Bytes GET_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT =
            Bytes.fromHexString("0xae7611a000000000000000000000000000000000000000000000000000000000000003ee");

    private static final Bytes GET_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT =
            Bytes.fromHexString("0xae7611a000000000000000000000000000000000000000000000000000000000000003f6");
    private HTSPrecompiledContract subject;
    private MockedStatic<TokenGetCustomFeesPrecompile> staticTokenGetCustomFeesPrecompile;

    @BeforeEach
    void setUp() {
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        TokenGetCustomFeesPrecompile tokenGetCustomFeesPrecompile =
                new TokenGetCustomFeesPrecompile(syntheticTxnFactory, encoder, evmEncoder, precompilePricingUtils);
        PrecompileMapper precompileMapper = new PrecompileMapper(Set.of(tokenGetCustomFeesPrecompile));
        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, evmHTSPrecompiledContract);

        staticTokenGetCustomFeesPrecompile = Mockito.mockStatic(TokenGetCustomFeesPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        staticTokenGetCustomFeesPrecompile.close();
    }

    @Test
    void getTokenCustomFeesWorks() {
        givenMinimalFrameContext();
        givenReadOnlyFeeSchedule();

        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenCustomFeesWrapper = new TokenGetCustomFeesWrapper<>(tokenMerkleId);
        final var fractionalFee = customFees();
        final Bytes pretendArguments =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_CUSTOM_FEES)), tokenMerkleAddress);

        staticTokenGetCustomFeesPrecompile
                .when(() -> decodeTokenGetCustomFees(pretendArguments))
                .thenReturn(tokenCustomFeesWrapper);
        given(store.getToken(tokenMerkleAddress, OnMissing.DONT_THROW)).willReturn(token);
        given(token.getCustomFees()).willReturn(fractionalFee);
        given(evmEncoder.encodeTokenGetCustomFees(fractionalFee)).willReturn(successResult);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(transactionBody);
        given(TokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(any())).willReturn(tokenCustomFeesWrapper);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile()
                .getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, store, hederaEvmContractAliases);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void getTokenCustomFeesMissingTokenIdFails() {
        givenMinimalFrameContext();
        givenReadOnlyFeeSchedule();

        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenCustomFeesWrapper = new TokenGetCustomFeesWrapper<>(tokenMerkleId);
        final var fractionalFee = customFees();
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_CUSTOM_FEES)),
                EntityIdUtils.asTypedEvmAddress(tokenMerkleId));

        staticTokenGetCustomFeesPrecompile
                .when(() -> decodeTokenGetCustomFees(pretendArguments))
                .thenReturn(tokenCustomFeesWrapper);
        given(store.getToken(tokenMerkleAddress, OnMissing.DONT_THROW)).willReturn(token);
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(transactionBody);
        given(TokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(any())).willReturn(tokenCustomFeesWrapper);
        given(token.getCustomFees()).willReturn(fractionalFee);
        given(evmEncoder.encodeTokenGetCustomFees(fractionalFee)).willReturn(invalidTokenIdResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile()
                .getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, store, hederaEvmContractAliases);
        final var result = subject.computeInternal(frame);

        // then:.
        assertEquals(invalidTokenIdResult, result);
    }

    @Test
    void decodeGetFungibleTokenCustomFeesInput() {
        staticTokenGetCustomFeesPrecompile
                .when(() -> decodeTokenGetCustomFees(GET_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT))
                .thenCallRealMethod();

        final var decodedInput = decodeTokenGetCustomFees(GET_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT);

        assertTrue(decodedInput.token().getTokenNum() > 0);
    }

    @Test
    void decodeGetNonFungibleTokenCustomFeesInput() {
        staticTokenGetCustomFeesPrecompile
                .when(() -> decodeTokenGetCustomFees(GET_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT))
                .thenCallRealMethod();
        final var decodedInput = decodeTokenGetCustomFees(GET_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT);

        assertTrue(decodedInput.token().getTokenNum() > 0);
    }

    private void givenMinimalFrameContext() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(100_000L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        given(worldUpdater.getStore()).willReturn(store);
    }

    private void givenReadOnlyFeeSchedule() {
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
    }

    private List<com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee> customFees() {
        com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee fractionalFee =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee(
                        1L, 2L, 10_000L, 400_000_000L, false, EntityIdUtils.asTypedEvmAddress(payer));

        com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee customFee1 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee1.setFractionalFee(fractionalFee);

        return List.of(customFee1);
    }
}
