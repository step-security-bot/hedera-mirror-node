package com.hedera.mirror.web3.evm.store.contract.precompile;

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

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.ERROR_DECODING_PRECOMPILE_INPUT;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils.isViewFunction;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.google.common.annotations.VisibleForTesting;

import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;

import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

public class MirrorHTSPrecompiledContract extends EvmHTSPrecompiledContract {
    private static final Logger log = LogManager.getLogger(MirrorHTSPrecompiledContract.class);

    private static final PrecompileContractResult NO_RESULT =
            new PrecompileContractResult(null, true, MessageFrame.State.COMPLETED_FAILED, Optional.empty());

    private static final Bytes STATIC_CALL_REVERT_REASON = Bytes.of("HTS precompiles are not static".getBytes());
    private static final String NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON = "Invalid operation for ERC-20 token!";
    private static final String NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON = "Invalid operation for ERC-721 token!";
    public static final String URI_QUERY_NON_EXISTING_TOKEN_ERROR = "ERC721Metadata: URI query for nonexistent token";

    private final EncodingFacade encoder;
    private final EvmEncodingFacade evmEncoder;
    private final MirrorNodeProperties mirrorNodeProperties;

    private Precompile precompile;
    private final Provider<FeeCalculator> feeCalculator;
    private long gasRequirement = 0;
    private final PrecompilePricingUtils precompilePricingUtils;
    private Address senderAddress;
    private HederaStackedWorldStateUpdater updater;

    public MirrorHTSPrecompiledContract(
            final EvmInfrastructureFactory infrastructureFactory,
            final MirrorNodeProperties mirrorNodeProperties,
            final GasCalculator gasCalculator,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final Provider<FeeCalculator> feeCalculator) {
        super(infrastructureFactory);
        this.encoder = encoder;
        this.evmEncoder = evmEncoder;
        this.mirrorNodeProperties = mirrorNodeProperties;
        this.feeCalculator = feeCalculator;
        this.currentView = currentView;
        this.precompilePricingUtils = precompilePricingUtils;
    }

    @Override
    public Pair<Long, Bytes> computeCosted(
            final Bytes input,
            final MessageFrame frame,
            final ViewGasCalculator viewGasCalculator,
            final TokenAccessor tokenAccessor) {
        if (frame.isStatic()) {
            if (!isTokenProxyRedirect(input) && !isViewFunction(input)) {
                frame.setRevertReason(STATIC_CALL_REVERT_REASON);
                return Pair.of(defaultGas(), null);
            }

            final var proxyUpdater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
            if (!proxyUpdater.isInTransaction()) {
                return super.computeCosted(
                        input,
                        frame,
                        precompilePricingUtils::computeViewFunctionGas,
                        //yanko pr
                        new TokenAccessorImpl(
                                proxyUpdater.trackingLedgers(),
                                //mirror properties check if ledgerid is available if not to be added
                                //evmproperties
                                currentView.getNetworkInfo().ledgerId(),
                                proxyUpdater::unaliased));
            }
        }
        final var result = computePrecompile(input, frame);
        return Pair.of(gasRequirement, result.getOutput());
    }

    @Override
    public String getName() {
        return "MirrorHTS";
    }

    @Override
    public long gasRequirement(final Bytes bytes) {
        return gasRequirement;
    }

    @NonNull
    @Override
    public PrecompileContractResult computePrecompile(final Bytes input, @NonNull final MessageFrame frame) {
        prepareFields(frame);
        //tqnata implementaciq na unaliased
        prepareComputation(input, updater::unaliased);

        gasRequirement = defaultGas();
        if (this.precompile == null || this.transactionBody == null) {
            frame.setExceptionalHaltReason(Optional.of(ERROR_DECODING_PRECOMPILE_INPUT));
            return NO_RESULT;
        }

        final var now = frame.getBlockValues().getTimestamp();
        gasRequirement = precompile.getGasRequirement(now);
        final Bytes result = computeInternal(frame);

        return result == null
                ? PrecompiledContract.PrecompileContractResult.halt(null, Optional.of(ExceptionalHaltReason.NONE))
                : PrecompiledContract.PrecompileContractResult.success(result);
    }

