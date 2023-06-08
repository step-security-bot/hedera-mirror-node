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

package com.hedera.mirror.test.e2e.acceptance.steps;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.ZERO_ADDRESS;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.FastHex;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.hashgraph.sdk.*;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.*;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.util.ResourceUtils;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class CallFeature extends AbstractFeature {

    private CompiledSolidityArtifact estimateGasArtifacts;
    private CompiledSolidityArtifact ercArtifacts;
    private CompiledSolidityArtifact precompileArtifacts;
    private String newAccountAddress;
    private static DeployedContract deployedContract;
    private final ContractClient contractClient;
    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;
    private String estimateGasContractAddress;
    private String ERCContractAddress;
    private String precompileContractAddress;
    private int lowerDeviation;
    private int upperDeviation;
    private final AccountClient accountClient;
    private final TokenClient tokenClient;
    private final List<TokenId> tokenIds = new ArrayList<>();
    private ExpandedAccountId allowanceSpenderAccountId;
    private static final int INITIAL_SUPPLY = 1_000_000;
    private static final int MAX_SUPPLY = 1;
    private String testName = "TEST_name";
    private String fungibleTokenName = "fungible_name";
    private String nonFungibleTokenName = "non_fungible_name";

    private static final ObjectMapper MAPPER = new ObjectMapper().configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    /**
     * Checks if the actualUsedGas is within the specified range of the estimatedGas.
     * <p>
     * The method calculates the lower and upper bounds as percentages of the actualUsedGas, then checks if the
     * estimatedGas is within the range (inclusive) and returns true if it is, otherwise returns false.
     *
     * @param actualUsedGas     the integer value that represents the expected value
     * @param estimatedGas      the integer value to be checked
     * @param lowerBoundPercent the integer percentage value for the lower bound of the acceptable range
     * @param upperBoundPercent the integer percentage value for the upper bound of the acceptable range
     * @return true if the actualUsedGas is within the specified range, false otherwise
     */
    public static boolean isWithinDeviation(int actualUsedGas, int estimatedGas, int lowerBoundPercent,
                                            int upperBoundPercent) {
        int lowerDeviation = actualUsedGas * lowerBoundPercent / 100;
        int upperDeviation = actualUsedGas * upperBoundPercent / 100;

        int lowerBound = actualUsedGas + lowerDeviation;
        int upperBound = actualUsedGas + upperDeviation;

        return (estimatedGas >= lowerBound) && (estimatedGas <= upperBound);
    }

    /**
     * Estimate gas values are hardcoded at this moment until we get better solution such as actual gas used returned
     * from the consensus node. It will be changed in future PR when actualGasUsed field is added to the protobufs.
     */
    private static final String ALLOWANCE_SELECTOR = "927da105"; //TODO
    private static final String IERC721_TOKEN_NAME_SELECTOR = "b1ec803c";
    private static final String IERC721_TOKEN_SYMBOL_SELECTOR = "f6b486b7";
    private static final String IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR = "3cd9a3ab";
    private static final String IERC721_TOKEN_BALANCE_OF_SELECTOR = "063c7dcf";
    private static final String HTS_IS_TOKEN_SELECTOR = "bff9834f";
    private static final String HTS_IS_FROZEN_SELECTOR = "565ca6fa";
    private static final String HTS_IS_KYC_SELECTOR = "bc2fb00e";
    private static final String HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR = "319a8723";
    private static final String HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR = "fd4d1c26";
    private static final String HTS_GET_TOKEN_CUSTOM_FEES_SELECTOR = "44f38bc8";

    @Value("classpath:solidity/artifacts/contracts/EstimateGasContract.sol/EstimateGasContract.json")
    private Path estimateGasTestContract;

    @Value("classpath:solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json")
    private Path ercTestContract;

    @Value("classpath:solidity/artifacts/contracts/PrecompileTestContract.sol/PrecompileTestContract.json")
    private Path precompileTestContract;

    @Before
    public void initialization() throws IOException {
        estimateGasArtifacts = MAPPER.readValue(ResourceUtils.getFile(estimateGasTestContract.toUri()),
                CompiledSolidityArtifact.class);
        ercArtifacts = MAPPER.readValue(ResourceUtils.getFile(ercTestContract.toUri()),
                CompiledSolidityArtifact.class);
        precompileArtifacts = MAPPER.readValue(ResourceUtils.getFile(precompileTestContract.toUri()),
                CompiledSolidityArtifact.class);
        newAccountAddress = PrivateKey.generateECDSA().getPublicKey().toEvmAddress().toString();
    }

    @Before
    public void createNewFungibleToken() {
        createNewToken(
                fungibleTokenName,
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.INFINITE,
                Collections.emptyList());
    }

    @Before
    public void createNewNonFungibleToken() {
        createNewToken(
                nonFungibleTokenName,
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenSupplyType.INFINITE,
                Collections.emptyList());
    }

    @Given("I successfully create estimate gas contract from contract bytes")
    public void createNewEstimateContract() {
        deployedContract = createContract(estimateGasArtifacts);
        estimateGasContractAddress = deployedContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create ERC contract from contract bytes")
    public void createNewERCContract() {
        deployedContract = createContract(ercArtifacts);
        ERCContractAddress = deployedContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create precompile contract from contract bytes")
    public void createNewPrecompileContract() {
        deployedContract = createContract(precompileArtifacts);
        precompileContractAddress = deployedContract.contractId().toSolidityAddress();
    }


    @And("lower deviation is {int} and upper deviation is {int}")
    public void setDeviations(int lower, int upper) {
        lowerDeviation = lower;
        upperDeviation = upper;
    }

    //ETHCALL-017
    @RetryAsserts
    @Then("I call function with IERC721Metadata token name")
    public void IERC721MetadataTokenName(){
        var getResponse = mirrorClient.contractsCall(
                IERC721_TOKEN_NAME_SELECTOR + to32BytesString(tokenIds.get(0).toSolidityAddress()),
                ERCContractAddress,
                contractClient.getClientAddress());

        assertThat(getResponse.getResultAsText()).isEqualTo(testName);
    }

    //ETHCALL-018
    @RetryAsserts
    @Then("I call function with IERC721Metadata token symbol")
    public void IERC721MetadataTokenSymbol(){
        var getSymbolResponse = mirrorClient.contractsCall(
                IERC721_TOKEN_SYMBOL_SELECTOR + to32BytesString(tokenIds.get(0).toSolidityAddress()),
                ERCContractAddress,
                contractClient.getClientAddress());

        assertThat(getSymbolResponse.getResultAsText()).isEqualTo(fungibleTokenName);
    }

    //ETHCALL-019
    @RetryAsserts
    @Then("I call function with IERC721Metadata token totalSupply")
    public void IERC721MetadataTokenTotalSupply(){
        var getTotalSupplyResponse = mirrorClient.contractsCall(
                IERC721_TOKEN_TOTAL_SUPPLY_SELECTOR + to32BytesString(tokenIds.get(0).toSolidityAddress()),
                ERCContractAddress,
                contractClient.getClientAddress());

        assertThat(getTotalSupplyResponse.getResultAsNumber()).isEqualTo(1_000_000L);
    }

    //ETHCALL-020
    @RetryAsserts
    @Then("I call function with IERC721 token balanceOf owner")
    public void IERC721MetadataTokenBalanceOf(){
        var getBalanceOfResponse = mirrorClient.contractsCall(
                IERC721_TOKEN_BALANCE_OF_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()),
                ERCContractAddress,
                contractClient.getClientAddress());

        assertThat(getBalanceOfResponse.getResultAsNumber()).isEqualTo(1000000);
    }

    //ETHCALL-025
    @RetryAsserts
    @Then("I call function with HederaTokenService isToken token")
    public void HTSIsToken(){
        var getIsTokenResponse = mirrorClient.contractsCall(
                HTS_IS_TOKEN_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()),
                precompileContractAddress,
                contractClient.getClientAddress());

        assertThat(getIsTokenResponse.getResultAsBoolean()).isTrue();
    }

    //ETHCALL-026
    @RetryAsserts
    @Then("I call function with HederaTokenService isFrozen token, account")
    public void HTSIsFrozen(){
        var getIsFrozenResponse = mirrorClient.contractsCall(
                HTS_IS_FROZEN_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()),
                precompileContractAddress,
                contractClient.getClientAddress());

        assertThat(getIsFrozenResponse.getResultAsBoolean()).isFalse();
    }

    //ETHCALL-027
    @RetryAsserts
    @Then("I call function with HederaTokenService isKyc token, account")
    public void HTSIsKyc(){
        var getIsKycResponse = mirrorClient.contractsCall(
                HTS_IS_KYC_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()),
                precompileContractAddress,
                contractClient.getClientAddress());

        assertThat(getIsKycResponse.getResultAsBoolean()).isFalse();
    }

    //ETHCALL-028
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultFreezeStatus token")
    public void HTSgetTokenDefaultFreezeStatus(){
        var getDefaultFreezeStatusResponse = mirrorClient.contractsCall(
                HTS_GET_DEFAULT_FREEZE_STATUS_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()),
                precompileContractAddress,
                contractClient.getClientAddress());

        assertThat(getDefaultFreezeStatusResponse.getResultAsBoolean()).isFalse();
    }

    //ETHCALL-029
    @RetryAsserts
    @Then("I call function with HederaTokenService getTokenDefaultKycStatus token")
    public void HTSgetTokenDefaultKycStatus(){
        var getIsKycStatusResponse = mirrorClient.contractsCall(
                HTS_GET_TOKEN_DEFAULT_KYC_STATUS_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()),
                precompileContractAddress,
                contractClient.getClientAddress());

        assertThat(getIsKycStatusResponse.getResultAsBoolean()).isFalse();
    }

    //ETHCALL-040
    @RetryAsserts
    @Then("I call function that test allowance with evm address for spender")
    public void allowanceWithEVMaddressForSpender(){
        String accountName = "Bob";
        int amount = 1;
        allowanceSpenderAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        networkTransactionResponse =
                accountClient.approveToken(tokenIds.get(0), allowanceSpenderAccountId.getAccountId(), amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

      //TODO  allowanceSpenderAccountId.getAccountId()//To long zero
        var longZeroAddress = Long.parseLong(allowanceSpenderAccountId.getAccountId().toString());

        var getAllowanceResponse = getAllowanceResponse(tokenIds.get(0).toSolidityAddress(), contractClient.getClientAddress());

//3 cases
        //solidity to EVM
        //long zero to evm
        //evm to evm
        //3 test with different selector
        //isApprovedForAll (change the selector)
        //evm adress for account (change the selector - balanceOf will take only 1 param
        assertThat(getAllowanceResponse.getResultAsNumber()).isZero();

       //TODO validateGasEstimation(HTS_GET_TOKEN_KEY_SELECTOR, HTS_GET_TOKEN_KEY_ACTUAL_GAS, precompileContractAddress);
    }

    //ETHCALL-056
    @RetryAsserts
    @Then("I call function that test")
    public void allowanceWithEVMaddressFor(){

        //TODO validateGasEstimation(HTS_GET_TOKEN_KEY_SELECTOR, HTS_GET_TOKEN_KEY_ACTUAL_GAS, precompileContractAddress);
    }

    @Retryable(
            value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyToken(TokenId tokenId) {
        MirrorTokenResponse mirrorToken = mirrorClient.getTokenInfo(tokenId.toString());

        assertNotNull(mirrorToken);
        assertThat(mirrorToken.getTokenId()).isEqualTo(tokenId.toString());
    }

    private DeployedContract createContract(CompiledSolidityArtifact compiledSolidityArtifact) {
        var fileId = persistContractBytes(compiledSolidityArtifact.getBytecode().replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(fileId,
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
               null,
                null);
        var contractId = verifyCreateContractNetworkResponse();

        return new DeployedContract(fileId, contractId, compiledSolidityArtifact);
    }

    private record DeployedContract(FileId fileId, ContractId contractId,
                                    CompiledSolidityArtifact compiledSolidityArtifact) {
    }

    private ContractId verifyCreateContractNetworkResponse() {
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var contractId = networkTransactionResponse.getReceipt().contractId;
        assertNotNull(contractId);
        return contractId;
    }

    private FileId persistContractBytes(String contractContents) {
        // rely on SDK chunking feature to upload larger files
        networkTransactionResponse = fileClient.createFile(new byte[]{});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);

        networkTransactionResponse = fileClient.appendFile(fileId, contractContents.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        return fileId;
    }

    public ContractCallResponse getAllowanceResponse(String firstAddress, String secondAddress){
        var getAllowanceResponseResult = mirrorClient.contractsCall(
                ALLOWANCE_SELECTOR
                        + to32BytesString(firstAddress)
                        + to32BytesString(tokenClient
                        .getSdkClient()
                        .getExpandedOperatorAccountId()
                        .getAccountId()
                        .toSolidityAddress())
                        + to32BytesString(secondAddress),
                ERCContractAddress,
                contractClient.getClientAddress());
        return getAllowanceResponseResult;
    }

    private TokenId createNewToken(
            String symbol,
            int freezeStatus,
            int kycStatus,
            TokenType tokenType,
            TokenSupplyType tokenSupplyType,
            List<CustomFee> customFees) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.createToken(
                admin,
                symbol,
                freezeStatus,
                kycStatus,
                admin,
                INITIAL_SUPPLY,
                tokenSupplyType,
                MAX_SUPPLY,
                tokenType,
                customFees);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);
        tokenIds.add(tokenId);

        return tokenId;
    }
}
