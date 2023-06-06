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

package com.hedera.mirror.importer.parser.syntheticlog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.syntheticlog.contractlog.SyntheticContractLogService;
import com.hedera.mirror.importer.parser.syntheticlog.contractlog.SyntheticContractLogServiceImpl;
import com.hedera.mirror.importer.parser.syntheticlog.contractlog.TransferContractLog;
import com.hedera.mirror.importer.parser.syntheticlog.contractlog.TransferIndexedContractLog;
import com.hedera.mirror.importer.parser.syntheticlog.contractresult.SyntheticContractResultService;
import com.hedera.mirror.importer.parser.syntheticlog.contractresult.SyntheticContractResultServiceImpl;
import com.hedera.mirror.importer.parser.syntheticlog.contractresult.TransferContractResult;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyntheticLogServiceImplTest {
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    private final EntityProperties entityProperties = new EntityProperties();

    @Mock
    private EntityListener entityListener;

    private SyntheticLogService syntheticLogService;

    private RecordItem recordItem;
    private EntityId entityTokenId;
    private EntityId senderId;
    private EntityId receiverId;
    private long amount;

    @BeforeEach
    void beforeEach() {
        SyntheticContractLogService syntheticContractLogService =
                new SyntheticContractLogServiceImpl(entityListener, entityProperties);
        SyntheticContractResultService syntheticContractResultService =
                new SyntheticContractResultServiceImpl(entityListener, entityProperties);
        syntheticLogService = new SyntheticLogServiceImpl(syntheticContractLogService, syntheticContractResultService);

        recordItem = recordItemBuilder.tokenMint(TokenType.FUNGIBLE_COMMON).build();

        TokenID tokenId = recordItem.getTransactionBody().getTokenMint().getToken();
        entityTokenId = EntityId.of(tokenId);
        senderId = EntityId.EMPTY;
        receiverId =
                EntityId.of(recordItem.getTransactionBody().getTransactionID().getAccountID());
        amount = recordItem.getTransactionBody().getTokenMint().getAmount();
    }

    @Test
    @DisplayName("Should be able to create valid synthetic contract log")
    void createValid() {
        syntheticLogService.create(
                recordItem,
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount),
                new TransferContractResult(recordItem, entityTokenId, senderId));
        verify(entityListener, times(1)).onContractLog(any());
        verify(entityListener, times(1)).onContractResult(any());
    }

    @Test
    @DisplayName("Should be able to create valid synthetic log with indexed value")
    void createValidIndexed() {
        syntheticLogService.create(
                recordItem,
                new TransferIndexedContractLog(recordItem, entityTokenId, senderId, receiverId, amount),
                new TransferContractResult(recordItem, entityTokenId, senderId));
        verify(entityListener, times(1)).onContractLog(any());
        verify(entityListener, times(1)).onContractResult(any());
    }

    @Test
    @DisplayName("Should not create synthetic log with contract")
    void createWithContract() {
        recordItem = recordItemBuilder.contractCall().build();
        syntheticLogService.create(
                recordItem,
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount),
                new TransferContractResult(recordItem, entityTokenId, senderId));
        verify(entityListener, times(0)).onContractLog(any());
        verify(entityListener, times(0)).onContractResult(any());
    }

    @Test
    @DisplayName("Should not create synthetic log with entity properties turned off")
    void createTurnedOff() {
        entityProperties.getPersist().setSyntheticContractLogs(false);
        entityProperties.getPersist().setSyntheticContractResults(false);
        syntheticLogService.create(
                recordItem,
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount),
                new TransferContractResult(recordItem, entityTokenId, senderId));
        verify(entityListener, times(0)).onContractLog(any());
        verify(entityListener, times(0)).onContractResult(any());
    }
}
