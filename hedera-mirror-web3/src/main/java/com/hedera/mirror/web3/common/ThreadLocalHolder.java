/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.common;

import com.hedera.mirror.web3.evm.store.CachingStateFrame;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import org.hyperledger.besu.datatypes.Address;

@Named
public class ThreadLocalHolder {

    /** Boolean flag which determines whether we should make a contract call or contract create transaction simulation */
    @NonNull
    public static final ThreadLocal<Boolean> isCreate = ThreadLocal.withInitial(() -> false);

    @NonNull
    public static final ThreadLocal<Map<Address, Address>> aliases = ThreadLocal.withInitial(HashMap::new);

    @NonNull
    public static final ThreadLocal<Map<Address, Address>> pendingAliases = ThreadLocal.withInitial(HashMap::new);

    @NonNull
    public static final ThreadLocal<Set<Address>> pendingRemovals = ThreadLocal.withInitial(HashSet::new);
    /** Current top of stack (which is all linked together) */
    @NonNull
    public static final ThreadLocal<CachingStateFrame<Object>> stack = ThreadLocal.withInitial(() -> null);

    /** Fixed "base" of stack: a R/O cache frame on top of the DB-backed cache frame */
    @NonNull
    public static final ThreadLocal<CachingStateFrame<Object>> stackBase = ThreadLocal.withInitial(() -> null);

    private ThreadLocalHolder() {}

    /** Chop the stack back to its base. This keeps the most-upstream-layer which connects to the database, and the
     * `ROCachingStateFrame` on top of it.  Therefore, everything already read from the database is still present,
     * unchanged, in the stacked cache.  (Usage case is the multiple calls to `eth_estimateGas` in order to "binary
     * search" to the closest gas approximation for a given contract call: The _first_ call is the only one that actually
     * hits the database (via the database accessors), all subsequent executions will fetch the same values
     * (required!) from the RO-cache without touching the database again - if you cut back the stack between executions
     * using this method.)
     */
    public static void resetToBase() {
        stack.set(stackBase.get());
    }

    public static void cleanThread() {
        resetState();
        stack.remove();
        stackBase.remove();
    }

    public static void resetState() {
        resetToBase();
        isCreate.remove();
        aliases.remove();
        pendingAliases.remove();
        pendingRemovals.remove();
    }
}
