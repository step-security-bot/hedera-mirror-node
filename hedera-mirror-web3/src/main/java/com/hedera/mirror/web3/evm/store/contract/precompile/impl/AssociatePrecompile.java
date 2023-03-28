package com.hedera.mirror.web3.evm.store.contract.precompile.impl;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class AssociatePrecompile extends AbstractAssociatePrecompile {
    private static final Function ASSOCIATE_TOKEN_FUNCTION = new Function("associateToken(address,address)", INT);
    private static final Bytes ASSOCIATE_TOKEN_SELECTOR = Bytes.wrap(ASSOCIATE_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> ASSOCIATE_TOKEN_DECODER = TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    public AssociatePrecompile(
            final ContractAliases aliases,
            final PrecompilePricingUtils pricingUtils,
            final Provider<FeeCalculator> feeCalculator) {
        super(
                aliases,
                pricingUtils,
                feeCalculator);
    }

    @Override
    public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        associateOp = decodeAssociation(input, aliasResolver);
        transactionBody = syntheticTxnFactory.createAssociate(associateOp);
        return transactionBody;
    }

    @Override
    public long getGasRequirement(long blockTimestamp) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody);
    }

    public static Association decodeAssociation(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments = decodeFunctionCall(input, ASSOCIATE_TOKEN_SELECTOR, ASSOCIATE_TOKEN_DECODER);

        final var accountID = convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(1));

        return Association.singleAssociation(accountID, tokenID);
    }


}
