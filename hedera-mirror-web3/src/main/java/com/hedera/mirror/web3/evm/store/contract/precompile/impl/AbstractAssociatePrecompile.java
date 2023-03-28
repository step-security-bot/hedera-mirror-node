package com.hedera.mirror.web3.evm.store.contract.precompile.impl;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.store.contract.precompile.Precompile;

import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class AbstractAssociatePrecompile implements Precompile {
    private static final String ASSOCIATE_FAILURE_MESSAGE = "Invalid full prefix for associate precompile!";
//    private final WorldLedgers ledgers;
    private final ContractAliases aliases;
//    private final EvmSigsVerifier sigsVerifier;
//    private final SideEffectsTracker sideEffects;
//    private final InfrastructureFactory infrastructureFactory;
    protected final PrecompilePricingUtils pricingUtils;
    protected TransactionBody.Builder transactionBody;
    protected Association associateOp;
//    protected final SyntheticTxnFactory syntheticTxnFactory;
    protected final Provider<FeeCalculator> feeCalculator;

    protected AbstractAssociatePrecompile(
//            final WorldLedgers ledgers,
            final ContractAliases aliases,
//            final EvmSigsVerifier sigsVerifier,
//            final SideEffectsTracker sideEffects,
//            final SyntheticTxnFactory syntheticTxnFactory,
//            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final Provider<FeeCalculator> feeCalculator) {
//        this.ledgers = ledgers;
        this.aliases = aliases;
//        this.sigsVerifier = sigsVerifier;
//        this.sideEffects = sideEffects;
        this.pricingUtils = pricingUtils;
//        this.syntheticTxnFactory = syntheticTxnFactory;
//        this.infrastructureFactory = infrastructureFactory;
        this.feeCalculator = feeCalculator;
    }

    @Override
    public void run(final MessageFrame frame) {
        // --- Check required signatures ---
        final var accountId =
                Id.fromGrpcAccount(Objects.requireNonNull(associateOp).accountId());
        final var hasRequiredSigs = KeyActivationUtils.validateKey(
                frame, accountId.asEvmAddress(), sigsVerifier::hasActiveKey, ledgers, aliases);
        validateTrue(hasRequiredSigs, INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE, ASSOCIATE_FAILURE_MESSAGE);

        // --- Build the necessary infrastructure to execute the transaction ---
        final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
        final var tokenStore = infrastructureFactory.newTokenStore(
                accountStore, sideEffects, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());

        // --- Execute the transaction and capture its results ---
        final var associateLogic = infrastructureFactory.newAssociateLogic(accountStore, tokenStore);
        final var validity = associateLogic.validateSyntax(transactionBody.build());
        validateTrue(validity == OK, validity);
        associateLogic.associate(accountId, associateOp.tokenIds());
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        return pricingUtils.getMinimumPriceInTinybars(ASSOCIATE, consensusTime);
    }

}
