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

import {getSequentialTestScenarios} from '../../lib/common.js';

// import test modules
import * as contractCallTokenCustomFeesEstimate from './contractCallTokenCustomFeesEstimate.js';
import * as contractCallNftEstimate from './contractCallNftEstimate.js';
import * as contractCallEstimateNftCustomFees from './contractCallEstimateNftCustomFees.js';
import * as contractCallTokenDissociateEstimate from './contractCallTokenDissociateEstimate.js';
import * as contractCallTokenEstimate from './contractCallTokenEstimate.js';

const testsEstimate = {
  contractCallTokenCustomFeesEstimate,
  contractCallEstimateNftCustomFees,
  contractCallNftEstimate,
  contractCallTokenEstimate,
  contractCallTokenDissociateEstimate,
};

const {funcs, options, scenarioDurationGauge, scenarios} = getSequentialTestScenarios(testsEstimate);

export {funcs, options, scenarioDurationGauge, scenarios};
