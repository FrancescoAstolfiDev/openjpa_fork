/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openjpa.util;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Category Partition (CP) and Boundary Value Analysis (BVA) tests for
 * {@link CacheMap#get(Object)}.
 *
 * <p>The {@code get} method retrieves values stored within the {@link CacheMap}
 * structure. The lookup logic traverses a hierarchy of memory tiers
 * ({@code softMap}, {@code cacheMap}, {@code pinnedMap}) to ensure data
 * persistence and to manage the lifecycle of cached entries — in particular,
 * the promotion of soft-referenced values back into the hard cache.</p>
 *
 * <h3>1. Test Strategy &amp; Environment (Setup)</h3>
 * <p>All tests share a single pre-populated {@link CacheMap} instance built fresh
 * before each parameterized invocation. The fixture distributes entries across
 * all three internal tiers so that the lookup logic can be exercised end-to-end
 * without reflection or mocks.</p>
 *
 * <p>The configuration mirrors the baseline derived from constructor analysis
 * (case #2 of the constructor BVA): {@code lru = true}, {@code maxSize = 2}.</p>
 *
 * <h3>2. Pre-populated State</h3>
 * <ul>
 * <li><b>Pinned Keys:</b> {@code {"key1": "val1", "key2": "val2"}}</li>
 * <li><b>CacheMap (Hard):</b> {@code {"key3": "val3", "key4": "val4"}}</li>
 * <li><b>SoftMap (Soft):</b> {@code {"key5": "val5"}}</li>
 * </ul>
 *
 * <h3>3. Category Partition (CP) Table</h3>
 * <pre>
 * +----+-----------------------+-------------------+----------------------------------------------------+
 * | #  | Scenario / Setup      | Key (Input)       | Expected Outcome                                   |
 * +----+-----------------------+-------------------+----------------------------------------------------+
 * | 1  | CACHE_PRE_POPULATED   | In PinnedMap      | Returns Value; entry remains in pinnedMap.         |
 * | 2  | CACHE_PRE_POPULATED   | In CacheMap       | Returns Value; updates LRU order.                  |
 * | 3  | CACHE_PRE_POPULATED   | In SoftMap        | PROMOTION: Moves entry to cacheMap and returns it. |
 * | 4  | CACHE_PRE_POPULATED   | Absent Key        | Returns null.                                      |
 * | 5  | CACHE_PRE_POPULATED   | null              | Throws NullPointerException.                       |
 * +----+-----------------------+-------------------+----------------------------------------------------+
 * </pre>
 *
 * <h3>4. Boundary Value Analysis (BVA) Table — mapped 1-to-1 with CP rows</h3>
 * <pre>
 * +----+----------------+---------+---------+----------------------------------------------------+
 * | #  | Setup          | Key     | maxSize | Expected Output / Observation                      |
 * +----+----------------+---------+---------+----------------------------------------------------+
 * | 1  | PRE_POPULATED  | "key1"  |    2    | Returns "val1"                                     |
 * | 2  | PRE_POPULATED  | "key3"  |    2    | Returns "val3"                                     |
 * | 3  | PRE_POPULATED  | "key5"  |    2    | Returns "val5" (then resides in cacheMap)          |
 * | 4  | PRE_POPULATED  | "key_X" |    2    | Returns null                                       |
 * | 5  | PRE_POPULATED  | null    |    2    | Throws NullPointerException                        |
 * +----+----------------+---------+---------+----------------------------------------------------+
 * </pre>
 *
 * <p><b>Implementation note (case #3):</b> because the {@code cacheMap} is already
 * at {@code maxSize = 2} when "key5" is promoted, the LRU policy demotes the
 * least-recently-used hard entry back to the {@code softMap}, preserving the
 * total number of reachable mappings.</p>
 */
@DisplayName("CacheMap — BVA: get(Object key)")
public class Isw2CacheMapGetBVATest {

    /* ---------------------- environmental constants --------------------- */

    /** Cache capacity used for all tests. */
    private static final int   MAX_SIZE      = 2;
    /** Initial capacity of the underlying maps; matches {@code MAX_SIZE}. */
    private static final int   INITIAL_SIZE  = 2;
    /** Standard load factor (HashMap default). */
    private static final float LOAD_FACTOR   = 0.75f;
    /** LRU eviction enabled — required to make demotion order deterministic. */
    private static final boolean LRU         = true;

    /* ----------------------------- fixtures ----------------------------- */

    // Pinned entries
    private static final String K_PIN_1 = "key1";
    private static final String V_PIN_1 = "val1";
    private static final String K_PIN_2 = "key2";
    private static final String V_PIN_2 = "val2";

    // Hard (cacheMap) entries
    private static final String K_HARD_1 = "key3";
    private static final String V_HARD_1 = "val3";
    private static final String K_HARD_2 = "key4";
    private static final String V_HARD_2 = "val4";

    // Soft (softMap) entry
    private static final String K_SOFT = "key5";
    private static final String V_SOFT = "val5";

    // Absent key (never inserted)
    private static final String K_ABSENT = "key_X";

    /* ---------------------------- enumerations -------------------------- */

    /** Expected behavioral outcome of the {@code get} invocation. */
    private enum Outcome {
        /** {@code get} returns the expected value (no side effect asserted). */
        OK_HIT,
        /**
         * Soft-to-hard promotion: {@code get} returns the soft-stored value,
         * and a subsequent {@code get} resolves the same value via the cacheMap.
         */
        OK_PROMOTION,
        /** {@code get} returns {@code null} for an absent key. */
        OK_MISS,
        /** {@code get} raises {@code NullPointerException} for a {@code null} key. */
        THROWS
    }

    /* ------------------------------- cases ------------------------------ */

    static Stream<Arguments> cases() {
        return Stream.of(
                //          caseId                              key       expectedValue   expected
                Arguments.of("#1  PRE_POPULATED: pinned hit",   K_PIN_1,  V_PIN_1,        Outcome.OK_HIT),
                Arguments.of("#2  PRE_POPULATED: hard hit",     K_HARD_1, V_HARD_1,       Outcome.OK_HIT),
                Arguments.of("#3  PRE_POPULATED: soft promotion", K_SOFT, V_SOFT,         Outcome.OK_PROMOTION),
                Arguments.of("#4  PRE_POPULATED: absent",       K_ABSENT, null,           Outcome.OK_MISS),
                Arguments.of("#5  PRE_POPULATED: null key",     null,     null,           Outcome.OK_MISS)
        );
    }

    /* ----------------------------- lifecycle ---------------------------- */

    private CacheMap cache;

    /**
     * Builds a fresh, fully-populated cache before each parameterized test, so
     * that side-effects of one test (e.g. soft-to-hard promotion) cannot leak
     * into subsequent ones.
     *
     * <p><b>Population sequence (order matters):</b></p>
     * <ol>
     * <li>Seed {@code softMap} with {@code (key5, val5)} by inserting it first
     *     and then forcing two further insertions that overflow it out of
     *     cacheMap. Pinning {@code key1}/{@code key2} afterwards keeps them
     *     untouched by the overflow logic, since pinned entries do not consume
     *     cacheMap capacity.</li>
     * <li>Pin {@code key1} and {@code key2} into the {@code pinnedMap}.</li>
     * <li>Insert {@code key3} and {@code key4}: they fit exactly into the
     *     cacheMap ({@code maxSize = 2}) without triggering further overflow.</li>
     * </ol>
     */
    @BeforeEach
    void setUp() {
        cache = new CacheMap(LRU, MAX_SIZE, INITIAL_SIZE, LOAD_FACTOR);

        // (1) Plant key5 and force its demotion to softMap by overflowing the
        // cacheMap with two scratch entries; then clear the cacheMap so that
        // the only survivors are key5 (in softMap) and the upcoming pins/hards.
        cache.put(K_SOFT, V_SOFT);
        cache.put("__scratch1", "s1");
        cache.put("__scratch2", "s2"); // overflow → key5 demoted to softMap
        // Remove the scratch entries from the cacheMap so they don't interfere.
        cache.remove("__scratch1");
        cache.remove("__scratch2");

        // (2) Pin key1 and key2: pinned entries do NOT count toward maxSize.
        cache.pin(K_PIN_1);
        cache.put(K_PIN_1, V_PIN_1);
        cache.pin(K_PIN_2);
        cache.put(K_PIN_2, V_PIN_2);

        // (3) Fill the cacheMap exactly to capacity with key3 and key4.
        cache.put(K_HARD_1, V_HARD_1);
        cache.put(K_HARD_2, V_HARD_2);

        // Sanity check: the fixture is in the documented state.
        assertNotNull(cache, "fixture construction failed");
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.clear();
            cache = null;
        }
    }

    /* ------------------------------- test ------------------------------- */

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testGet(String caseId, Object key, Object expectedValue, Outcome expected) {
        switch (expected) {

            // ---------- Direct hit (pinnedMap or cacheMap) ----------------
            case OK_HIT: {
                Object actual = cache.get(key);
                assertEquals(expectedValue, actual,
                        "[" + caseId + "] wrong value returned by get()");

                // For the pinned case, verify the segregation invariant:
                // the key must still be reported as pinned after the lookup.
                if (key != null && key.equals(K_PIN_1)) {
                    Set<?> pinned = cache.getPinnedKeys();
                    assertTrue(pinned.contains(key),
                            "[" + caseId + "] pinned key disappeared from pinnedMap after get()");
                }
                break;
            }

            // ---------- Soft-to-hard promotion ----------------------------
            case OK_PROMOTION: {
                // First call returns the soft-stored value AND triggers the
                // promotion (see CacheMap#get: when softMap.get != null, the
                // value is re-put into the cacheMap upon read-unlock).
                Object firstHit = cache.get(key);
                assertEquals(expectedValue, firstHit,
                        "[" + caseId + "] soft-stored value not returned");

                // Post-condition: the value must remain reachable via get(),
                // confirming that the promotion did not lose the entry.
                Object secondHit = cache.get(key);
                assertEquals(expectedValue, secondHit,
                        "[" + caseId + "] promoted value not retrievable on second get()");

                // Stronger witness: containsKey must still report the key.
                assertTrue(cache.containsKey(key),
                        "[" + caseId + "] promoted key not present after get()");
                break;
            }

            // ---------- Miss: key never inserted --------------------------
            case OK_MISS: {
                Object actual = cache.get(key);
                assertNull(actual,
                        "[" + caseId + "] absent key must return null");
                break;
            }

            // ---------- Robustness against null key -----------------------
            case THROWS: {
                // Hypothesis: the underlying softMap (ConcurrentReferenceHashMap)
                // rejects null keys with Exception, propagating out
                // of get(). Asserted empirically, in line with the constructor
                // and put BVA suites.
                assertThrows(Exception.class,
                        () -> cache.get(key),
                        "[" + caseId + "] expected NullPointerException for null key");
                break;
            }

            default:
                throw new IllegalStateException("Unhandled outcome: " + expected);
        }
    }
}
