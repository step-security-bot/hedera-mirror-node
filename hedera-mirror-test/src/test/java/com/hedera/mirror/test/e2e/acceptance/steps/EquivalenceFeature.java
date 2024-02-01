/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.steps;

import static com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum.ALICE;
import static com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum.BOB;
import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE;
import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.NFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_CALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_DESTRUCT;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_PRECOMPILE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.Selectors.GET_EXCHANGE_RATE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.Selectors.GET_PRNG;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.Selectors.IS_TOKEN;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.hexToAscii;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesStringRightPad;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Strings;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import com.hedera.hashgraph.sdk.proto.ResponseCodeEnum;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.NetworkException;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.GeneralContractExecutionResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.util.TestUtil;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EquivalenceFeature extends AbstractFeature {
    private final TokenClient tokenClient;
    private static final String OBTAINER_SAME_CONTRACT_ID_EXCEPTION = "OBTAINER_SAME_CONTRACT_ID";
    private static final String INVALID_SOLIDITY_ADDRESS_EXCEPTION = "INVALID_SOLIDITY_ADDRESS";
    private static final String INVALID_SIGNATURE = "INVALID_SIGNATURE";
    private static final String INVALID_FEE_SUBMITTED = "INVALID_FEE_SUBMITTED";
    private static final String INVALID_SOLIDITY_ADDRESS = "INVALID_SOLIDITY_ADDRESS";
    private static final String TRANSACTION_SUCCESSFUL_MESSAGE = "Transaction successful";
    private static final String TRANSACTION_FAILED_MESSAGE = "Transaction failed";
    private static final String CONTRACT_REVERTED = "CONTRACT_REVERT_EXECUTED";
    private static final String BAD_REQUEST = "400 Bad Request";
    private static final String INVALID_RECEIVING_NODE_ACCOUNT = "INVALID_RECEIVING_NODE_ACCOUNT";
    private static final String INVALID_ALIAS_KEY = "INVALID_ALIAS_KEY";
    private static final String INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE =
            "INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE";
    private static final String SUCCESS = "SUCCESS";
    private static final String TOKEN_NOT_ASSOCIATED_TO_ACCOUNT = "TOKEN_NOT_ASSOCIATED_TO_ACCOUNT";
    private static final String SPENDER_DOES_NOT_HAVE_ALLOWANCE = "SPENDER_DOES_NOT_HAVE_ALLOWANCE";

    private DeployedContract deployedEquivalenceDestruct;
    private DeployedContract deployedEquivalenceCall;
    private DeployedContract deployedPrecompileContract;

    private String equivalenceDestructContractSolidityAddress;
    private String equivalenceCallContractSolidityAddress;
    private String precompileContractSolidityAddress;
    private final AccountClient accountClient;
    private ExpandedAccountId admin;
    private ExpandedAccountId receiverAccount;
    private ExpandedAccountId secondReceiverAccount;
    private String receiverAccountAlias;
    private String secondReceiverAccountAlias;
    private TokenId fungibleTokenId;
    private TokenId nonFungibleTokenId;

    @Given("I successfully create selfdestruct contract")
    public void createNewSelfDestructContract() throws IOException {
        deployedEquivalenceDestruct = getContract(EQUIVALENCE_DESTRUCT);
        equivalenceDestructContractSolidityAddress =
                deployedEquivalenceDestruct.contractId().toSolidityAddress();
    }

    @Given("I successfully create equivalence call contract")
    public void createNewEquivalenceCallContract() throws IOException {
        deployedEquivalenceCall = getContract(EQUIVALENCE_CALL);
        equivalenceCallContractSolidityAddress =
                deployedEquivalenceCall.contractId().toSolidityAddress();
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        receiverAccount = accountClient.getAccount(AccountClient.AccountNameEnum.BOB);
        receiverAccountAlias = receiverAccount.getPublicKey().toEvmAddress().toString();
        secondReceiverAccount = accountClient.getAccount(ALICE);
        secondReceiverAccountAlias = secondReceiverAccount.getPublicKey().toString();
    }

    @RetryAsserts
    @Given("I verify the equivalence contract bytecode is deployed")
    public void verifyEquivalenceContractDeployed() {
        verifyContractDeployed(equivalenceCallContractSolidityAddress);
    }

    @RetryAsserts
    @Given("I verify the selfdestruct contract bytecode is deployed")
    public void verifyDestructContractDeployed() {
        verifyContractDeployed(equivalenceDestructContractSolidityAddress);
    }

    private void verifyContractDeployed(String contractAddress) {
        var response = mirrorClient.getContractInfo(contractAddress);
        Assertions.assertThat(response.getBytecode()).isNotBlank();
        Assertions.assertThat(response.getRuntimeBytecode()).isNotBlank();
    }
    @Given("I successfully create estimate precompile contract")
    public void createNewEstimatePrecompileContract() throws IOException {
        deployedPrecompileContract = getContract(ESTIMATE_PRECOMPILE);
        precompileContractSolidityAddress =
                deployedPrecompileContract.contractId().toSolidityAddress();
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
    }

    @Given("I successfully create tokens")
    public void createTokens() {
        fungibleTokenId = tokenClient.getToken(FUNGIBLE).tokenId();
        nonFungibleTokenId = tokenClient.getToken(NFT).tokenId();
    }

    @Given("I mint a new nft")
    public void mintNft() {
        networkTransactionResponse =
                tokenClient.mint(nonFungibleTokenId, RandomStringUtils.random(4).getBytes());
    }

    @And("I update the {account} account and token key for contract {string}")
    public void updateAccountAndTokensKeys(AccountNameEnum accountName, String contractName)
            throws PrecheckStatusException, ReceiptStatusException, TimeoutException, IOException {
        ExpandedAccountId account = accountClient.getAccount(accountName);
        DeployedContract contract = getContract(ContractResource.valueOf(contractName));
        var keyList = KeyList.of(account.getPublicKey(), contract.contractId()).setThreshold(1);
        new AccountUpdateTransaction()
                .setAccountId(account.getAccountId())
                .setKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(account.getPrivateKey())
                .execute(accountClient.getClient());
        new TokenUpdateTransaction()
                .setTokenId(fungibleTokenId)
                .setSupplyKey(keyList)
                .setAdminKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(account.getPrivateKey())
                .execute(accountClient.getClient());
        var tokenUpdate = new TokenUpdateTransaction()
                .setTokenId(nonFungibleTokenId)
                .setSupplyKey(keyList)
                .setAdminKey(keyList)
                .freezeWith(accountClient.getClient())
                .sign(account.getPrivateKey())
                .execute(accountClient.getClient());
        networkTransactionResponse = new NetworkTransactionResponse(
                tokenUpdate.transactionId, tokenUpdate.getReceipt(accountClient.getClient()));
    }

    @Then("the mirror node REST API should return status {int} for the HAPI transactions")
    public void verifyMirrorNodeAPIResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    @Then("the mirror node REST API should return status {int} for the contracts creation")
    public void verifyMirrorAPIResponse(int status) {
        if (networkTransactionResponse != null) {
            verifyMirrorTransactionsResponse(mirrorClient, status);
        }
    }

    @Then("I execute selfdestruct and set beneficiary to invalid {string} address")
    public void selfDestructAndSetBeneficiary(String beneficiary) {
        var accountId = new AccountId(extractAccountNumber(beneficiary)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(deployedEquivalenceDestruct, "destroyContract", parameters);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals(INVALID_SOLIDITY_ADDRESS_EXCEPTION, extractedStatus);
    }

    @Then("I execute selfdestruct and set beneficiary to valid {string} address")
    public void selfDestructAndSetBeneficiaryValid(String beneficiary) {
        var accountId = new AccountId(extractAccountNumber(beneficiary)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(deployedEquivalenceDestruct, "destroyContract", parameters);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        assertEquals(TRANSACTION_SUCCESSFUL_MESSAGE, message);
    }

    @Then("I execute selfdestruct and set beneficiary to the deleted contract address")
    public void selfDestructAndSetBeneficiaryToDeletedContract() {
        var parameters = new ContractFunctionParameters().addAddress(equivalenceDestructContractSolidityAddress);
        var message = executeContractCallTransaction(deployedEquivalenceDestruct, "destroyContract", parameters);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals(OBTAINER_SAME_CONTRACT_ID_EXCEPTION, extractedStatus);
    }

    @Then("I execute balance opcode to system account {string} address would return 0")
    public void balanceOfAddress(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "getBalance", parameters);
        assertEquals(new BigInteger("0"), functionResult.getInt256(0));
    }

    @Then("I execute balance opcode against a contract with balance")
    public void balanceOfContract() {
        var parameters = new ContractFunctionParameters().addAddress(equivalenceDestructContractSolidityAddress);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "getBalance", parameters);
        assertEquals(new BigInteger("10000"), functionResult.getInt256(0));
    }

    @Then("I verify extcodesize opcode against a system account {string} address returns 0")
    public void extCodeSizeAgainstSystemAccount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "getCodeSize", parameters);
        assertEquals(new BigInteger("0"), functionResult.getInt256(0));
    }

    @Then("I verify extcodecopy opcode against a system account {string} address returns empty bytes")
    public void extCodeCopyAgainstSystemAccount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "copyCode", parameters);
        assertArrayEquals(new byte[0], functionResult.getBytes(0));
    }

    @Then("I verify extcodehash opcode against a system account {string} address returns empty bytes")
    public void extCodeHashAgainstSystemAccount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "getCodeHash", parameters);
        assertArrayEquals(new byte[0], functionResult.getBytes(0));
    }

    @And("I associate {token} to contract")
    public void associateTokenToContract(TokenNameEnum tokenName) throws InvalidProtocolBufferException {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        tokenClient.associate(deployedEquivalenceCall.contractId(), tokenId);
    }

    @Then("I execute directCall to {string} address without amount")
    public void directCallToZeroAddressWithoutAmount(String address) {
        var contractId = new ContractId(extractAccountNumber(address));
        var message = executeContractCallTransaction(contractId, "destroyContract", null, null);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals("INVALID_CONTRACT_ID", extractedStatus);
    }

    @Then("I execute directCall to {string} address with amount {int}")
    public void directCallToZeroAddressWithAmount(String address, int amount) {
        var contractId = new ContractId(extractAccountNumber(address));
        var message = executeContractCallTransaction(contractId, "destroyContract", null, Hbar.fromTinybars(amount));
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals("INVALID_CONTRACT_ID", extractedStatus);
    }

    @Then("I execute directCall to range {string} addresses via HTS Precompile without amount")
    public void directCallHTSPrecompileWithoutAmount(String beneficiary) {
        var accountId = new AccountId(extractAccountNumber(beneficiary)).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(deployedEquivalenceCall, "destroyContract", parameters, null);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        assertEquals(TRANSACTION_SUCCESSFUL_MESSAGE, message);
    }

    @Then("I execute directCall to range {string} addresses via HTS Precompile with amount")
    public void directCallHTSPrecompileWithAmount(String beneficiary) {
        var accountId = new AccountId(extractAccountNumber(beneficiary)).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(deployedEquivalenceCall, "destroyContract", parameters, null);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        assertEquals(TRANSACTION_SUCCESSFUL_MESSAGE, message);
    }

    @Then("I execute directCall to range {string} addresses without amount")
    public void directCallToRangeAddressesWithoutAmount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(deployedEquivalenceCall, "destroyContract", parameters, null);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals("INVALID_SOLIDITY_ADDRESS", extractedStatus);
    }

    @Then("I execute directCall to range {string} addresses with amount {int}")
    public void directCallToRangeAddressesWithoutAmount(String address, int amount) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(
                deployedEquivalenceCall, "destroyContract", parameters, Hbar.fromTinybars(amount));
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals("INVALID_FEE_SUBMITTED", extractedStatus);
    }

    @Then("I execute EOA call to non existing mirror")
    public void EOACallsNonExistingMirror() {
        var accountId = new AccountId(extractAccountNumber("0.0.1001")).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(deployedEquivalenceCall, "destroyContract", parameters, null);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals("INVALID_FEE_SUBMITTED", extractedStatus);
    }

    @Then("I execute directCall to address {string} with contract without amount")
    public void directCallToAddressWithoutAmount(String beneficiary) {
        var accountId = new AccountId(extractAccountNumber(beneficiary)).toSolidityAddress(); // 0.0.1005
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(deployedEquivalenceCall, "getBalance", parameters, null);
        assertEquals(TRANSACTION_SUCCESSFUL_MESSAGE, message);
    }

    @Then("I execute directCall to address above 1000 with invalid contractID without amount")
    public void directCallToAddressWithoutAmountToInvalidContractID() {
        var contractId = new ContractId(extractAccountNumber("0.0.10000000"));
        var deployedContractID = new DeployedContract(null, contractId, null);
        var message = executeContractCallTransaction(deployedContractID, "makeCallWithoutAmount", null, null);
        var extractedStatus = extractStatus(message);
        assertEquals("INVALID_CONTRACT_ID", extractedStatus);
    }

    @Then("I execute directCall to address {string} with contract with amount {int}")
    // failed contract revert
    public void directCallToAddressWithAmount(String beneficiary, int amount) {
        var accountId = new AccountId(extractAccountNumber(beneficiary)).toSolidityAddress(); // 0.0.1005
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(
                deployedEquivalenceCall, "makeCallWithAmount", parameters, Hbar.fromTinybars(amount));
        assertEquals(TRANSACTION_SUCCESSFUL_MESSAGE, message);
    }

    @Then("I execute directCall to address with non-payable contract with amount {int}")
    public void directCallToAddressWithAmountNonPayable(int amount) {
        var message = executeContractCallTransaction(
                deployedPrecompileContract, "isTokenAddress", null, Hbar.fromTinybars(amount));
        var extractedStatus = extractStatus(message);
        assertEquals("CONTRACT_REVERT_EXECUTED", extractedStatus);
    }

    @Then("I execute directCall to address with contract with amount {int} with receiverSig false")
    public void directCallToAddressWithAmountWithReceiverSigFalse(int amount) {
        var receiverAccountId = new DeployedContract(
                null,
                new ContractId(extractAccountNumber(
                        accountClient.getAccount(ALICE).getAccountId().toString())),
                null);
        var message = executeContractCallTransaction(receiverAccountId, "foo()", null, Hbar.fromTinybars(amount));

        assertEquals(TRANSACTION_SUCCESSFUL_MESSAGE, message);
    }

    @Then("I execute directCall to address with contract with amount {int} with receiverSig true")
    public void directCallToAddressWithAmountWithReceiverSigTrue(int amount) {
        var receiverAccountId = new DeployedContract(
                null,
                new ContractId(extractAccountNumber(
                        accountClient.getAccount(BOB).getAccountId().toString())),
                null);
        var message = executeContractCallTransaction(receiverAccountId, "foo()", null, Hbar.fromTinybars(amount));
        var extractedStatus = extractStatus(message);
        assertEquals(INVALID_SIGNATURE, extractedStatus);
    }

    @Then("I execute directCall to address with contract with amount {int} with hollow account")
    public void directCallToAddressWithAmountWithHollowAccount(int amount) {
        // DirectCall to address(over 0.0.01000) with amount (case Hollow account):
        // Execute directCall with amount against an address that is empty
        var receiverAccountId = new DeployedContract(
                null,
                new ContractId(extractAccountNumber(
                        accountClient.getAccount(BOB).getAccountId().toString())),
                null);
        var message = executeContractCallTransaction(receiverAccountId, "foo()", null, Hbar.fromTinybars(amount));

        assertEquals(TRANSACTION_SUCCESSFUL_MESSAGE, message);
    }

    @Then("I execute directCall to address above 1000 with contract without amount {int}")
    public void directCallToAddressViaContractWithoutAmount(int amount) {
        var contractId = new ContractId(extractAccountNumber("0.0.10000000"));
        var deployedContractID = new DeployedContract(null, contractId, null);
        var message = executeContractCallTransaction(
                deployedContractID, "makeCallWithAmount", null, Hbar.fromTinybars(amount));
        var extractedStatus = extractStatus(message);
        assertEquals("INVALID_CONTRACT_ID", extractedStatus);
    }

    @Then("I execute internal {string} against HTS precompile with isToken function for {token} {string} amount to {node} node")
    public void executeInternalCallForHTSApproveWithAmount(String call, TokenNameEnum tokenName, String amountType, NodeNameEnum node) {
        var callType = getMethodName(call, amountType);
        var tokenId = tokenClient.getToken(tokenName).tokenId();

        var data = encodeDataToByteArray(EQUIVALENCE_CALL, callType, TestUtil.asAddress("0x0000000000000000000000000000000000000167"), encodeDataToByteArray(IS_TOKEN, asAddress(tokenId)));

        if (amountType.equals("with")) {
            final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, Bytes.fromHexString(
                    Strings.encode(data)).trimTrailingZeros().toArray(), Hbar.fromTinybars(100L));
            if (node.equals(NodeNameEnum.CONSENSUS)) {
                var transactionId = response.networkResponse().getTransactionIdStringNoCheckSum();
                var resultMessage = mirrorClient
                        .getTransactions(transactionId)
                        .getTransactions()
                        .get(1)
                        .getResult();
                assertEquals(INVALID_FEE_SUBMITTED, resultMessage);
            } else {
                TupleType tupleType = TupleType.parse("(bool,bytes)");
                var decodedResult = tupleType.decode(ByteString.fromHex(response.contractCallResponse().getResult().substring(2)).toByteArray());
                // TODO: returns result but should fail with INVALID_FEE_SUBMITTED
            }
        } else {
            final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, Bytes.fromHexString(
                    Strings.encode(data)).trimTrailingZeros().toArray(), null);
            if (node.equals(NodeNameEnum.CONSENSUS)) {
                var transactionId = response.networkResponse().getTransactionIdStringNoCheckSum();
                var resultMessage = mirrorClient
                        .getContractActions(transactionId)
                        .getActions()
                        .get(1)
                        .getResultData();
                assertNull(response.errorMessage());
                assertFalse(Bytes.fromHexString(resultMessage).slice(32).isZero());
            } else {
                assertFalse(Bytes.fromHexString(response.contractCallResponse().getResult()).slice(32).isZero());
            }
        }
    }

    @Then("I execute internal {string} against {string} contract {string} amount to {node} node")
    public void executeInternalCallAgainstContract(String call, String payable, String amountType, NodeNameEnum node) {
        var callType = getMethodName(call, amountType);
        DeployedContract receiverContract;
        final var isPayable = payable.equals("payable");
        if (isPayable) {
            receiverContract = deployedEquivalenceDestruct;
        } else {
            receiverContract = deployedEquivalenceCall;
        }

        if (amountType.equals("with")) {
            final var data = encodeDataToByteArray(EQUIVALENCE_CALL, "makeCallWithAmountRevert", TestUtil.asAddress(receiverContract.contractId().toSolidityAddress()), new byte[0]);
            GeneralContractExecutionResponse response;
            try {
                response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, "makeCallWithAmountRevert", data, Hbar.fromTinybars(123L));
                if (isPayable) {
                    assertThat(response.errorMessage()).isBlank();
                }
            } catch (Exception e) {
                if (!isPayable) {
                    if (node.equals(NodeNameEnum.CONSENSUS)) {
                        assertEquals(CONTRACT_REVERTED, extractStatus(e.getMessage()));
                    } else {
                        assertThat(e.getMessage()).contains(BAD_REQUEST);
                    }
                }
            }
        } else {
            byte[] data = encodeDataToByteArray(EQUIVALENCE_CALL, callType, TestUtil.asAddress(receiverContract.contractId().toSolidityAddress()), new byte[0]);
            final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);
            assertThat(response.errorMessage()).isBlank();
        }
    }

    @Then("I execute internal {string} against Ecrecover precompile to {node} node")
    public void executeAllCallsForEcrecover(String calltype, NodeNameEnum node) {
        var messageSignerAddress = "0x05FbA803Be258049A27B820088bab1cAD2058871";
        var hashedMessage =
                "0xf950ac8b7f08b2f5ffa0f893d0f85398135301759b768dc20c1e16d9cdba5b53000000000000000000000000000000000000000000000000000000000000001b45e5f9dc145b79479820a9dfa925bb698333e7f17b7d570391e8487c96a39e07675b682b2519f6232152a9f6f4f5923d171dfb7636daceee2c776edecc6c8b64";

        var callType = getMethodName(calltype, "without");

        var data = encodeDataToByteArray(EQUIVALENCE_CALL, callType, TestUtil.asAddress("0x0000000000000000000000000000000000000001"), Bytes.fromHexString(hashedMessage).toArrayUnsafe());
        final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);

        if (node.equals(NodeNameEnum.CONSENSUS)) {
            var transactionId = response.networkResponse().getTransactionIdStringNoCheckSum();
            var resultMessageSigner = mirrorClient
                    .getContractActions(transactionId)
                    .getActions()
                    .get(1)
                    .getResultData();
            assertEquals(messageSignerAddress, asAddress(resultMessageSigner).toString());
        } else {
            TupleType tupleType = TupleType.parse("(bool,bytes)");
            var decodedResult = tupleType.decode(ByteString.fromHex(response.contractCallResponse().getResult().substring(2)).toByteArray());
            assertEquals(messageSignerAddress, asAddress((byte[]) decodedResult.get(1)).toString());
        }
    }

    @Then("I execute internal {string} against SHA-256 precompile to {node} node")
    public void executeAllCallsForSha256(String calltype, NodeNameEnum node) {
        var message = "Encode me!";
        var hashedMessage = "68907fbd785a694c3617d35a6ce49477ac5704d75f0e727e353da7bc664aacc2";

        var callType = getMethodName(calltype, "without");

        var data = encodeDataToByteArray(EQUIVALENCE_CALL, callType, TestUtil.asAddress("0x0000000000000000000000000000000000000002"), message.getBytes(StandardCharsets.UTF_8));
        final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);

        if (node.equals(NodeNameEnum.CONSENSUS)) {
            var transactionId = response.networkResponse().getTransactionIdStringNoCheckSum();
            var resultMessage = mirrorClient
                    .getContractActions(transactionId)
                    .getActions()
                    .get(1)
                    .getResultData();
            assertEquals(hashedMessage, to32BytesString(resultMessage));
        } else {
            TupleType tupleType = TupleType.parse("(bool,bytes)");
            var decodedResult = tupleType.decode(ByteString.fromHex(response.contractCallResponse().getResult().substring(2)).toByteArray());
            assertEquals(hashedMessage, to32BytesString(String.valueOf(Bytes.wrap((byte[]) decodedResult.get(1)))));
        }
    }

    @Then("I execute internal {string} against Ripemd-160 precompile to {node} node")
    public void executeAllCallsForRipemd160(String calltype, NodeNameEnum node) {
        var message = "Encode me!";
        var hashedMessage = "4f0c39893f4c1c805aea87a95b5d359a218920d6";

        var callType = getMethodName(calltype, "without");
        var data = encodeDataToByteArray(EQUIVALENCE_CALL, callType, TestUtil.asAddress("0x0000000000000000000000000000000000000003"), message.getBytes(StandardCharsets.UTF_8));
        final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);

        if (node.equals(NodeNameEnum.CONSENSUS)) {
            var transactionId = response.networkResponse().getTransactionIdStringNoCheckSum();
            var resultMessage = mirrorClient
                    .getContractActions(transactionId)
                    .getActions()
                    .get(1)
                    .getResultData();
            var result = Bytes.fromHexString(resultMessage).trimLeadingZeros().toUnprefixedHexString();
            assertEquals(hashedMessage, result);
        } else {
            TupleType tupleType = TupleType.parse("(bool,bytes)");
            var decodedResult = tupleType.decode(ByteString.fromHex(response.contractCallResponse().getResult().substring(2)).toByteArray());
            assertEquals(hashedMessage, String.valueOf(Bytes.wrap((byte[]) decodedResult.get(1)).trimLeadingZeros().toUnprefixedHexString()));
        }
    }

    @Then("I execute internal {string} against PRNG precompile address {string} amount to {node} node")
    public void executeInternalCallForPRNGWithoutAmount(String call, String amountType, NodeNameEnum node) {
        var callType = getMethodName(call, amountType);

        var data = encodeDataToByteArray(EQUIVALENCE_CALL, callType, TestUtil.asAddress("0x0000000000000000000000000000000000000169"), encodeDataToByteArray(GET_PRNG));

        if (amountType.equals("with")) {
            final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, Hbar.fromTinybars(10L));
            if (node.equals(NodeNameEnum.CONSENSUS)) {
                // POTENTIAL BUG
                // THIS RETURNS SUCCESS FOR THE 2ND CALL - > WE EXPECT INVALID_FEE_SUBMITTED
//                assertEquals(INVALID_FEE_SUBMITTED, response.errorMessage());
            } else {
                // POTENTIAL BUG
                // THIS RETURNS SUCCESS FOR THE 2ND CALL - > WE EXPECT INVALID_FEE_SUBMITTED
            }
        } else {
            final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);
            if (node.equals(NodeNameEnum.CONSENSUS)) {
                var transactionId = response.networkResponse().getTransactionIdStringNoCheckSum();
                var resultMessage = mirrorClient
                        .getContractActions(transactionId)
                        .getActions()
                        .get(1)
                        .getResultData();
                var result = Bytes.fromHexString(resultMessage).trimLeadingZeros();
                assertEquals(32, result.toArray().length);
            } else {
                if (call.equals("callcode")) {
                    assertEquals(32, response.contractCallResponse().getResultAsBytes().size());
                } else {
                    TupleType tupleType = TupleType.parse("(bool,bytes)");
                    var decodedResult = tupleType.decode(
                            ByteString.fromHex(response.contractCallResponse().getResult().substring(2)).toByteArray());
                    assertEquals(32, Bytes.wrap((byte[]) decodedResult.get(1)).size());
                }
            }
        }
    }

    @Then("I execute internal {string} against exchange rate precompile address {string} amount to {node} node")
    public void executeInternalCallForExchangeRateWithoutAmount(String call, String amountType, NodeNameEnum node) {
        var callType = getMethodName(call, amountType);

        var data = encodeDataToByteArray(EQUIVALENCE_CALL, callType, TestUtil.asAddress("0x0000000000000000000000000000000000000168"), encodeDataToByteArray(GET_EXCHANGE_RATE, new BigInteger("100")));

        if (amountType.equals("with")) {
            final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, Hbar.fromTinybars(10L));
            if (node.equals(NodeNameEnum.CONSENSUS)) {
                // POTENTIAL BUG
                // THIS RETURNS SUCCESS FOR THE 2ND CALL - > WE EXPECT INVALID_FEE_SUBMITTED
//                assertEquals(INVALID_FEE_SUBMITTED, response.errorMessage());
            } else {
                // POTENTIAL BUG
                // THIS RETURNS SUCCESS FOR THE 2ND CALL - > WE EXPECT INVALID_FEE_SUBMITTED
            }
            // AMOUNT REACHES ONLY THE CONTRACT(deployedEquivalenceCall)
        } else {
            final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);
            if (node.equals(NodeNameEnum.CONSENSUS)) {
                var transactionId = response.networkResponse().getTransactionIdStringNoCheckSum();
                var resultMessage = mirrorClient
                        .getContractActions(transactionId)
                        .getActions()
                        .get(1)
                        .getResultData();
                assertNull(response.errorMessage());
                var result = Bytes.fromHexString(resultMessage).trimLeadingZeros();
                assertTrue(result.toLong() > 1);
            } else {
                TupleType tupleType = TupleType.parse("(bool,bytes)");
                var decodedResult = tupleType.decode(
                        ByteString.fromHex(response.contractCallResponse().getResult().substring(2)).toByteArray());
                assertTrue(Bytes.wrap((byte[]) decodedResult.get(1)).toBigInteger().compareTo(BigInteger.ONE) > 0);
            }
        }
    }

    @Then("I make internal {string} to system account {string} {string} amount to {node} node")
    public void callToSystemAddress(String call, String address, String amountType, NodeNameEnum node) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var callType = getMethodName(call, amountType);

        byte[] functionParameterData;
        if (call.equals("callcode")) {
            functionParameterData = new byte[] {0x21, 0x21, 0x12, 0x12};
        } else {
            functionParameterData = new byte[0];
        }
        byte[] data =
                encodeDataToByteArray(EQUIVALENCE_CALL, callType, TestUtil.asAddress(accountId), functionParameterData);

        Hbar amount = null;
        if (amountType.equals("with")) {
            amount = Hbar.fromTinybars(10);
        }

        var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, amount);
        var accountNumber = extractAccountNumber(address);

        if (node.equals(NodeNameEnum.CONSENSUS)) {
            // POTENTIAL BUG
            // CALL WITH AMOUNT RETURNS FAILURE FOR THE 2ND CALL WITH PRECOMPILE_ERROR - > WE EXPECT INVALID_FEE_SUBMITTED
            // CALL WITHOUT RETURNS FAILURE FOR THE 2ND CALL WITH PRECOMPILE_ERROR - > WE EXPECT INVALID_SOLIDITY_ADDRESS
            // TOP LEVEL TRANSACTION IS SUCCESS
            // WAITING TO BE CLARIFIED
            if (accountNumber > 751) {
                assertThat(response.errorMessage()).isBlank();
            }
        } else {
            if (call.equals("callcode")) {
                var returnedDataBytes = response.contractCallResponse().getResultAsBytes();
                var returnedDataMessage = response.contractCallResponse().getResultAsText();
                if (amount == null) {
                    if (shouldSystemAccountWithoutAmountReturnInvalidSolidityAddress(accountNumber)) {
                        assertEquals(INVALID_SOLIDITY_ADDRESS, returnedDataMessage);
                    } else if (shouldSystemAccountWithoutAmountReturnSuccess(accountNumber)) {
                        assertArrayEquals(functionParameterData, returnedDataBytes.trimTrailingZeros().toArray());
                    }
                } else {
                    if (shouldSystemAccountWithAmountReturnInvalidFeeSubmitted(accountNumber)) {
                        assertEquals(INVALID_FEE_SUBMITTED, returnedDataMessage);
                    } else {
                        assertArrayEquals(functionParameterData, returnedDataBytes.trimTrailingZeros().toArray());
                    }
                }
            } else {
                TupleType tupleType = TupleType.parse("(bool,bytes)");
                var decodedResult = tupleType.decode(
                        ByteString.fromHex(response.contractCallResponse().getResult().substring(2)).toByteArray());
                var isSuccess = (boolean) decodedResult.get(0);
                var returnedData = String.valueOf(
                        Bytes.wrap((byte[]) decodedResult.get(1)).trimLeadingZeros().toUnprefixedHexString());

                if (amount == null) {
                    if (shouldSystemAccountWithoutAmountReturnInvalidSolidityAddress(accountNumber)) {
                        assertFalse(isSuccess);
                        assertEquals(INVALID_SOLIDITY_ADDRESS, returnedData);
                    } else if (shouldSystemAccountWithoutAmountReturnSuccess(accountNumber)) {
                        assertTrue(isSuccess);
                    }
                } else {
                    if (shouldSystemAccountWithAmountReturnInvalidFeeSubmitted(accountNumber)) {
                        assertFalse(isSuccess);
                        assertEquals(INVALID_FEE_SUBMITTED, returnedData);
                    } else {
                        assertTrue(isSuccess);
                    }
                }
            }
        }
    }

    @Then("I make internal {string} to account {account} {string} amount from {account}")
    public void internalCallToSystemAddress(
            String call, AccountNameEnum accountName, String amountType, AccountNameEnum signer) {
        String transactionId;
        var accountAlias = getAccountAlias(accountName);
        var callType = getMethodName(call, amountType);
        ContractFunctionParameters parameters =
                new ContractFunctionParameters().addAddress(accountAlias).addBytes(new byte[0]);

        // approveTokenFor(tokenName, tokenId,
        // AccountId.fromString(deployedPrecompileContract.contractId().toString()));
        // approveTokenFor(tokenName, tokenId, receiverAccount.getAccountId());
        accountClient.sendCryptoTransfer(
                accountClient.getAccount(signer).getAccountId(),
                Hbar.from(50L),
                accountClient.getAccount(signer).getPrivateKey());

        if (amountType.equals("with") && signer.name().equals("OPERATOR")) {
            transactionId = executeContractCallTransactionAndReturnId(
                    deployedEquivalenceCall, callType, parameters, Hbar.fromTinybars(10));
        } else if (signer.name().equals("OPERATOR")) {
            transactionId =
                    executeContractCallTransactionAndReturnId(deployedEquivalenceCall, callType, parameters, null);
        } else {
            transactionId = executeContractCallTransactionAndReturnId(
                    deployedEquivalenceCall, callType, parameters, null, accountClient.getAccount(signer));
        }
        var message = mirrorClient
                .getTransactions(transactionId)
                .getTransactions()
                .get(0)
                .getResult();
        var expectedMessage = (accountName.name().equals("ALICE")) ? SUCCESS : INVALID_SIGNATURE;
        assertEquals(expectedMessage, message);
    }

    @Then("I make internal {string} to ethereum precompile {string} address with amount to {node} node")
    public void internalCallToEthPrecompileWithAmount(String call, String address, NodeNameEnum node) {
        var callType = getMethodName(call, "with");
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();

        var data = encodeDataToByteArray(EQUIVALENCE_CALL, callType, TestUtil.asAddress(accountId), new byte[0]);
        final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, Hbar.fromTinybars(10L));

        if (node.equals(NodeNameEnum.CONSENSUS)) {
            assertEquals(INVALID_FEE_SUBMITTED, response.errorMessage());
        } else {
            // TODO
            // Potential bug - 0x result is returned instead of error message

//            TupleType tupleType = TupleType.parse("(bool,bytes)");
//            var decodedResult = tupleType.decode(ByteString.fromHex(response.contractCallResponse().getResult().substring(2)).toByteArray());
//            assertEquals(INVALID_FEE_SUBMITTED, String.valueOf(Bytes.wrap((byte[]) decodedResult.get(1)).trimLeadingZeros().toUnprefixedHexString()));
        }
    }

    @Then("I call precompile with transferFrom {token} token to a {string} address")
    public void transferFromFungibleTokens(TokenNameEnum tokenName, String address) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var contractFunction = getMethodName(tokenName.getSymbol(), "transferFrom");
        var receiverAccountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters()
                .addAddress(tokenId.toSolidityAddress())
                .addAddress(admin.getAccountId().toSolidityAddress())
                .addAddress(receiverAccountId)
                .addUint256(new BigInteger("1"));
        var transactionId = executeContractCallTransactionAndReturnId(
                deployedPrecompileContract, contractFunction, parameters, null);
        var message = mirrorClient
                .getTransactions(transactionId)
                .getTransactions()
                .get(1)
                .getResult();
        var expectedMessage = getExpectedResponseMessage(address);
        assertEquals(expectedMessage, message);
    }

    @Then("I call precompile with transferFrom {token} token to an EVM address")
    public void transferFromFungibleTokensToEVM(TokenNameEnum tokenName) throws InvalidProtocolBufferException {
        var HEX_DIGITS = "0123456789abcdef";
        var RANDOM_ADDRESS = to32BytesString(RandomStringUtils.random(40, HEX_DIGITS));
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var contractFunction = getMethodName(tokenName.getSymbol(), "transferFrom");
        approveTokenFor(
                tokenName,
                tokenId,
                AccountId.fromString(deployedPrecompileContract.contractId().toString()));

        var parameters = new ContractFunctionParameters()
                .addAddress(tokenId.toSolidityAddress())
                .addAddress(admin.getAccountId().toSolidityAddress())
                .addAddress(RANDOM_ADDRESS.substring(24, 64))
                .addUint256(new BigInteger("3"));
        var transactionId = executeContractCallTransactionAndReturnId(
                deployedPrecompileContract, contractFunction, parameters, null);
        var message = mirrorClient
                .getTransactions(transactionId)
                .getTransactions()
                .get(1)
                .getResult();

        assertEquals(SUCCESS, message);
    }

    @Then("I call precompile with transferFrom {token} token to a {account} EVM address")
    public void transferFromFungibleTokensReceiver(TokenNameEnum tokenName, AccountNameEnum accountName)
            throws InvalidProtocolBufferException {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var accountAlias = getAccountAlias(accountName);
        var accountId = (accountName.name().equals("ALICE")) ? secondReceiverAccount : receiverAccount;
        var contractFunction = getMethodName(tokenName.getSymbol(), "transferFrom");
        var expectedMessage =
                (accountName.name().equals("ALICE")) ? SUCCESS : INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;

        approveTokenFor(
                tokenName,
                tokenId,
                AccountId.fromString(deployedPrecompileContract.contractId().toString()));
        tokenClient.associate(accountId, tokenId);
        var parameters = new ContractFunctionParameters()
                .addAddress(tokenId.toSolidityAddress())
                .addAddress(admin.getAccountId().toSolidityAddress())
                .addAddress(accountAlias)
                .addUint256(new BigInteger("2"));
        var transactionId = executeContractCallTransactionAndReturnId(
                deployedPrecompileContract, contractFunction, parameters, null);
        var message = mirrorClient
                .getTransactions(transactionId)
                .getTransactions()
                .get(1)
                .getResult();

        assertEquals(expectedMessage, message);
    }

    @Then("I call precompile with signer BOB to transferFrom {token} token to ALICE")
    public void transferFromBobToAlice(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var contractFunction = getMethodName(tokenName.getSymbol(), "transferFrom");

        approveTokenFor(
                tokenName,
                tokenId,
                AccountId.fromString(deployedPrecompileContract.contractId().toString()));
        approveTokenFor(tokenName, tokenId, receiverAccount.getAccountId());
        accountClient.sendCryptoTransfer(
                receiverAccount.getAccountId(), Hbar.from(50L), receiverAccount.getPrivateKey());

        var parameters = new ContractFunctionParameters()
                .addAddress(tokenId.toSolidityAddress())
                .addAddress(admin.getAccountId().toSolidityAddress())
                .addAddress(secondReceiverAccount.getAccountId().toSolidityAddress())
                .addUint256(new BigInteger("4"));
        var transactionId = executeContractCallTransactionAndReturnId(
                deployedPrecompileContract, contractFunction, parameters, null, receiverAccount);
        var message = mirrorClient
                .getTransactions(transactionId)
                .getTransactions()
                .get(1)
                .getResult();

        assertEquals(SUCCESS, message);
    }

    @Then("I call precompile with transferFrom FUNGIBLE token to a {string} address")
    public void transferFromFungibleTokens(String address) {
        var receiverAccountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters()
                .addAddress(fungibleTokenId.toSolidityAddress())
                .addAddress(admin.getAccountId().toSolidityAddress())
                .addAddress(receiverAccountId)
                .addUint256(new BigInteger("1"));
        var transactionId = executeContractCallTransactionAndReturnId(
                deployedPrecompileContract, "transferFromExternal", parameters, null);
        var message = mirrorClient
                .getTransactions(transactionId)
                .getTransactions()
                .get(1)
                .getResult();
        var expectedMessage = getExpectedResponseMessage(address);
        assertEquals(expectedMessage, message);
    }

    @Then("I call precompile with transfer {token} token to a {string} address")
    public void transferNftTokens(TokenNameEnum tokenName, String address) throws InvalidProtocolBufferException {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var receiverAccountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var contractFunction = getMethodName(tokenName.getSymbol(), "transfer");
        approveTokenFor(
                tokenName,
                tokenId,
                AccountId.fromString(deployedPrecompileContract.contractId().toString()));
        var parameters = new ContractFunctionParameters()
                .addAddress(tokenId.toSolidityAddress())
                .addAddress(admin.getAccountId().toSolidityAddress())
                .addAddress(receiverAccountId)
                .addInt64(1L);
        var transactionId = executeContractCallTransactionAndReturnId(
                deployedPrecompileContract, contractFunction, parameters, null);
        var message = mirrorClient
                .getTransactions(transactionId)
                .getTransactions()
                .get(1)
                .getResult();
        var expectedMessage = getExpectedResponseMessage(address);
        assertEquals(expectedMessage, message);
    }

    @Then("I call precompile with transferFromNFT to a {string} address")
    public void transferFromNftTokens(String address) {
        var receiverAccountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters()
                .addAddress(nonFungibleTokenId.toSolidityAddress())
                .addAddress(admin.getAccountId().toSolidityAddress())
                .addAddress(receiverAccountId)
                .addUint256(new BigInteger("1"));
        var transactionId = executeContractCallTransactionAndReturnId(
                deployedPrecompileContract, "transferFromNFTExternal", parameters, null);
        var message = mirrorClient
                .getTransactions(transactionId)
                .getTransactions()
                .get(1)
                .getResult();
        var expectedMessage = INVALID_RECEIVING_NODE_ACCOUNT;
        if (extractAccountNumber(address) > 751) {
            expectedMessage = SPENDER_DOES_NOT_HAVE_ALLOWANCE;
        }
        assertEquals(expectedMessage, message);
    }

    @Then("I call precompile with transferFrom {token} token to a contract")
    public void transferFromTokensToContract(TokenNameEnum tokenName) throws InvalidProtocolBufferException {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var contractFunction = getMethodName(tokenName.getSymbol(), "transferFrom");
        tokenClient.associate(deployedEquivalenceCall.contractId(), tokenId);
        approveTokenFor(
                tokenName,
                tokenId,
                AccountId.fromString(deployedPrecompileContract.contractId().toString()));

        var parameters = new ContractFunctionParameters()
                .addAddress(tokenId.toSolidityAddress())
                .addAddress(admin.getAccountId().toSolidityAddress())
                .addAddress(deployedEquivalenceCall.contractId().toSolidityAddress())
                .addUint256(new BigInteger("1"));
        var transactionId = executeContractCallTransactionAndReturnId(
                deployedPrecompileContract, contractFunction, parameters, null);
        var message = mirrorClient
                .getTransactions(transactionId)
                .getTransactions()
                .get(1)
                .getResult();
        assertEquals(SUCCESS, message);
    }

    @Then("I execute internal {string} against Identity precompile to {node} node")
    public void executeAllCallsForIdentity(String calltype, NodeNameEnum node) {
        var message = "Encode me!";
        var messageBytes = message.getBytes(StandardCharsets.UTF_8);

        var callType = getMethodName(calltype, "without");
        var data = encodeDataToByteArray(EQUIVALENCE_CALL, callType, TestUtil.asAddress("0x0000000000000000000000000000000000000004"), messageBytes);

        final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);

        if (node.equals(NodeNameEnum.CONSENSUS)) {
            var transactionId = response.networkResponse().getTransactionIdStringNoCheckSum();
            var resultMessage = mirrorClient
                    .getContractActions(transactionId)
                    .getActions()
                    .get(1)
                    .getResultData();
            assertEquals(Hex.encodeHexString(messageBytes), resultMessage.replace("0x", ""));
        } else {
            TupleType tupleType = TupleType.parse("(bool,bytes)");
            var decodedResult = tupleType.decode(ByteString.fromHex(response.contractCallResponse().getResult().substring(2)).toByteArray());
            assertEquals(message, new String(decodedResult.get(1), StandardCharsets.UTF_8));
        }
    }

    public String getMethodName(String typeOfCall, String amountValue) {
        String combinedKey = typeOfCall + "_" + amountValue;

        return switch (combinedKey) {
            case "call_without" -> "makeCallWithoutAmount";
            case "call_with" -> "makeCallWithAmount";
            case "staticcall_without" -> "makeStaticCall";
            case "delegatecall_without" -> "makeDelegateCall";
            case "callcode_without" -> "callCodeToContractWithoutAmount";
            case "callcode_with" -> "callCodeToContractWithAmount";
            case "non_fungible_transferFrom" -> "transferFromNFTExternal";
            case "fungible_transferFrom" -> "transferFromExternal";
            case "fungible_transfer" -> "transferTokenExternal";
            case "non_fungible_transfer" -> "transferNFTExternal";
            default -> "Unknown";
        };
    }

    private static long extractAccountNumber(String account) {
        String[] parts = account.split("\\.");
        return Long.parseLong(parts[parts.length - 1]);
    }

    public static String extractStatus(String transactionResult) {
        String key = "status=";
        int statusIndex = transactionResult.indexOf(key);

        if (statusIndex != -1) {
            int startIndex = statusIndex + key.length();
            int endIndex = transactionResult.indexOf(',', startIndex);
            endIndex = endIndex != -1 ? endIndex : transactionResult.length();
            return transactionResult.substring(startIndex, endIndex);
        }

        return "Status not found";
    }

    public String extractInternalCallErrorMessage(String transactionId) throws IllegalArgumentException {
        var actions = mirrorClient.getContractActions(transactionId).getActions();
        if (actions == null || actions.size() < 2) {
            throw new IllegalArgumentException("The actions list must contain at least two elements.");
        }

        String hexString = actions.get(1).getResultData();
        return hexToAscii(hexString.replace("0x", ""));
    }

    private String executeContractCallTransaction(
            DeployedContract deployedContract,
            String functionName,
            ContractFunctionParameters parameters,
            Hbar payableAmount) {
        try {
            ExecuteContractResult executeContractResult = contractClient.executeContract(
                    deployedContract.contractId(),
                    contractClient
                            .getSdkClient()
                            .getAcceptanceTestProperties()
                            .getFeatureProperties()
                            .getMaxContractFunctionGas(),
                    functionName,
                    parameters,
                    payableAmount);

            networkTransactionResponse = executeContractResult.networkTransactionResponse();
            return networkTransactionResponse.getReceipt().status == Status.SUCCESS
                    ? TRANSACTION_SUCCESSFUL_MESSAGE
                    : TRANSACTION_FAILED_MESSAGE;
        } catch (Exception e) {
            // Return the exception message
            return e.getMessage();
        }
    }

    private String executeContractCallTransaction(
            DeployedContract deployedContract, String functionName, ContractFunctionParameters parameters) {
        try {
            ExecuteContractResult executeContractResult = contractClient.executeContract(
                    deployedContract.contractId(),
                    contractClient
                            .getSdkClient()
                            .getAcceptanceTestProperties()
                            .getFeatureProperties()
                            .getMaxContractFunctionGas(),
                    functionName,
                    parameters,
                    null);

            networkTransactionResponse = executeContractResult.networkTransactionResponse();
            return networkTransactionResponse.getReceipt().status == Status.SUCCESS
                    ? TRANSACTION_SUCCESSFUL_MESSAGE
                    : TRANSACTION_FAILED_MESSAGE;
        } catch (Exception e) {
            // Return the exception message
            return e.getMessage();
        }
    }

    private String executeContractCallTransaction(
            ContractId contractId, String functionName, ContractFunctionParameters parameters, Hbar payableAmount) {
        try {
            ExecuteContractResult executeContractResult = contractClient.executeContract(
                    contractId,
                    contractClient
                            .getSdkClient()
                            .getAcceptanceTestProperties()
                            .getFeatureProperties()
                            .getMaxContractFunctionGas(),
                    functionName,
                    parameters,
                    payableAmount);

            networkTransactionResponse = executeContractResult.networkTransactionResponse();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
            return TRANSACTION_SUCCESSFUL_MESSAGE;
        } catch (Exception e) {
            // Return the exception message
            return e.getMessage();
        }
    }

    private String executeContractCallTransactionAndReturnId(
            DeployedContract deployedContract,
            String functionName,
            ContractFunctionParameters parameters,
            Hbar payableAmount) {
        try {
            ExecuteContractResult executeContractResult = contractClient.executeContract(
                    deployedContract.contractId(),
                    contractClient
                            .getSdkClient()
                            .getAcceptanceTestProperties()
                            .getFeatureProperties()
                            .getMaxContractFunctionGas(),
                    functionName,
                    parameters,
                    payableAmount);

            networkTransactionResponse = executeContractResult.networkTransactionResponse();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
            return networkTransactionResponse.getTransactionIdStringNoCheckSum();
        } catch (Exception e) {
            return extractTransactionId(e.getMessage());
        }
    }

    private String executeContractCallTransactionAndReturnId(
            DeployedContract deployedContract,
            String functionName,
            ContractFunctionParameters parameters,
            Hbar payableAmount,
            ExpandedAccountId payer) {
        try {
            ExecuteContractResult executeContractResult = contractClient.executeContract(
                    deployedContract.contractId(),
                    contractClient
                            .getSdkClient()
                            .getAcceptanceTestProperties()
                            .getFeatureProperties()
                            .getMaxContractFunctionGas(),
                    functionName,
                    parameters,
                    payableAmount,
                    payer);

            networkTransactionResponse = executeContractResult.networkTransactionResponse();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
            return networkTransactionResponse.getTransactionIdStringNoCheckSum();
        } catch (Exception e) {
            return extractTransactionId(e.getMessage());
        }
    }

    private ContractFunctionResult executeContractCallQuery(
            DeployedContract deployedContract, String functionName, ContractFunctionParameters parameters) {
        return contractClient.executeContractQuery(
                deployedContract.contractId(),
                functionName,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                parameters);
    }

    public static String extractTransactionId(String message) {
        Pattern pattern = Pattern.compile("transactionId=(\\d+\\.\\d+\\.\\d+)@(\\d+)\\.(\\d+)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
        } else {
            return "Not found";
        }
    }

    private static String getExpectedResponseMessage(String address) {
        var expectedMessage = INVALID_RECEIVING_NODE_ACCOUNT;
        if (extractAccountNumber(address) > 751 && extractAccountNumber(address) < 1001) {
            expectedMessage = TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
        } else if (extractAccountNumber(address) > 1001) {
            expectedMessage = INVALID_ALIAS_KEY;
        }
        return expectedMessage;
    }

    private void approveTokenFor(TokenNameEnum tokenName, TokenId tokenId, AccountId accountId) {
        if (tokenName.getSymbol().equals("non_fungible")) {
            accountClient.approveNftAllSerials(tokenId, accountId);
        } else {
            accountClient.approveToken(tokenId, accountId, 10L);
        }
    }

    private String getAccountAlias(AccountNameEnum accountName) {
        if (accountName.name().equals("ALICE")) {
            return secondReceiverAccount.getAccountId().toSolidityAddress();
        } else if (accountName.name().equals("BOB")) {
            return receiverAccountAlias;
        } else {
            return "Invalid account name!";
        }
    }

    private boolean shouldSystemAccountWithoutAmountReturnInvalidSolidityAddress(long accountNumber) {
        return accountNumber == 0
                || (accountNumber >= 10 && accountNumber <= 357)
                || (accountNumber >= 361 && accountNumber <= 1000);
    }

    private boolean shouldSystemAccountWithoutAmountReturnSuccess(long accountNumber) {
        return (accountNumber >= 1 && accountNumber <= 9)
                || (accountNumber >= 358 && accountNumber <= 360);
    }

    private boolean shouldSystemAccountWithAmountReturnInvalidFeeSubmitted(long accountNumber) {
        return accountNumber >= 0 && accountNumber <= 750;
    }

    @Getter
    @RequiredArgsConstructor
    enum Selectors implements SelectorInterface {
        IS_TOKEN("isToken(address)"),
        GET_PRNG("getPseudorandomSeed()"),
        GET_EXCHANGE_RATE("tinycentsToTinybars(uint256)");

        private final String selector;
    }
}
