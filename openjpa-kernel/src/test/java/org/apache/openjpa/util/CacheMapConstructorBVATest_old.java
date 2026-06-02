/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openjpa.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Category Partition (CP) and Boundary Value Analysis (BVA) tests for
 * {@link CacheMap#CacheMap(boolean, int, int, float)}.
 *
 * <p>All tests exercise the constructor in isolation against the public {@link CacheMap}
 * API — no reflection on internal fields, no mocks. Since a constructor has no directly
 * observable output, the sanity of each constructed instance is validated through a
 * <b>Round Trip Test</b> used as a smoke probe: a small, fixed sequence of
 * {@code put(k, v)} followed by {@code get(k)}, asserting that {@code v.equals(get(k))}
 * for every entry inserted.
 *
 * <p><b>Round-trip scope:</b> the round-trip is intentionally limited to
 * {@value #SMOKE_TEST_ENTRIES} entries — well below the baseline {@code max} — because
 * the unit under test is the constructor, not the eviction or overflow logic of
 * {@code put}/{@code get}. Behaviours such as LRU eviction, soft-map overflow, the
 * exact threshold of {@code max}, and rehashing during fill-up are responsibility of
 * dedicated test classes ({@code Isw2CacheMapPutBVATest}, {@code Isw2CacheMapGetBVATest},
 * etc.) and are not asserted here even when a parameter under analysis influences them.
 *
 * <p><b>Base choice strategy:</b> the baseline configuration (case #1) corresponds to
 * {@code lru = false}, {@code max = 16}, {@code size = 8}, {@code load = 0.75f}. This
 * choice mirrors the implicit defaults of the {@link CacheMap#CacheMap()} no-arg
 * constructor — documented as <i>"a non-LRU (and therefore highly concurrent) cache
 * map"</i> — and the canonical load factor of the underlying {@code HashMap}. The
 * baseline acts as the fixed configuration for all subsequent cases: only one parameter
 * is varied per row (unidimensional strategy) to avoid the combinatorial explosion of
 * the Cartesian product.
 *
 * <p><b>Constructor selection:</b> the four-argument constructor was chosen over its
 * five-argument overload {@code CacheMap(boolean, int, int, float, int)} because the
 * additional {@code concurrencyLevel} parameter influences only the dimensioning of
 * internal synchronisation segments and does not contribute to the validation of the
 * functional correctness of the class.
 *
 * <h3>Outcome legend</h3>
 * <ul>
 *   <li><b>OK_RT</b>      — construction succeeds and the smoke round-trip recovers
 *                           every inserted entry.</li>
 *   <li><b>OK_NO_RT</b>   — construction succeeds; the smoke round-trip is intentionally
 *                           not asserted because subsequent {@code put}/{@code get} on
 *                           that configuration is undefined ({@code size=0} divides by
 *                           zero on the modulo-based hash index; {@code load=NaN}
 *                           collapses the resize threshold to 0).</li>
 *   <li><b>OK_RT_FAIL</b> — construction succeeds but the resulting instance is a
 *                           disabled cache: {@code put}/{@code get} short-circuit and
 *                           return {@code null}. Documented as a smoke-probe failure.</li>
 *   <li><b>THROWS</b>     — the constructor itself raises
 *                           {@code IllegalArgumentException}.</li>
 * </ul>
 *
 * <h3>Category Partition table</h3>
 * <pre>
 * +----+-------+-----------+------------+----------------+--------------------------------------------------------------+
 * |  # | lru   | max       | size       | load           | Expected output                                              |
 * +----+-------+-----------+------------+----------------+--------------------------------------------------------------+
 * |  1 | false | &gt;0     | &lt;max    | 0&lt;load≤1    | OK_RT                                                        |
 * |  2 | true  | &gt;0     | &lt;max    | 0&lt;load≤1    | OK_RT (LRU eviction not exercised here)                      |
 * |  3 | false | =0        | &lt;max    | 0&lt;load≤1    | Hyp.: Exception / Out: OK_RT_FAIL                            |
 * |  4 | false | =-1       | &lt;max    | 0&lt;load≤1    | Hyp.: Exception / Out: OK_RT (no-limit sentinel)             |
 * |  5 | false | &lt;-1    | &lt;max    | 0&lt;load≤1    | Hyp.: Exception / Out: OK_RT (treated as -1)                 |
 * |  6 | false | MAX_VALUE | &lt;max    | 0&lt;load≤1    | OK_RT                                                        |
 * |  7 | false | &gt;0     | =max       | 0&lt;load≤1    | OK_RT                                                        |
 * |  8 | false | &gt;0     | &gt;max    | 0&lt;load≤1    | OK_RT                                                        |
 * |  9 | false | &gt;0     | =0         | 0&lt;load≤1    | Hyp.: Exception / Out: OK_NO_RT                              |
 * | 10 | false | &gt;0     | =-1        | 0&lt;load≤1    | Hyp.: Exception / Out: OK_RT (rewritten to 500)              |
 * | 11 | false | &gt;0     | &lt;-1     | 0&lt;load≤1    | Hyp.: Exception / Out: OK_RT (treated as -1, rewritten 500)  |
 * | 12 | false | &gt;0     | &lt;max    | =0             | THROWS                                                       |
 * | 13 | false | &gt;0     | &lt;max    | &lt;0          | THROWS                                                       |
 * | 14 | false | &gt;0     | &lt;max    | NaN            | Hyp.: THROWS / Out: OK_RT                                    |
 * | 15 | false | &gt;0     | &lt;max    | &gt;1          | Hyp.: OK_RT / Out: THROWS                                    |
 * | 16 | false | &gt;0     | &lt;max    | =Float.MAX_VALUE | Hyp.: OK_RT / Out: THROWS                                  |
 * +----+-------+-----------+------------+----------------+--------------------------------------------------------------+
 * </pre>
 *
 * <h3>Boundary Value Analysis table — mapped 1-to-1 with CP rows</h3>
 * <pre>
 * +----+-------+-------------------+-------+-----------------+--------------------------------------------------------------+
 * |  # | lru   | max               | size  | load            | Expected output                                              |
 * +----+-------+-------------------+-------+-----------------+--------------------------------------------------------------+
 * |  1 | false | 16                |  8    | 0.75f           | OK_RT (baseline)                                             |
 * |  2 | true  | 16                |  8    | 0.75f           | OK_RT                                                        |
 * |  3 | false | 0                 |  8    | 0.75f           | Hyp.: Exception / Out: OK_RT_FAIL                            |
 * |  4 | false | -1                |  8    | 0.75f           | Hyp.: Exception / Out: OK_RT                                 |
 * |  5 | false | -100              |  8    | 0.75f           | Hyp.: Exception / Out: OK_RT                                 |
 * |  6 | false | Integer.MAX_VALUE |  8    | 0.75f           | OK_RT                                                        |
 * |  7 | false | 16                | 16    | 0.75f           | OK_RT                                                        |
 * |  8 | false | 16                | 32    | 0.75f           | OK_RT                                                        |
 * |  9 | false | 16                |  0    | 0.75f           | Hyp.: Exception / Out: OK_NO_RT                              |
 * | 10 | false | 16                | -1    | 0.75f           | Hyp.: Exception / Out: OK_RT                                 |
 * | 11 | false | 16                | -100  | 0.75f           | Hyp.: Exception / Out: OK_RT                                 |
 * | 12 | false | 16                |  8    | 0.0f            | THROWS                                                       |
 * | 13 | false | 16                |  8    | -0.5f           | THROWS                                                       |
 * | 14 | false | 16                |  8    | Float.NaN       | Hyp.: THROWS / Out: OK_RT                                    |
 * | 15 | false | 16                |  8    | 1.5f            | Hyp.: OK_RT / Out: THROWS                                    |
 * | 16 | false | 16                |  8    | Float.MAX_VALUE | Hyp.: OK_RT / Out: THROWS                                    |
 * +----+-------+-------------------+-------+-----------------+--------------------------------------------------------------+
 * </pre>
 *
 * <p><b>Notes on hypothesis/output divergences:</b>
 * <ul>
 *   <li>Cases #3, #4, #5 — non-positive values of {@code max}: a black-box reading of
 *       the contract suggests rejection, but empirically only the documented sentinel
 *       {@code -1} is mapped to {@code Integer.MAX_VALUE}; case #3 ({@code max=0})
 *       constructs a disabled instance, while case #5 ({@code max=-100}) is treated as
 *       the {@code -1} sentinel and round-trips correctly.</li>
 *   <li>Cases #10 ({@code size=-1}) and #11 ({@code size=-100}): both negative
 *       values are sampled to probe whether the constructor distinguishes the
 *       {@code -1} value from a generic negative. The {@code if (size < 0)} guard
 *       rewrites any negative {@code size} to {@code 500} regardless of its
 *       magnitude, so both rows yield {@code OK_RT}: no discriminating threshold
 *       exists, consistently with the analogous {@code if (max < 0)} guard.</li>
 *   <li>Cases #14 ({@code load=NaN}), #15, #16 ({@code load>1}): the observed
 *       behaviour is governed by the internal {@code ConcurrentReferenceHashMap}, not
 *       by the contract of {@code HashMap}. {@code load>1} is rejected (where
 *       {@code HashMap} would accept it). NaN bypasses both the {@code load>1} and
 *       {@code load<=0} guards (IEEE-754) and is silently accepted, but the resulting
 *       instance has an undefined resize threshold.</li>
 * </ul>
 */
@DisplayName("CacheMap — BVA: CacheMap(boolean lru, int max, int size, float load)")
public class CacheMapConstructorBVATest_old {

    // ─────────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────────

    private static final int   BASELINE_MAX        = 16;
    private static final int   BASELINE_SIZE       = 8;
    private static final float BASELINE_LOAD       = 0.75f;

    /**
     * Number of entries inserted in the round-trip smoke probe. Kept strictly
     * below {@link #BASELINE_MAX} so that no eviction or soft-map overflow is
     * triggered: this class tests the constructor, not the eviction logic.
     */
    private static final int   SMOKE_TEST_ENTRIES  = 5;

    // ─────────────────────────────────────────────────────────────────────────
    // Outcome enumeration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Possible outcomes for a single CP/BVA row. See the class-level Javadoc for
     * the semantics of each value.
     */
    private enum Outcome {
        /** Construction succeeds + round-trip recovers every inserted entry. */
        OK_RT,
        /** Construction succeeds; round-trip is intentionally not asserted. */
        OK_NO_RT,
        /** Construction succeeds; round-trip fails silently (disabled cache). */
        OK_RT_FAIL,
        /** Constructor raises {@code IllegalArgumentException}. */
        THROWS
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parametric table
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns one {@link Arguments} row per CP/BVA case. Column order:
     * caseId, lru, max, size, load, expectedOutcome.
     */
    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("#1  baseline",
                        false, BASELINE_MAX, BASELINE_SIZE, BASELINE_LOAD,            Outcome.OK_RT),

                Arguments.of("#2  lru=true",
                        true,  BASELINE_MAX, BASELINE_SIZE, BASELINE_LOAD,            Outcome.OK_RT),

                Arguments.of("#3  max=0 (disabled cache)",
                        false, 0,            BASELINE_SIZE, BASELINE_LOAD,            Outcome.OK_RT_FAIL),

                Arguments.of("#4  max=-1 (no-limit sentinel)",
                        false, -1,           BASELINE_SIZE, BASELINE_LOAD,            Outcome.OK_RT),

                Arguments.of("#5  max=-100 (treated as no-limit)",
                        false, -100,         BASELINE_SIZE, BASELINE_LOAD,            Outcome.OK_RT),

                Arguments.of("#6  max=Integer.MAX_VALUE",
                        false, Integer.MAX_VALUE, BASELINE_SIZE, BASELINE_LOAD,       Outcome.OK_RT),

                Arguments.of("#7  size==max",
                        false, BASELINE_MAX, BASELINE_MAX, BASELINE_LOAD,             Outcome.OK_RT),

                Arguments.of("#8  size>max",
                        false, BASELINE_MAX, BASELINE_MAX * 2, BASELINE_LOAD,         Outcome.OK_RT),

                Arguments.of("#9  size=0",
                        false, BASELINE_MAX, 0, BASELINE_LOAD,                        Outcome.OK_NO_RT),

                Arguments.of("#10 size=-1",
                        false, BASELINE_MAX, -1, BASELINE_LOAD,                       Outcome.OK_RT),

                Arguments.of("#11 size=-100 (treated as -1, rewritten to 500)",
                        false, BASELINE_MAX, -100, BASELINE_LOAD,                     Outcome.OK_RT),

                Arguments.of("#12 load=0.0f",
                        false, BASELINE_MAX, BASELINE_SIZE, 0.0f,                     Outcome.THROWS),

                Arguments.of("#13 load=-0.5f",
                        false, BASELINE_MAX, BASELINE_SIZE, -0.5f,                    Outcome.THROWS),

                Arguments.of("#14 load=NaN",
                        false, BASELINE_MAX, BASELINE_SIZE, Float.NaN,                Outcome.OK_RT),

                Arguments.of("#15 load=1.5f",
                        false, BASELINE_MAX, BASELINE_SIZE, 1.5f,                     Outcome.THROWS),

                Arguments.of("#16 load=Float.MAX_VALUE",
                        false, BASELINE_MAX, BASELINE_SIZE, Float.MAX_VALUE,          Outcome.THROWS)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    private CacheMap cache;

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.clear();
            cache = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parametric test body
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes one row from the parametric table.
     *
     * <p>Row-by-row notes:
     * <ul>
     *   <li>Rows 1–2, 4–8 — happy path: construction succeeds and the smoke probe
     *       recovers every inserted entry. The LRU flag in row 2 is accepted by
     *       the constructor but its eviction semantics are out of scope here.</li>
     *   <li>Row 3 — {@code max=0} builds a disabled instance; {@code put}/{@code get}
     *       short-circuit and return {@code null}. The smoke probe is asserted as
     *       <i>failing</i> via {@code assertNull}.</li>
     *   <li>Row 9 — {@code size=0} would trigger a division-by-zero in the soft map
     *       on any {@code put}/{@code get}. Only construction is asserted.</li>
     *   <li>Rows 10–11 — {@code size=-1} and {@code size=-100} both reach the
     *       {@code if (size < 0)} rewrite branch and are remapped to {@code 500};
     *       the round-trip therefore succeeds for both, confirming the absence of
     *       any discrimination between the {@code -1} value and a generic negative.</li>
     *   <li>Row 14 — {@code load=NaN} bypasses the load-factor guards (IEEE-754).
     *       Construction succeeds; the resize threshold is undefined so the round
     *       trip is not asserted.</li>
     *   <li>Rows 12–13, 15–16 — invalid {@code load} values are
     *       rejected by the underlying {@code ConcurrentReferenceHashMap} with
     *       {@code IllegalArgumentException}.</li>
     * </ul>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testConstructor(
            String  caseId,
            boolean lru,
            int     max,
            int     size,
            float   load,
            Outcome expected) {

        switch (expected) {

            case OK_RT: {
                cache = new CacheMap(lru, max, size, load);
                assertNotNull(cache, "[" + caseId + "] construction returned null");
                for (int i = 0; i < SMOKE_TEST_ENTRIES; i++) {
                    cache.put("k" + i, "v" + i);
                }
                for (int i = 0; i < SMOKE_TEST_ENTRIES; i++) {
                    assertEquals("v" + i, cache.get("k" + i),
                            "[" + caseId + "] round-trip mismatch on key k" + i);
                }
                break;
            }

            case OK_NO_RT: {
                cache = new CacheMap(lru, max, size, load);
                assertNotNull(cache, "[" + caseId + "] construction returned null");
                // Round trip intentionally not asserted (see Outcome legend).
                break;
            }

            case OK_RT_FAIL: {
                cache = new CacheMap(lru, max, size, load);
                assertNotNull(cache, "[" + caseId + "] construction returned null");
                cache.put("k0", "v0");
                assertNull(cache.get("k0"),
                        "[" + caseId + "] expected disabled cache, but get returned a value");
                break;
            }

            case THROWS: {
                assertThrows(IllegalArgumentException.class,
                        () -> new CacheMap(lru, max, size, load),
                        "[" + caseId + "] expected IllegalArgumentException at construction time");
                break;
            }

            default:
                throw new IllegalStateException("Unhandled outcome: " + expected);
        }
    }
}
