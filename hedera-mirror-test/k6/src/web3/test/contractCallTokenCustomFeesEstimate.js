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
  '8ba74da0000000000000000000000000000000000000000000000000000000000000050f0000000000000000000000000000000000000000000000000000000000000518' +
    COMMA_SEPARATOR +
    '8ba74da0000000000000000000000000000000000000000000000000000000000000051d0000000000000000000000000000000000000000000000000000000000000526'
);
const allTo = getParameterFromEnv(
  __ENV.TO,
  '0000000000000000000000000000000000000511' + COMMA_SEPARATOR + '000000000000000000000000000000000000051f'
);
const allFrom = getParameterFromEnv(
  __ENV.FROM,
  '000000000000000000000000000000000000050f' + COMMA_SEPARATOR + '000000000000000000000000000000000000051d'
);

const BLOCK = __ENV.BLOCK || 'latest';
const DATA = allData[__VU % allData.length];
const TO = allTo[__VU % allTo.length];
const GAS = __ENV.GAS || 15000000;
const FROM = allFrom[__VU % allFrom.length];
const VALUE = __ENV.VALUE || 1635000000;
const SLEEP = __ENV.SLEEP || 1;

const params = {
  BLOCK: BLOCK,
  DATA: DATA,
  TO: TO,
  GAS: GAS,
  FROM: FROM,
  VALUE: VALUE,
  NAME: 'contractCallTokenCustomFeesEstimate',
  SLEEP: SLEEP,
};

const {options, run} = buildScenario(params);

export {options, run};
