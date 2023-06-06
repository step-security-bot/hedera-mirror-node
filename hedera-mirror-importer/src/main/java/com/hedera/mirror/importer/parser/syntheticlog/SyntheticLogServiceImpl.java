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

import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.parser.syntheticlog.contractlog.SyntheticContractLog;
import com.hedera.mirror.importer.parser.syntheticlog.contractlog.SyntheticContractLogService;
import com.hedera.mirror.importer.parser.syntheticlog.contractresult.SyntheticContractResult;
import com.hedera.mirror.importer.parser.syntheticlog.contractresult.SyntheticContractResultService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SyntheticLogServiceImpl implements SyntheticLogService {

    private final SyntheticContractLogService syntheticContractLogService;
    private final SyntheticContractResultService syntheticContractResultService;

    @Override
    public void create(RecordItem recordItem, SyntheticContractLog log, SyntheticContractResult result) {
        if (isContract(recordItem)) {
            return;
        }
        syntheticContractLogService.create(log);
        syntheticContractResultService.create(result);
    }

    private boolean isContract(RecordItem recordItem) {
        return recordItem.getTransactionRecord().hasContractCallResult()
                || recordItem.getTransactionRecord().hasContractCreateResult();
    }
}
