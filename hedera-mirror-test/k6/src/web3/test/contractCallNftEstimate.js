/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed ato in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {ContractCallScenarioBuilder} from './common.js';


const {options, run} = new ContractCallScenarioBuilder()
                           .block('latest')
                           .data('d85f74c10000000000000000000000000000000000000000000000000000000000000559')
                           .to('000000000000000000000000000000000000055d')
                           .gas(15000000)
                           .from('0000000000000000000000000000000000000559')
                           .value(812000000)
                           .name('contractCallNftEstimate')
                           .sleep(1)
                           .build();

export {options, run};