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

import http from 'k6/http';

import {TestScenarioBuilder} from '../../lib/common.js';
import {accountListName, urlPrefix} from '../../lib/constants.js';
import {isValidListResponse} from './common.js';
import {setupTestParameters} from './bootstrapEnvParameters.js';

const urlTag = '/accounts';

const {options, run} = new TestScenarioBuilder()
  .name('rampUp') // use unique scenario name among all tests
  .tags({url: urlTag})
  .scenario({
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      {
        duration: __ENV.DEFAULT_RAMPUP_DURATION || __ENV.DEFAULT_DURATION,
        target: __ENV.DEFAULT_RAMPUP_VUS || __ENV.DEFAULT_VUS,
      },
    ],
    gracefulRampDown: '0s',
  })
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL']}${urlPrefix}${urlTag}?limit=${testParameters['DEFAULT_LIMIT']}`;
    return http.get(url);
  })
  .check('Accounts OK', (r) => isValidListResponse(r, accountListName))
  .build();

export {options, run};

export const setup = setupTestParameters;