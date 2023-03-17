package com.hedera.mirror.web3.utils;

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

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.inject.Named;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;

@NoArgsConstructor
@Named
public class FunctionEncoder {
    private static final String RESOURCE_PATH = "build/resources/test/contracts/%1$s.%2$s";
    private static final String STRING = "(string)";
    private static final String ADDRESS = "(address)";
    private static final String ADDRESS_DUO = "(address,address)";
    private static final String UINT_8 = "(uint8)";
    private static final String UINT = "(uint256)";
    private static final String INT = "(int256)";
    private static final String INT64 = "(int64)";

    private static final String TRIPLE_ADDRESS = "(address,address,address)";
    private static final String ADDRESS_UINT = "(address,uint256)";
    private static final String BOOLEAN = "(bool)";
    private static final String KEY_VALUE = "(bool,address,bytes,bytes,address)";
    public static final byte[] ECDSA = new byte[] {58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42,
            96, 75, -31, -5, 72, 13, -70, 101, -111, -1, 77, -103, 47, -118, 107, -58, -85, -63, 55, -57};

    private final Map<String, SolidityFunction> functionsAbi = new HashMap<>();
//    private final Map<String, String> contracts = new HashMap<>();

    public byte[] getContractBytes(final String contractName) {
        try {
            final var path = getPathFor(contractName, "bin");
            return Hex.decode(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Bytes functionHashFor(final String functionName, final String contractName, final Object... parameters) {
        final var functionSignature = functionsAbi.getOrDefault(functionName, getFunctionAbi(functionName,
                contractName));
        final var inputAbi = functionSignature.functionInputAbi;

        Function function = new Function(functionName + inputAbi);

        Bytes functionBytes = Bytes.wrap(function.selector());
        Bytes parametersBytes = encodeTupleParameters(inputAbi, parameters);
        return Bytes.concatenate(functionBytes, parametersBytes);
    }

    public String encodedResultFor(final String functionName, final String contractName, final Object... results) {
        final var type =
                functionsAbi.getOrDefault(functionName, getFunctionAbi(functionName, contractName)).functionOutputAbi;
        return encodeTupleParameters(type, results).toHexString();
    }

    private Bytes encodeTupleParameters(String tupleSig, Object... parameters) {
//        final var tupleElemets = tupleSig.replaceAll("[()]", "").split(",");
        TupleType tupleType = TupleType.parse(tupleSig);
        final var result =
                switch (tupleSig) {
                    case ADDRESS -> Tuple.of(converAddress((Address) parameters[0]));
                    case STRING, UINT_8, BOOLEAN, INT64 -> Tuple.of(parameters[0]);
                    case UINT, INT -> Tuple.of(BigInteger.valueOf((long) parameters[0]));
                    case ADDRESS_DUO ->
                            Tuple.of(converAddress((Address) parameters[0]), converAddress((Address) parameters[1]));
                    case TRIPLE_ADDRESS ->
                            Tuple.of(converAddress((Address) parameters[0]), converAddress((Address) parameters[1]),
                                    converAddress((Address) parameters[2]));
                    case ADDRESS_UINT ->
                            Tuple.of(converAddress((Address) parameters[0]), BigInteger.valueOf((long) parameters[1]));
                    case KEY_VALUE -> Tuple.of(
                            parameters[0],
                            converAddress((Address) parameters[1]),
                            parameters[2],
                            parameters[3],
                            converAddress((Address)parameters[4]));
                    default -> Tuple.EMPTY;
                };
        return Bytes.wrap(tupleType.encode(result).array());
    }


    private SolidityFunction getFunctionAbi(final String functionName, final String contractName) {
        var contractPath = getPathFor(contractName, "json");

        try (final var in = new FileInputStream(contractPath.toString())) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(in);

            JsonNode functionNode = StreamSupport.stream(rootNode.spliterator(), false)
                    .filter(node -> node.get("name").asText().equals(functionName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Function not found: " + functionName));

            JsonNode inputsNode = functionNode.get("inputs");
            final var inputParams = StreamSupport.stream(inputsNode.spliterator(), false)
                    .map(node -> node.get("type").asText())
                    .collect(Collectors.joining(",", "(", ")"));

            JsonNode outputsNode = functionNode.get("outputs");
            final var outputParams = StreamSupport.stream(outputsNode.spliterator(), false)
                    .map(node -> node.has("components")
                            ? StreamSupport.stream(node.get("components").spliterator(), false)
                            .map(component -> component.get("type").asText())
                            .collect(Collectors.joining(",", "(", ")"))
                            : "(" + node.get("type").asText() + ")"
                    )
                    .collect(Collectors.joining());

            final var functionAbi = SolidityFunction.builder()
                    .functionName(functionName)
                    .functionInputAbi(inputParams)
                    .functionOutputAbi(outputParams)
                    .build();
            functionsAbi.put(functionName, functionAbi);

            return functionAbi;
        } catch (IOException e) {
            return SolidityFunction.builder().build();
        }
    }

    private Path getPathFor(final String fileName, final String fileExtension) {
        return Paths.get(String.format(RESOURCE_PATH, fileName, fileExtension));
    }

    private com.esaulpaugh.headlong.abi.Address converAddress(final Address address) {
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(address.toUnsignedBigInteger()));
    }

    @Value
    @Builder
    private static class SolidityFunction {
        String functionName;
        String functionInputAbi;
        String functionOutputAbi;
    }
}


//    private static final ABIType<Tuple> ENCODER = TypeFactory.create("("
//            + DecodingFacade.removeBrackets(BYTES32)
//            + ","
//            + DecodingFacade.TOKEN_KEY_DECODER
//            + ARRAY_BRACKETS
//            + ")");
