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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Category Partition (CP) and Boundary Value Analysis (BVA) tests for
 * {@link CacheMap#put(Object, Object)}.
 *
 * <p>The {@code put} method is the primary entry point for data insertion in the {@link CacheMap}.
 * Its responsibility is to manage the multi-tier storage architecture, ensuring that objects
 * are stored in the appropriate internal map (Pinned, Hard, or Soft) based on their status
 * and the current capacity of the system.</p>
 *
 * <p>Unlike stateless constructors, the behavior of {@code put} is state-dependent.
 * To ensure deterministic results and full coverage of internal transitions,
 * three <b>Environmental Setup Scenarios</b> are defined:</p>
 * <ul>
 * <li><b>CACHE_STANDARD:</b> {@code maxSize = 2}, 1 existing entry. Tests nominal
 * insertions and updates without overflow.</li>
 * <li><b>CACHE_FULL:</b> {@code maxSize = 2}, 2 existing entries plus one entry
 * already migrated to {@code softMap}. Tests the boundary of capacity and the
 * overflow/promotion logic.</li>
 * <li><b>CACHE_PINNED_PRIORITY:</b> {@code maxSize = 2}, key {@code "X"} is
 * pre-pinned via {@code pin("X")} and present in the {@code pinnedMap}.
 * Tests the priority of pinned objects over standard storage.</li>
 * </ul>
 *
 * <p><b>Base choice strategy:</b> the baseline configuration corresponds to
 * the simplest possible insertion path — {@code CACHE_STANDARD} setup with an
 * existing key being updated to a new value. This avoids interactions with
 * overflow or pinning sub-mechanisms.</p>
 *
 * <h3>Outcome legend</h3>
 * <ul>
 * <li><b>OK_INSERT</b>    — {@code put} returns {@code null}; key is now retrievable via {@code get}.</li>
 * <li><b>OK_UPDATE</b>    — {@code put} returns the previous value; {@code get} returns the new value.</li>
 * <li><b>OK_PROMOTION</b> — Key was in {@code softMap}; {@code put} returns the previous soft value,
 *     and the new value is now retrievable from {@code cacheMap}.</li>
 * <li><b>OK_OVERFLOW</b>  — {@code cacheMap} was full; insertion succeeds and total size reflects
 *     migration of the eldest entry to {@code softMap}.</li>
 * <li><b>OK_PINNED</b>    — Key is in {@code pinnedMap}; update occurs there exclusively,
 *     {@code getPinnedKeys()} still contains the key.</li>
 * </ul>
 *
 * <h3>Category Partition (CP) Table</h3>
 * <pre>
 * +----+-----------------------+----------+---------+------------------------------------------------------------+
 * | #  | Setup Scenario        | Key      | Value   | Logical Context / Expected Outcome                         |
 * +----+-----------------------+----------+---------+------------------------------------------------------------+
 * | 1  | CACHE_STANDARD        | New      | Non-Null| OK_INSERT (Standard insertion)                             |
 * | 2  | CACHE_STANDARD        | Existing | New     | OK_UPDATE (Hard reference update) [BASELINE]               |
 * | 3  | CACHE_STANDARD        | Existing | Idem    | OK_UPDATE (Idempotent update)                              |
 * | 4  | CACHE_STANDARD        | New      | Null    | Hyp.: THROWS / Out: OK_INSERT (null value accepted)        |
 * | 5  | CACHE_STANDARD        | Null     | Null    | Hyp.: THROWS / Out: OK_INSERT (null key accepted by        |
 * |    |                       |          |         | cacheMap via sentinel; softMap not reached, no overflow)   |
 * | 6  | CACHE_FULL            | New      | Non-Null| OK_OVERFLOW (Eviction to SoftMap triggered)                |
 * | 7  | CACHE_FULL            | In Soft  | Non-Null| OK_PROMOTION (Soft-to-Hard migration)                      |
 * | 8  | CACHE_PIN_PRIORITY    | Pinned   | New     | OK_PINNED (Update within pinnedMap)                        |
 * | 9  | CACHE_PIN_PRIORITY    | Pinned   | Idem    | OK_PINNED (Idempotent update on Pinned)                    |
 * | 10 | CACHE_PIN_PRIORITY    | New      | Non-Null| OK_INSERT (pinnedMap remains unaffected)                   |
 * +----+-----------------------+----------+---------+------------------------------------------------------------+
 * </pre>
 *
 * <h3>Boundary Value Analysis (BVA) Table — mapped 1-to-1 with CP rows</h3>
 * <pre>
 * +----+-----------------------+----------+---------+---------+----------------------------------------------------+
 * | #  | Setup                 | Key      | Value   | maxSize | Expected Output                                    |
 * +----+-----------------------+----------+---------+---------+----------------------------------------------------+
 * | 1  | CACHE_STANDARD        | "k_new"  | "v_new" |    2    | OK_INSERT                                          |
 * | 2  | CACHE_STANDARD        | "k_old"  | "v_new" |    2    | OK_UPDATE (baseline)                               |
 * | 3  | CACHE_STANDARD        | "k_old"  | "v_old" |    2    | OK_UPDATE (idempotent)                             |
 * | 4  | CACHE_STANDARD        | "k_new"  | null    |    2    | Hyp.: THROWS / Out: OK_INSERT (null value accepted)|
 * | 5  | CACHE_STANDARD        | null     | null    |    2    | Hyp.: THROWS / Out: OK_INSERT (null key accepted   |
 * |    |                       |          |         |         | by cacheMap sentinel; softMap not reached)         |
 * | 6  | CACHE_FULL            | "k_new"  | "v_new" |    2    | OK_OVERFLOW                                        |
 * | 7  | CACHE_FULL            | "k_soft" | "v_new" |    2    | OK_PROMOTION                                       |
 * | 8  | CACHE_PIN_PRIORITY    | "X"      | "v_new" |    2    | OK_PINNED                                          |
 * | 9  | CACHE_PIN_PRIORITY    | "X"      | "v_pin" |    2    | OK_PINNED (idempotent)                             |
 * | 10 | CACHE_PIN_PRIORITY    | "k_new"  | "v_new" |    2    | OK_INSERT                                          |
 * +----+-----------------------+----------+---------+---------+----------------------------------------------------+
 * </pre>
 *
 * <p><b>Implementation Note:</b> This test suite uses {@code @ParameterizedTest}
 * with a {@code MethodSource} to ensure 1-to-1 traceability between the rows of
 * the tables above and the executed JUnit cases.</p>
 *
 * <h3>Null-key acceptance and the Null-Demotion Anomaly</h3>
 * <p>
 * The BVA suite reveals an asymmetric null-tolerance across the three internal maps of
 * {@link CacheMap}. The {@code cacheMap} (OpenJPA {@code ConcurrentHashMap}) accepts null
 * keys via an internal sentinel ({@code key == null ? NULL_KEY : key}), so {@code put(null, v)}
 * and {@code get(null)} complete normally as long as the entry remains in {@code cacheMap}.
 * The {@code pinnedMap} similarly accepts null keys, as confirmed by {@link #testPinNullKeyAnomaly()}.
 * The {@code softMap} ({@code ConcurrentReferenceHashMap}), however, structurally forbids null
 * references — a soft reference cannot wrap a null object — and raises
 * {@link IllegalArgumentException}("Null references not supported") on any attempt to insert
 * a null key.
 * </p>
 * <p>
 * This asymmetry is invisible to the caller until an overflow event forces the demotion of a
 * null-keyed entry from {@code cacheMap} to {@code softMap}. The {@link IllegalArgumentException}
 * is raised not by the original {@code put(null, v)} but by the subsequent {@code put} that
 * triggered the overflow, making the root cause non-obvious. The demotion also violates atomicity:
 * the entry is removed from {@code cacheMap} before the exception is thrown, so it is silently lost.
 * </p>
 * <p>
 * The nominal BVA cases #4 and #5 are not affected: both use {@code CACHE_STANDARD}, a
 * configuration with one free slot where no overflow is triggered and the null-keyed entry
 * never reaches {@code softMap}. Both therefore complete as OK_INSERT.
 * The anomaly is isolated in {@link #testNullEntryDemotionAnomaly()}.
 * </p>
 *
 * <h3>pin(null) behaviour</h3>
 * <p>
 * {@link #testPinNullKeyAnomaly()} documents that {@code pin(null)} is safe in both the
 * empty-cache and post-put scenarios. In the empty-cache case, {@code cacheMap.remove(null)}
 * returns null (key absent) and an internal guard prevents {@code softMap.remove(null)} from
 * being reached; the null key is installed directly in {@code pinnedMap}. After
 * {@code put(null, null)}, the entry is transferred from {@code cacheMap} to {@code pinnedMap}
 * without touching {@code softMap}, so no exception is raised and the value remains accessible.
 * </p>
 */
@DisplayName("CacheMap — BVA: put(Object key, Object value)")
public class Isw2CacheMapPutBVATest {

    private static final int BASELINE_MAX_SIZE = 2;

    private static final String K_OLD     = "k_old";
    private static final String V_OLD     = "v_old";
    private static final String K_OLD2    = "k_old2";
    private static final String V_OLD2    = "v_old2";
    private static final String K_SOFT    = "k_soft";
    private static final String V_SOFT    = "v_soft";
    private static final String K_PIN     = "X";
    private static final String V_PIN     = "v_pin";
    private static final String K_NEW     = "k_new";
    private static final String V_NEW     = "v_new";

    private enum Setup {
        CACHE_STANDARD,
        CACHE_FULL,
        CACHE_PINNED_PRIORITY
    }

    private enum Outcome {
        OK_INSERT,
        OK_UPDATE,
        OK_PROMOTION,
        OK_OVERFLOW,
        OK_PINNED
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("#1  STD  + newKey/newVal",          Setup.CACHE_STANDARD,          K_NEW,   V_NEW,    Outcome.OK_INSERT),
                Arguments.of("#2  STD  + existKey/newVal [base]", Setup.CACHE_STANDARD,          K_OLD,   V_NEW,    Outcome.OK_UPDATE),
                Arguments.of("#3  STD  + existKey/idemVal",       Setup.CACHE_STANDARD,          K_OLD,   V_OLD,    Outcome.OK_UPDATE),
                Arguments.of("#4  STD  + newKey/null",            Setup.CACHE_STANDARD,          K_NEW,   null,     Outcome.OK_INSERT),
                Arguments.of("#5  STD  + null/null",              Setup.CACHE_STANDARD,          null,    null,     Outcome.OK_INSERT),
                Arguments.of("#6  FULL + newKey/newVal",          Setup.CACHE_FULL,              K_NEW,   V_NEW,    Outcome.OK_OVERFLOW),
                Arguments.of("#7  FULL + softKey/newVal",         Setup.CACHE_FULL,              K_SOFT,  V_NEW,    Outcome.OK_PROMOTION),
                Arguments.of("#8  PIN  + pinnedKey/newVal",       Setup.CACHE_PINNED_PRIORITY,   K_PIN,   V_NEW,    Outcome.OK_PINNED),
                Arguments.of("#9  PIN  + pinnedKey/idemVal",      Setup.CACHE_PINNED_PRIORITY,   K_PIN,   V_PIN,    Outcome.OK_PINNED),
                Arguments.of("#10 PIN  + newKey/newVal",          Setup.CACHE_PINNED_PRIORITY,   K_NEW,   V_NEW,    Outcome.OK_INSERT)
        );
    }

    private CacheMap cache;

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.clear();
            cache = null;
        }
    }

    private CacheMap buildCache(Setup setup) {
        CacheMap c = new CacheMap(false, BASELINE_MAX_SIZE, BASELINE_MAX_SIZE, 0.75f);
        switch (setup) {
            case CACHE_STANDARD:
                c.put(K_OLD, V_OLD);
                break;
            case CACHE_FULL:
                // Overflow K_SOFT into softMap, then refill cacheMap to capacity.
                c.put(K_SOFT, V_SOFT);
                c.put(K_OLD, V_OLD);
                c.put(K_OLD2, V_OLD2);
                // Post-condition: cacheMap = {k_old, k_old2}, softMap = {k_soft}.
                break;
            case CACHE_PINNED_PRIORITY:
                c.put(K_PIN, V_PIN);
                c.pin(K_PIN);
                c.put(K_OLD, V_OLD);
                break;
        }
        return c;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testPut(String caseId, Setup setup, Object key, Object value, Outcome expected) {
        cache = buildCache(setup);

        switch (expected) {

            case OK_INSERT: {
                // For case #5 (null key): CACHE_STANDARD has one free slot, so no overflow
                // is triggered and softMap is never reached. The null key is stored in
                // cacheMap via sentinel and is retrievable without exception.
                assertFalse(cache.containsKey(key),
                        "[" + caseId + "] precondition violated: key already present");

                Object prev = cache.put(key, value);

                assertNull(prev, "[" + caseId + "] expected null return for new insertion");
                assertEquals(value, cache.get(key),
                        "[" + caseId + "] inserted value not retrievable via get()");

                if (setup == Setup.CACHE_PINNED_PRIORITY) {
                    Set<?> pinned = cache.getPinnedKeys();
                    assertTrue(pinned.contains(K_PIN),
                            "[" + caseId + "] pinned key disappeared after unrelated insertion");
                    assertFalse(pinned.contains(key),
                            "[" + caseId + "] new key wrongly landed in pinnedMap");
                }
                break;
            }

            case OK_UPDATE: {
                assertEquals(V_OLD, cache.get(key),
                        "[" + caseId + "] precondition violated: existing value mismatch");

                Object prev = cache.put(key, value);

                assertEquals(V_OLD, prev,
                        "[" + caseId + "] expected previous value to be returned on update");
                assertEquals(value, cache.get(key),
                        "[" + caseId + "] updated value not retrievable via get()");
                break;
            }

            case OK_PROMOTION: {
                assertEquals(V_SOFT, cache.get(key),
                        "[" + caseId + "] precondition violated: soft entry not retrievable");

                Object prev = cache.put(key, value);

                assertEquals(V_SOFT, prev,
                        "[" + caseId + "] expected previous soft value to be returned");
                assertEquals(value, cache.get(key),
                        "[" + caseId + "] promoted value not retrievable via get()");
                break;
            }

            case OK_OVERFLOW: {
                assertFalse(cache.containsKey(key),
                        "[" + caseId + "] precondition violated: key should be new");
                int sizeBefore = cache.size();

                Object prev = cache.put(key, value);

                assertNull(prev, "[" + caseId + "] expected null return for new key");
                assertEquals(value, cache.get(key),
                        "[" + caseId + "] inserted value not retrievable after overflow");
                assertTrue(cache.size() >= sizeBefore - 1,
                        "[" + caseId + "] size dropped unexpectedly after overflow");
                break;
            }

            case OK_PINNED: {
                Set<?> pinnedBefore = cache.getPinnedKeys();
                assertTrue(pinnedBefore.contains(key),
                        "[" + caseId + "] precondition violated: key not pinned");

                Object prev = cache.put(key, value);

                assertEquals(V_PIN, prev,
                        "[" + caseId + "] expected previous pinned value to be returned");
                assertEquals(value, cache.get(key),
                        "[" + caseId + "] updated pinned value not retrievable via get()");

                assertTrue(cache.getPinnedKeys().contains(key),
                        "[" + caseId + "] pinned key must remain in pinnedMap after update");
                break;
            }
        }
    }

    /* ---------------- Anomaly probe: (null, null) demotion -------------- */

    /**
     * Anomaly test — exposes the asymmetric null-tolerance between {@code cacheMap}
     * and {@code softMap} inside {@link CacheMap}.
     *
     * <p><b>Scenario.</b> A {@code (null, null)} entry is accepted by the primary
     * {@code cacheMap} (OpenJPA's {@code ConcurrentHashMap} handles null keys via an
     * internal sentinel). As long as the entry remains in {@code cacheMap}, the cache
     * behaves correctly: {@code put} returns normally and {@code get(null)} retrieves null.</p>
     *
     * <p><b>Anomaly.</b> When subsequent insertions saturate the {@code cacheMap}, the
     * overflow logic ({@code cacheMapOverflowRemoved}) attempts to demote the eldest
     * entry — {@code (null, null)} — into {@code softMap} via
     * {@code ConcurrentReferenceHashMap.put}. That structure structurally forbids null
     * references and raises {@link IllegalArgumentException}("Null references not supported").
     * The exception propagates from the {@code put("k2","v2")} call that triggered the
     * overflow, not from the original {@code put(null, null)}.</p>
     *
     * <p><b>Atomicity violation.</b> By the time the exception is thrown, the null-keyed
     * entry has already been removed from {@code cacheMap} by the overflow logic, leaving
     * the cache in an inconsistent state: the entry is neither in {@code cacheMap} nor
     * in {@code softMap}.</p>
     */
    @Test
    @DisplayName("ANOMALY: (null,null) accepted by cacheMap but throws IllegalArgumentException on demotion to softMap")
    public void testNullEntryDemotionAnomaly() {
        cache = new CacheMap(true , 2, 2, 0.75f);

        // Step 1 — Insert (null, null): cacheMap accepts it via sentinel, no exception.
        Object prev = cache.put(null, null);
        assertNull(prev, "first insertion must return null (no previous mapping)");

        // Step 2 — Entry is in cacheMap: both get(null) and containsKey(null) work correctly.
        assertNull(cache.get(null), "value mapped to null key must be null");
        assertTrue(cache.containsKey(null), "(null,null) entry must be present in cacheMap");

        // Step 3 — Fill the remaining slot. No overflow yet; null entry still reachable.
        cache.put("k1", "v1");
        assertTrue(cache.containsKey(null), "(null,null) must still be reachable before overflow");

        // Step 4 — Trigger overflow. The eldest entry (null,null) is demoted to softMap,
        // which rejects null references with IllegalArgumentException.
        // The exception originates here, not at the original put(null,null).
        assertThrows(IllegalArgumentException.class,
                () -> cache.put("k2", "v2"),
                "demotion of (null,null) to softMap must raise IllegalArgumentException");
    }

    /* ------------------ pin(null) behaviour probe ----------------------- */

    /**
     * Tests the behaviour of {@link CacheMap#pin(Object)} when invoked with a null key.
     *
     * <p><b>Sub-scenario A — empty cache.</b> {@code pin(null)} completes without exception.
     * Internally, {@code cacheMap.remove(null)} returns null (key absent) and an early-exit
     * guard prevents {@code softMap.remove(null)} from being reached. The null key is
     * correctly installed in {@code pinnedMap}.</p>
     *
     * <p><b>Sub-scenario B — after {@code put(null,null)}.</b> The null-keyed entry sits in
     * {@code cacheMap}. {@code pin(null)} transfers it to {@code pinnedMap} without touching
     * {@code softMap}, so no exception is raised. The value remains accessible via
     * {@code get(null)}. This confirms that the null-tolerance asymmetry between {@code cacheMap}
     * and {@code softMap} does not affect the pin path.</p>
     */
    @Test
    @DisplayName("pin(null): null key is accepted and correctly installed in pinnedMap")
    void testPinNullKeyAnomaly() {

        // ----- Sub-scenario A: empty cache ------------------------------
        cache = new CacheMap(true , 2, 2, 0.75f);
        assertDoesNotThrow(
                () -> cache.pin(null),
                "pin(null) on an empty cache must not throw");
        assertTrue(cache.getPinnedKeys().contains(null),
                "null key must appear in pinnedMap after pin(null) on empty cache");

        cache.clear();
        cache = null;

        // ----- Sub-scenario B: after put(null, null) --------------------
        cache = new CacheMap(true , 2, 2, 0.75f);
        cache.put(null, null);
        assertTrue(cache.containsKey(null),
                "precondition: (null,null) must be present in cacheMap before pin");

        assertDoesNotThrow(
                () -> cache.pin(null),
                "pin(null) after put(null,null) must not throw");

        assertTrue(cache.getPinnedKeys().contains(null),
                "null key must appear in pinnedMap after pin(null)");
        assertNull(cache.get(null),
                "value mapped to null key must still be null after pinning");
    }
}
