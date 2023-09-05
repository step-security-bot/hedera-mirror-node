/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import {ContractCallScenarioBuilder, buildScenario, getParameterFromEnv} from './common.js';

const COMMA_SEPARATOR = ',';

const allData = getParameterFromEnv(
  __ENV.DATA,
  '0488c93900000000000000000000000000000000000000000000000000000000000004f300000000000000000000000000000000000000000000000000000000000004fe' +
    COMMA_SEPARATOR +
    '0488c9390000000000000000000000000000000000000000000000000000000000000501000000000000000000000000000000000000000000000000000000000000050c'
);
const allTo = getParameterFromEnv(
  __ENV.TO,
  '00000000000000000000000000000000000004f5' + COMMA_SEPARATOR + '0000000000000000000000000000000000000503'
);
const allFrom = getParameterFromEnv(
  __ENV.FROM,
  '00000000000000000000000000000000000004f3' + COMMA_SEPARATOR + '0000000000000000000000000000000000000501'
);

const BLOCK = __ENV.BLOCK || 'latest';
const DATA = allData[__VU % allData.length];
const TO = allTo[__VU % allTo.length];
const GAS = __ENV.GAS || 15000000;
const FROM = allFrom[__VU % allFrom.length];
const VALUE = __ENV.VALUE || 1650000000;
const SLEEP = __ENV.SLEEP || 1;

const params = {
  BLOCK: BLOCK,
  DATA: DATA,
  TO: TO,
  GAS: GAS,
  FROM: FROM,
  VALUE: VALUE,
  NAME: 'contractCallNftCustomFeesEstimate',
  SLEEP: SLEEP,
};

const {options, run} = buildScenario(params);

export {options, run};
