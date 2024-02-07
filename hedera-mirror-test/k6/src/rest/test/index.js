/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import {getSequentialTestScenarios} from '../../lib/common.js';

// import test modules
import * as accounts from './accounts.js';
import * as accountsBalanceFalse from './accountsBalanceFalse.js';
import * as accountsBalanceFalsePubkey from './accountsBalanceFalsePubkey.js';
import * as accountsBalanceGt0 from './accountsBalanceGt0.js';
import * as accountsBalanceGt0Pubkey from './accountsBalanceGte0Pubkey.js';
import * as accountsBalanceNe from './accountsBalanceNe.js';
import * as accountsCryptoAllowance from './accountsCryptoAllowance.js';
import * as accountsCryptoAllowanceSpender from './accountsCryptoAllowanceSpender.js';
import * as accountsId from './accountsId.js';
import * as accountsTokenAllowance from './accountsTokenAllowance.js';
import * as blocks from './blocks.js';
import * as blocksNumber from './blocksNumber.js';
import * as contracts from './contracts.js';
import * as contractsId from './contractsId.js';
import * as contractsIdResults from './contractsIdResults.js';
import * as contractsIdResultsLogs from './contractsIdResultsLogs.js';
import * as contractsIdResultsTimestamp from './contractsIdResultsTimestamp.js';
import * as contractsIdState from './contractsIdState.js';
import * as contractsResults from './contractsResult.js';
import * as contractsResultsId from './contractsResultsId.js';
import * as contractsResultsIdActions from './contractsResultsIdActions.js';
import * as contractsResultsLogs from './contractsResultsLogs.js';
import * as networkExchangeRate from './networkExchangeRate.js';
import * as networkFees from './networkFees.js';
import * as networkNodes from './networkNodes.js';
import * as networkStake from './networkStake.js';
import * as networkSupply from './networkSupply.js';
import * as rampUp from './rampUp.js';
import * as schedulesId from './schedulesId.js';
import * as tokensId from './tokensId.js';
import * as tokensNftsSerial from './tokensNftsSerial.js';
import * as tokensNftsSerialTransactions from './tokensNftsSerialTransactions.js';
import * as topicsIdMessages from './topicsIdMessages.js';
import * as topicsIdMessagesSequence from './topicsIdMessagesSequence.js';
import * as topicsIdMessagesSequenceQueryParam from './topicsIdMessagesSequenceQueryParam.js';
import * as topicsMessagesTimestamp from './topicsMessagesTimestamp.js';
import * as transactionsAccountId from './transactionsAccountId.js';
import * as transactionsHash from './transactionsHash.js';

// add test modules here
const tests = {
  accounts,
  accountsCryptoAllowance,
  accountsCryptoAllowanceSpender,
  accountsId,
  accountsTokenAllowance,
  blocks,
  blocksNumber,
  contracts,
  contractsId,
  contractsIdResults,
  contractsIdResultsLogs,
  contractsIdResultsTimestamp,
  contractsIdState,
  contractsResults,
  contractsResultsId,
  contractsResultsIdActions,
  contractsResultsLogs,
  networkExchangeRate,
  networkFees,
  networkNodes,
  networkStake,
  networkSupply,
  rampUp,
  schedulesId,
  tokensId,
  tokensNftsSerial,
  tokensNftsSerialTransactions,
  topicsIdMessages,
  topicsIdMessagesSequence,
  topicsIdMessagesSequenceQueryParam,
  topicsMessagesTimestamp,
  transactionsAccountId,
  transactionsHash,
};

const {funcs, getUrlFuncs, options, requiredParameters, scenarioDurationGauge, scenarios} =
  getSequentialTestScenarios(tests);

export {funcs, getUrlFuncs, options, requiredParameters, scenarioDurationGauge, scenarios};
