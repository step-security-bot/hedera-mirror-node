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

package com.hedera.mirror.web3.repository;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_TOKEN;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_NFT;

import com.hedera.mirror.common.domain.token.AbstractNft;
import com.hedera.mirror.common.domain.token.Nft;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NftRepository extends CrudRepository<Nft, AbstractNft.Id> {

    @Override
    @Cacheable(cacheNames = CACHE_NAME_NFT, cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    Optional<Nft> findById(AbstractNft.Id id);

    @Query(
            value = "select n.* from Nft n "
                    + "left join Entity e on e.id = n.token_id "
                    + "where n.token_id=:tokenId and n.serial_number=:serialNumber "
                    + "and n.deleted is false and e.deleted is not true",
            nativeQuery = true)
    Optional<Nft> findActiveById(long tokenId, long serialNumber);

    /**
     * Retrieves the most recent state of an nft by its ID up to a given block timestamp.
     * The method considers both the current state of the nft and its historical states
     * and returns the one that was valid just before or equal to the provided block timestamp.
     *
     * @param tokenId the token id of the nft to be retrieved.
     * @param serialNumber the serial number of the nft to be retrieved.
     * @param blockTimestamp the block timestamp used to filter the results.
     * @return an Optional containing the nft's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(
            value =
                    """
            select n.*
            from (
                (
                    select *
                    from nft
                    where token_id = :tokenId
                        and serial_number = :serialNumber
                        and lower(timestamp_range) <= :blockTimestamp
                        and deleted is not true
                )
                union all
                (
                    select *
                    from nft_history
                    where token_id = :tokenId
                        and serial_number = :serialNumber
                        and lower(timestamp_range) <= :blockTimestamp
                        and deleted is not true
                    order by lower(timestamp_range) desc
                    limit 1
                )
            ) as n
            join entity e on e.id = n.token_id
            where (e.deleted is not true or lower(e.timestamp_range) > :blockTimestamp)
            order by n.timestamp_range desc
            limit 1""",
            nativeQuery = true)
    Optional<Nft> findActiveByIdAndTimestamp(long tokenId, long serialNumber, long blockTimestamp);

    @Query(
            value = "select count(*) from Nft n "
                    + "join Entity e on e.id = n.token_id "
                    + "where n.account_id=:accountId and n.deleted is false and e.deleted is not true",
            nativeQuery = true)
    long countByAccountIdNotDeleted(Long accountId);
}
