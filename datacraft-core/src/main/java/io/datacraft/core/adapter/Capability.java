/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.adapter;

/** Optional database features. Adapters must advertise only features they implement. */
public enum Capability {
    TRANSACTIONS,
    SAVEPOINTS,
    SCHEMAS,
    MATERIALIZED_VIEWS,
    STORED_PROCEDURES,
    EXPLAIN_PLANS,
    EDITABLE_RESULTS,
    RETURNING_CLAUSE,
    MULTIPLE_DATABASES
}