    void prepareFields(final MessageFrame frame) {
        //tqnata pr
//        this.updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
//        this.sideEffectsTracker = infrastructureFactory.newSideEffects();
//        this.ledgers = updater.wrappedTrackingLedgers(sideEffectsTracker);

//        final var unaliasedSenderAddress =
//                updater.permissivelyUnaliased(frame.getSenderAddress().toArray());
//        this.senderAddress = Address.wrap(Bytes.of(unaliasedSenderAddress));
    }

    void prepareComputation(Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        this.precompile = null;

        final int functionId = input.getInt(0);
        this.gasRequirement = 0L;

        this.precompile = switch (functionId) {
            case AbiConstants.ABI_ID_ASSOCIATE_TOKEN -> new AssociatePrecompile(
                    updater.aliases(),
                    precompilePricingUtils,
                    feeCalculator);

            default -> null;};
        if (precompile != null) {
            decodeInput(input, aliasResolver);
        }
    }

    /* --- Helpers --- */
    void decodeInput(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        try {
            this.precompile.body(input, aliasResolver);
        } catch (final Exception e) {
            // check is there a way to throw exception which to be catched from the controller
        }
    }

    private Precompile checkNFT(final boolean isFungible, final Supplier<Precompile> precompileSupplier) {
        if (isFungible) {
            throw new InvalidTransactionException(NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
        } else {
            return precompileSupplier.get();
        }
    }

    private Precompile checkFungible(final boolean isFungible, final Supplier<Precompile> precompileSupplier) {
        if (!isFungible) {
            throw new InvalidTransactionException(NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
        } else {
            return precompileSupplier.get();
        }
    }

    private Precompile checkFeatureFlag(final boolean featureFlag, final Supplier<Precompile> precompileSupplier) {
        if (!featureFlag) {
            throw new InvalidTransactionException(NOT_SUPPORTED);
        } else {
            return precompileSupplier.get();
        }
    }

    @SuppressWarnings("rawtypes")
    protected Bytes computeInternal(final MessageFrame frame) {
        Bytes result;
        try {
            validateTrue(frame.getRemainingGas() >= gasRequirement, INSUFFICIENT_GAS);

            precompile.handleSentHbars(frame);
            precompile.run(frame);

            // As in HederaLedger.commit(), we must first commit the ledgers before creating our
            // synthetic record, as the ledger interceptors will populate the sideEffectsTracker
            //ostava no nqma da e kym ledgerite , a kym state na david
//            ledgers.commit();

            //waie vanko pr this method is changed
            result = precompile.getSuccessResultFor();
        } catch (final ResourceLimitException e) {
            // we want to propagate ResourceLimitException, so it is handled
            // in {@code EvmTxProcessor.execute()} as expected
            throw e;
        } catch (final InvalidTransactionException e) {
            final var status = e.getResponseCode();
            result = precompile.getFailureResultFor(status);

            if (e.isReverting()) {
                frame.setState(MessageFrame.State.REVERT);
                frame.setRevertReason(e.getRevertReason());
            }
        } catch (final Exception e) {
            log.warn("Internal precompile failure", e);
            result = precompile.getFailureResultFor(FAIL_INVALID);
        }

        // This should always have a parent stacked updater
        final var parentUpdater = updater.parentUpdater();
        if (!parentUpdater.isPresent()) {
            throw new InvalidTransactionException(FAIL_INVALID, "EvmHTS precompile frame had no parent updater");
        }

        return result;
    }

    private long defaultGas() {
        return mirrorNodeProperties.htsDefaultGasCost();
    }

    @VisibleForTesting
    public Precompile getPrecompile() {
        return precompile;
    }
}
