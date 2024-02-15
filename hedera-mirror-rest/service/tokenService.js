/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import {Token, TokenAccount} from '../model';
import BaseService from './baseService';
import {OrderSpec} from '../sql';
import _ from 'lodash';
import quickLru from 'quick-lru';
import config from '../config.js';

const tokenCache = new quickLru({
  maxSize: config.cache.token.maxSize,
});

/**
 * Token retrieval business logic
 */
class TokenService extends BaseService {
  static tokenRelationshipsQuery = `
        select ${TokenAccount.getFullName(TokenAccount.AUTOMATIC_ASSOCIATION)},
               ${TokenAccount.getFullName(TokenAccount.BALANCE)},
               ${TokenAccount.getFullName(TokenAccount.CREATED_TIMESTAMP)},
               ${TokenAccount.getFullName(TokenAccount.FREEZE_STATUS)},
               ${TokenAccount.getFullName(TokenAccount.KYC_STATUS)},
               ${TokenAccount.getFullName(TokenAccount.TOKEN_ID)}
        from ${TokenAccount.tableName} ${TokenAccount.tableAlias}
        where ${TokenAccount.tableAlias}.${TokenAccount.ACCOUNT_ID} = $1
        and ${TokenAccount.tableAlias}.${TokenAccount.ASSOCIATED} = true `;

  static tokenDecimalsQuery = `
        select ${Token.TOKEN_ID}, ${Token.DECIMALS} 
        from ${Token.tableName}
        where ${Token.TOKEN_ID} = any ($1)`;

  /**
   * Gets the full sql query and params to retrieve an account's token relationships
   *
   * @param query
   * @return {{sqlQuery: string, params: *[]}}
   */
  getTokenRelationshipsQuery(query) {
    const {conditions, inConditions, order, ownerAccountId, limit} = query;
    const params = [ownerAccountId, limit];
    // This is the inner query to get the latest balance for a token, account pair.
    const moreConditionsExist = conditions.length > 0 ? ` and ` : ``;
    const conditionClause =
      moreConditionsExist +
      conditions
        .map((condition) => {
          params.push(condition.value);
          return `${TokenAccount.getFullName(condition.key)} ${condition.operator} $${params.length}`;
        })
        .join(' and ');
    let inConditionClause = this.getInClauseSubQuery(inConditions, params);
    const limitClause = super.getLimitQuery(2);
    const orderClause = this.getOrderByQuery(OrderSpec.from(TokenAccount.getFullName(TokenAccount.TOKEN_ID), order));
    const sqlQuery = [
      TokenService.tokenRelationshipsQuery,
      conditionClause,
      inConditionClause,
      orderClause,
      limitClause,
    ].join('\n');
    return {sqlQuery, params};
  }

  /**
   * Gets the In clause query for Token.id
   * @param inConditions
   * @param params
   * @returns {string}
   */
  getInClauseSubQuery(inConditions, params) {
    const tokenIdInParams = [];
    inConditions.forEach((condition) => {
      tokenIdInParams.push(condition.value);
    });

    if (!_.isEmpty(tokenIdInParams)) {
      return ` and ${TokenAccount.getFullName(TokenAccount.TOKEN_ID)} in (${tokenIdInParams})`;
    }
  }

  /**
   * Gets the token accounts for the query as well as the decimal from the token
   * table.
   *
   * @param query
   * @return {Promise<TokenAccount[]>}
   */
  async getTokens(query) {
    const {sqlQuery, params} = this.getTokenRelationshipsQuery(query);
    const rows = await super.getRows(sqlQuery, params);
    const tokenIds = new Set();
    const tokenAccounts = rows.map((row) => {
      tokenIds.add(row[TokenAccount.TOKEN_ID]);
      return new TokenAccount(row);
    });
    const tokenCacheMap = await this.getCachedTokens(tokenIds);
    tokenAccounts.forEach((tokenAccount) => {
      const decimals = tokenCacheMap.get(tokenAccount.tokenId);
      tokenAccount.decimals = decimals !== undefined ? decimals : null;
    });
    return tokenAccounts;
  }

  /**
   * Adds a value to the token cache
   * @param {BigInt} tokenId
   * @param {BigInt} decimals
   */
  putTokenCache(tokenId, decimals) {
    tokenCache.set(tokenId, decimals);
  }

  /**
   * Gets the token cache for a list of token ids
   * @param {Set<BigInt>} tokenIds
   * @return {Promise<Map<tokenId, decimals>>}
   */
  async getCachedTokens(tokenIds) {
    const uncachedTokenIds = [];
    const cachedDecimals = new Map();
    for (const tokenId of tokenIds) {
      const decimals = tokenCache.get(tokenId);
      if (decimals) {
        cachedDecimals.set(tokenId, decimals);
      } else {
        uncachedTokenIds.push(tokenId);
      }
    }

    if (_.isEmpty(uncachedTokenIds)) {
      return cachedDecimals;
    }

    const rows = await super.getRows(TokenService.tokenDecimalsQuery, [uncachedTokenIds]);
    rows.forEach((row) => {
      const tokenId = row[Token.TOKEN_ID];
      const decimals = row[Token.DECIMALS];
      this.putTokenCache(tokenId, decimals);
      cachedDecimals.set(tokenId, decimals);
    });

    return cachedDecimals;
  }
}

export default new TokenService();
