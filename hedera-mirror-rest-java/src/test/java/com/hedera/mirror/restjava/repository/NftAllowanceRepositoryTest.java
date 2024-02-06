/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class NftAllowanceRepositoryTest extends RestJavaIntegrationTest {

    private final NftAllowanceRepository nftAllowanceRepository;

    @Test
    void findByOwnerTokenEq() {
        var nftAllowance = domainBuilder.nftAllowance().get();
        nftAllowanceRepository.save(nftAllowance);
        assertThat(nftAllowanceRepository
                        .findByOwnerAndTokenEq(nftAllowance.getOwner(), nftAllowance.getTokenId(), 1)
                        .get(0))
                .isEqualTo(nftAllowance);
    }

    @Test
    void findByOwnerTokenGte() {
        var nftAllowance = domainBuilder.nftAllowance().get();
        nftAllowanceRepository.save(nftAllowance);
        assertThat(nftAllowanceRepository
                        .findBySpenderAndTokenGte(nftAllowance.getSpender(), nftAllowance.getTokenId(), 1)
                        .get(0))
                .isEqualTo(nftAllowance);
    }
}
