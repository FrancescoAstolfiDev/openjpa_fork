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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM-generated mutation-coverage tests for {@link CacheMap}, targeting
 * surviving PIT mutants in the constructor, {@link CacheMap#get(Object)}, and
 * {@link CacheMap#put(Object, Object)} that are not killed by the existing
 * {@code Isw2CacheMap*} suites.
 *
 * <h3>Strategy</h3>
 * <p>The existing BVA and mutation-coverage suites leave three main gaps:</p>
 * <ol>
 *   <li><b>Constructor</b> — the {@code lru} flag and the {@code max} boundary
 *       logic are verified only through a round-trip smoke probe, which cannot
 *       distinguish between the two internal map implementations or detect a
 *       missing {@code setMaxSize} call.</li>
 *   <li><b>{@code get()}</b> — the soft-to-hard promotion side-effect (the
 *       {@code put(key,val)} call in the {@code finally} block) is never
 *       observed by existing tests: the return value and reachability of the
 *       key are identical with and without the promotion, so VOID_METHOD_CALLS
 *       and NEGATE_CONDITIONALS on {@code if (putcache)} both survive.</li>
 *   <li><b>{@code put()}</b> — the {@code entryRemoved(key,value,true)} call
 *       inside {@code cacheMapOverflowRemoved} (taken when the soft map is
 *       already full) is never reached by existing tests, which use an
 *       unbounded soft map.</li>
 * </ol>
 * <p>All three gaps are closed here via a {@link SpyCacheMap} subclass that
 * records every invocation of the two protected hooks.</p>
 *
 * <h3>PIT mutators killed per test</h3>
 * <pre>
 * +------+-------+----------------------------------------------------------+---------------------+
 * | Test | Group | Targeted mutation                                        | PIT operator        |
 * +------+-------+----------------------------------------------------------+---------------------+
 * | A1   | Ctor  | if (!lru) → lru=true uses ConcurrentHashMap             | NEGATE_CONDITIONALS |
 * | A2   | Ctor  | if (!lru) → lru=false uses LRUMap                       | NEGATE_CONDITIONALS |
 * | A3   | Ctor  | cacheMap.setMaxSize(max) removed → unconfigured maxSize  | VOID_METHOD_CALLS   |
 * | A4   | Ctor  | if (max < 0) negated → max=-1 not rewritten             | NEGATE_CONDITIONALS |
 * | A5   | Ctor  | if (max < 0) negated → max=0 wrongly mapped to MAX_VALUE | NEGATE_CONDITIONALS |
 * | B1   | get   | put(key,val) in finally removed → no promotion           | VOID_METHOD_CALLS   |
 * | B2   | get   | if (putcache) negated → cacheMap hit triggers promotion  | NEGATE_CONDITIONALS |
 * | B3   | get   | if (putcache) negated → pinnedMap hit triggers promotion | NEGATE_CONDITIONALS |
 * | B4   | get   | confirmation: second get hits cacheMap (no entryAdded)  | VOID_METHOD_CALLS   |
 * | C1   | put   | entryAdded suppressed on disabled cache (maxSize=0)     | VOID_METHOD_CALLS   |
 * | C2   | put   | pinnedMap.containsKey negated → value stored in cacheMap | NEGATE_CONDITIONALS |
 * | C3   | put   | _pinnedSize++ → size() does not reflect the new entry   | MATH / VOID         |
 * | C4   | put   | entryRemoved in cacheMapOverflowRemoved (softMap full)  | VOID_METHOD_CALLS   |
 * +------+-------+----------------------------------------------------------+---------------------+
 * </pre>
 */
@DisplayName("CacheMap — LLM Mutation-Coverage: constructor, get(), put()")
public class Isw2LLMCacheMapTest {

    // ─────────────────────────────────────────────────────────────────────────
    // SpyCacheMap — records every invocation of the two protected hooks
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Subclass that appends every {@code entryAdded} and {@code entryRemoved}
     * invocation to append-only lists, enabling white-box assertions on the
     * hook-call sequences produced by {@code get()} and {@code put()}.
     */
    private static final class SpyCacheMap extends CacheMap {

        /** Each element is {key, value}. */
        final List<Object[]> added   = new ArrayList<>();
        /** Each element is {key, value, Boolean expired}. */
        final List<Object[]> removed = new ArrayList<>();

        SpyCacheMap(boolean lru, int max, int size, float load) {
            super(lru, max, size, load);
        }

        @Override
        protected void entryAdded(Object key, Object value) {
            added.add(new Object[]{key, value});
        }

        @Override
        protected void entryRemoved(Object key, Object value, boolean expired) {
            removed.add(new Object[]{key, value, expired});
        }

        /** Clears both logs without touching cache contents. */
        void resetSpy() {
            added.clear();
            removed.clear();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Returns true iff the log contains at least one record with the given key/value. */
    private static boolean hookFiredWith(List<Object[]> log, Object key, Object value) {
        for (Object[] t : log) {
            if (eq(t[0], key) && eq(t[1], value)) return true;
        }
        return false;
    }

    /** Returns true iff the removed-log contains a record with the given key, value, and expired flag. */
    private static boolean removedFiredWith(List<Object[]> log, Object key, Object value, boolean expired) {
        for (Object[] t : log) {
            if (eq(t[0], key) && eq(t[1], value) && Boolean.valueOf(expired).equals(t[2])) return true;
        }
        return false;
    }

    private static String dumpLog(List<Object[]> log) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < log.size(); i++) {
            Object[] t = log.get(i);
            if (i > 0) sb.append(", ");
            sb.append("(").append(t[0]).append(", ").append(t[1]);
            if (t.length > 2) sb.append(", expired=").append(t[2]);
            sb.append(")");
        }
        return sb.append("]").toString();
    }

    /**
     * Builds a fresh SpyCacheMap (LRU, maxSize=2) with one key pre-loaded into
     * the soft map. Uses LRU so the eviction victim is deterministic (the first
     * key inserted is always the LRU candidate).
     */
    private SpyCacheMap buildWithSoftEntry(String softKey, String softVal) {
        SpyCacheMap c = new SpyCacheMap(true, 2, 4, 0.75f);
        c.put(softKey, softVal);   // will be LRU victim
        c.put("__x", "__vx");
        c.put("__y", "__vy");      // overflow: softKey demoted to softMap
        c.remove("__x");
        c.remove("__y");
        c.resetSpy();
        return c;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    private SpyCacheMap cache;

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.clear();
            cache = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GROUP A — Constructor
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * A1 — Kills NEGATE_CONDITIONALS on {@code if (!lru)} in the constructor.
     * If the condition is negated, a {@code lru=true} argument causes a
     * {@code ConcurrentHashMap} to be created instead of an {@code LRUMap},
     * and {@code isLRU()} returns {@code false}.
     */
    @Test
    @DisplayName("A1: constructor lru=true → isLRU() true")
    void testConstructor_lruTrue_isLRU() {
        cache = new SpyCacheMap(true, 8, 4, 0.75f);
        assertTrue(cache.isLRU(),
                "A1: isLRU() must return true when constructed with lru=true; "
                + "false signals NEGATE_CONDITIONALS on `if (!lru)`");
    }

    /**
     * A2 — Kills NEGATE_CONDITIONALS on {@code if (!lru)} from the false side.
     * If the condition is negated, {@code lru=false} creates an {@code LRUMap}
     * and {@code isLRU()} returns {@code true}.
     */
    @Test
    @DisplayName("A2: constructor lru=false → isLRU() false")
    void testConstructor_lruFalse_notLRU() {
        cache = new SpyCacheMap(false, 8, 4, 0.75f);
        assertFalse(cache.isLRU(),
                "A2: isLRU() must return false when constructed with lru=false; "
                + "true signals NEGATE_CONDITIONALS on `if (!lru)`");
    }

    /**
     * A3 — Kills VOID_METHOD_CALLS on {@code cacheMap.setMaxSize(max)}.
     * If the call is removed, the cache map retains a default max size
     * (likely {@code Integer.MAX_VALUE}), and {@code getCacheSize()} would
     * not equal the {@code max} argument passed to the constructor.
     */
    @Test
    @DisplayName("A3: constructor max=4 → getCacheSize() == 4")
    void testConstructor_maxPositive_cacheSizeMatchesParam() {
        cache = new SpyCacheMap(false, 4, 4, 0.75f);
        assertEquals(4, cache.getCacheSize(),
                "A3: getCacheSize() must equal the max constructor argument; "
                + "a different value signals VOID_METHOD_CALLS on `setMaxSize(max)`");
    }

    /**
     * A4 — Kills NEGATE_CONDITIONALS on {@code if (max < 0) max = Integer.MAX_VALUE}.
     * With {@code max=-1}: correct code rewrites to {@code Integer.MAX_VALUE} and
     * {@code getCacheSize()} returns {@code -1} (the unlimited sentinel). Under the
     * negated mutation ({@code max >= 0}), max=-1 is NOT rewritten and
     * {@code setMaxSize(-1)} is called directly, which the underlying map may
     * interpret differently.
     */
    @Test
    @DisplayName("A4: constructor max=-1 → getCacheSize() == -1 (unlimited)")
    void testConstructor_maxNegative_cacheIsUnlimited() {
        cache = new SpyCacheMap(false, -1, 4, 0.75f);
        assertEquals(-1, cache.getCacheSize(),
                "A4: getCacheSize() must return -1 (unlimited) for max=-1; "
                + "a different value signals NEGATE_CONDITIONALS on `if (max < 0)`");
        // Confirm the unlimited cache actually stores and retrieves entries.
        cache.put("k", "v");
        assertEquals("v", cache.get("k"),
                "A4: unlimited cache must be usable after construction");
    }

    /**
     * A5 — Kills NEGATE_CONDITIONALS on {@code if (max < 0)} from the zero side.
     * With {@code max=0}: the correct code does NOT rewrite (0 is not < 0), so
     * {@code setMaxSize(0)} is called, creating a disabled cache where every
     * {@code put()} short-circuits and returns {@code null}. Under the negated
     * mutation ({@code max >= 0} → always rewrite), max=0 becomes
     * {@code Integer.MAX_VALUE}, enabling an unlimited cache.
     */
    @Test
    @DisplayName("A5: constructor max=0 → getCacheSize()==0, put() short-circuits")
    void testConstructor_maxZero_cacheIsDisabled() {
        cache = new SpyCacheMap(false, 0, 4, 0.75f);
        assertEquals(0, cache.getCacheSize(),
                "A5: getCacheSize() must be 0 for max=0; non-zero signals "
                + "NEGATE_CONDITIONALS on `if (max < 0)` rewriting 0 to MAX_VALUE");
        Object prev = cache.put("k", "v");
        assertNull(prev,
                "A5: put() must return null immediately on a disabled cache");
        assertNull(cache.get("k"),
                "A5: get() must return null — entry was never stored");
        // Under the negated mutation the cache is unlimited and entryAdded fires.
        assertTrue(cache.added.isEmpty(),
                "A5: entryAdded must NOT fire on a disabled cache; "
                + "a record signals the cache was not actually disabled");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GROUP B — get()
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * B1 — Kills VOID_METHOD_CALLS on {@code put(key, val)} inside the
     * {@code finally} block of {@link CacheMap#get(Object)}.
     *
     * <p>When {@code softMap.get(key)} returns a non-null value, the flag
     * {@code putcache} is set to {@code true}; after the read lock is
     * released, {@code put(key, val)} is called to promote the entry from the
     * soft map to the cache map, which internally calls {@code entryAdded}.
     * If the {@code put} call is voided, no promotion occurs and {@code entryAdded}
     * is never called — observable via the spy.</p>
     */
    @Test
    @DisplayName("B1: get() soft-map hit → entryAdded fires (soft-to-hard promotion)")
    void testGet_softMapHit_entryAddedFires() {
        cache = buildWithSoftEntry("ks", "vs");

        Object val = cache.get("ks");

        assertEquals("vs", val, "B1: get() must return the soft-stored value");
        assertTrue(hookFiredWith(cache.added, "ks", "vs"),
                "B1: entryAdded(ks, vs) must fire during soft-to-hard promotion; "
                + "absent record signals VOID_METHOD_CALLS on `put(key, val)` in finally. "
                + "Actual added calls: " + dumpLog(cache.added));
    }

    /**
     * B2 — Kills NEGATE_CONDITIONALS on {@code if (putcache)} in the
     * {@code finally} block of {@link CacheMap#get(Object)}.
     *
     * <p>For a cache-map hit, {@code putcache} remains {@code false} and the
     * promotion {@code put()} must NOT be called. If the condition is negated
     * ({@code if (!putcache)}), a cache-map hit would trigger a spurious
     * {@code put()} and fire {@code entryAdded} unexpectedly.</p>
     */
    @Test
    @DisplayName("B2: get() cache-map hit → entryAdded does NOT fire (no spurious promotion)")
    void testGet_cacheMapHit_noEntryAddedFires() {
        cache = new SpyCacheMap(false, 4, 4, 0.75f);
        cache.put("kc", "vc");
        cache.resetSpy();

        Object val = cache.get("kc");

        assertEquals("vc", val, "B2: get() must return the hard-cached value");
        assertTrue(cache.added.isEmpty(),
                "B2: entryAdded must NOT fire for a cache-map hit; "
                + "a spurious record signals NEGATE_CONDITIONALS on `if (putcache)`. "
                + "Actual added calls: " + dumpLog(cache.added));
    }

    /**
     * B3 — Kills NEGATE_CONDITIONALS on {@code if (putcache)} from the
     * pinned-map side.
     *
     * <p>Analogous to B2: for a pinned-map hit, {@code putcache} is also
     * {@code false} and no promotion must be triggered.</p>
     */
    @Test
    @DisplayName("B3: get() pinned-map hit → entryAdded does NOT fire")
    void testGet_pinnedMapHit_noEntryAddedFires() {
        cache = new SpyCacheMap(false, 4, 4, 0.75f);
        cache.put("kp", "vp");
        cache.pin("kp");
        cache.resetSpy();

        Object val = cache.get("kp");

        assertEquals("vp", val, "B3: get() must return the pinned value");
        assertTrue(cache.added.isEmpty(),
                "B3: entryAdded must NOT fire for a pinned-map hit; "
                + "a spurious record signals NEGATE_CONDITIONALS on `if (putcache)`. "
                + "Actual added calls: " + dumpLog(cache.added));
    }

    /**
     * B4 — Complementary oracle for VOID_METHOD_CALLS on {@code put(key, val)}:
     * after a soft-to-hard promotion, the second {@code get()} must hit the
     * cache map (not the soft map) and must NOT fire {@code entryAdded} again.
     *
     * <p>Under the VOID_METHOD_CALLS mutation, the first {@code get()} never
     * promotes the entry; it stays in the soft map. The second {@code get()}
     * therefore hits the soft map again, sets {@code putcache=true}, and — if
     * the promotion call is also voided — would keep cycling silently. However,
     * if promotion IS attempted (no mutation), the first call consumes the soft
     * entry and the second call finds the entry in the cache map, leaving
     * {@code added} empty after the spy reset.</p>
     *
     * <p>Combined with B1 (which confirms the first get fires {@code entryAdded}),
     * this test verifies the full state transition: soft → hard.</p>
     */
    @Test
    @DisplayName("B4: after soft promotion, second get() hits cache-map → entryAdded silent")
    void testGet_afterSoftPromotion_secondGetHitsCacheMap() {
        cache = buildWithSoftEntry("ks", "vs");

        cache.get("ks");       // first get: promotes ks to cacheMap
        cache.resetSpy();
        Object second = cache.get("ks");  // second get: must hit cacheMap

        assertEquals("vs", second,
                "B4: value must remain reachable after soft-to-hard promotion");
        assertTrue(cache.added.isEmpty(),
                "B4: entryAdded must NOT fire on the second get() (expected cacheMap hit); "
                + "a firing here means the first get() never promoted the entry — "
                + "signature of VOID_METHOD_CALLS on `put(key, val)` in finally. "
                + "Actual added calls: " + dumpLog(cache.added));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GROUP C — put() supplement
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * C1 — Kills VOID_METHOD_CALLS on the early {@code return null} guarded by
     * {@code if (cacheMap.getMaxSize() == 0)} and confirms that {@code entryAdded}
     * is NOT called on a disabled cache.
     *
     * <p>If NEGATE_CONDITIONALS flips the condition, a non-zero-size cache would
     * also short-circuit (breaking every insert), while a zero-size cache would
     * proceed to insert. Neither is tested by existing BVA rows with a SpyCacheMap,
     * so this test adds the hook-level assertion.</p>
     */
    @Test
    @DisplayName("C1: put() on disabled cache (maxSize=0) → null return, entryAdded suppressed")
    void testPut_disabledCache_entryAddedSuppressed() {
        cache = new SpyCacheMap(false, 0, 4, 0.75f);
        cache.resetSpy();

        Object prev = cache.put("k", "v");

        assertNull(prev,
                "C1: put() on a disabled cache (maxSize=0) must return null");
        assertNull(cache.get("k"),
                "C1: entry must not be stored in a disabled cache");
        assertTrue(cache.added.isEmpty(),
                "C1: entryAdded must NOT fire on a disabled cache; "
                + "a record signals the maxSize==0 guard was bypassed. "
                + "Actual added calls: " + dumpLog(cache.added));
    }

    /**
     * C2 — Kills NEGATE_CONDITIONALS on {@code pinnedMap.containsKey(key)} in
     * {@link CacheMap#put(Object, Object)}.
     *
     * <p>Under the negated mutation, a put on a pinned key routes through the
     * cache-map branch instead of the pinned-map branch, storing the value in
     * the cache map while leaving the pinned-map entry as {@code (key, null)}.
     * A subsequent {@link CacheMap#remove(Object)} then enters the pinned-map
     * branch (since the key is still in {@code pinnedMap}), finds {@code null}
     * as the previous value, and returns {@code null} — whereas the correct
     * code stores the value in the pinned map and remove() correctly returns it.</p>
     */
    @Test
    @DisplayName("C2: put() on pinned key routes to pinnedMap; remove() returns pinned value")
    void testPut_pinnedKey_removeReturnsPinnedValue() {
        cache = new SpyCacheMap(false, 4, 4, 0.75f);
        cache.pin("p");            // pinnedMap: (p → null), _pinnedSize = 0
        cache.put("p", "v1");     // must go to pinnedMap branch
        cache.resetSpy();

        Object removed = cache.remove("p");

        assertEquals("v1", removed,
                "C2: remove() must return 'v1' — the value stored via the pinned branch; "
                + "null here means put() stored the value in cacheMap instead of pinnedMap "
                + "(signature of NEGATE_CONDITIONALS on `pinnedMap.containsKey()` in put())");
        assertTrue(removedFiredWith(cache.removed, "p", "v1", false),
                "C2: entryRemoved(p, v1, false) must fire when a pinned entry is removed. "
                + "Actual removed calls: " + dumpLog(cache.removed));
    }

    /**
     * C3 — Kills the MATH / VOID mutation on {@code _pinnedSize++} in the
     * {@code val == null} sub-branch of the pinned-map path in
     * {@link CacheMap#put(Object, Object)}.
     *
     * <p>After pinning a key (without a prior value) and inserting the first
     * value, {@code _pinnedSize} must be incremented to 1. The public
     * {@link CacheMap#size()} method returns {@code _pinnedSize + cacheMap.size()
     * + softMap.size()}; since only the pinned slot is populated, size() must
     * equal 1. If the increment is mutated to a decrement, size() returns -1.</p>
     */
    @Test
    @DisplayName("C3: put() into empty pinned slot → _pinnedSize++ → size() == 1")
    void testPut_pinnedInsert_sizeIncrements() {
        cache = new SpyCacheMap(false, 4, 4, 0.75f);
        cache.pin("p");     // pinnedMap: (p → null), _pinnedSize = 0

        cache.put("p", "v1");

        assertEquals(1, cache.size(),
                "C3: size() must be 1 after inserting into an empty pinned slot; "
                + "0 or negative signals that `_pinnedSize++` was mutated to `--` or removed");
    }

    /**
     * C4 — Kills VOID_METHOD_CALLS on {@code entryRemoved(key, value, true)}
     * inside {@link CacheMap#cacheMapOverflowRemoved(Object, Object)}.
     *
     * <p>When a cache-map overflow cannot place the evicted entry into the
     * soft map (because the soft map is already at its maximum size), the
     * implementation must call {@code entryRemoved(key, value, true)}. This
     * branch is only reachable when the soft map has a finite, saturated
     * capacity — a condition not exercised by any existing test because the
     * default soft-map size is effectively unlimited.</p>
     *
     * <p>Fixture: {@code cacheMap.maxSize = 1}, {@code softMap.maxSize = 1}
     * (via {@link CacheMap#setSoftReferenceSize(int)}).
     * <ol>
     *   <li>Put "a": fits in cacheMap. softMap empty.</li>
     *   <li>Put "b": overflow → "a" demoted to softMap (fits, softMap now full).
     *       cacheMap = {b}.</li>
     *   <li>resetSpy()</li>
     *   <li>Put "c": overflow → "b" evicted from cacheMap; softMap is full
     *       ({@code softMap.size() >= softMap.getMaxSize()}), so
     *       {@code cacheMapOverflowRemoved} takes the {@code else} branch and
     *       calls {@code entryRemoved("b", "vb", true)}.</li>
     * </ol>
     */
    @Test
    @DisplayName("C4: cacheMap overflow when softMap full → entryRemoved(expired=true) fires")
    void testPut_cacheOverflowSoftMapFull_entryRemovedWithExpiredFlag() {
        cache = new SpyCacheMap(false, 1, 4, 0.75f);
        cache.setSoftReferenceSize(1);   // softMap.maxSize = 1

        cache.put("a", "va");   // cacheMap = {a}, softMap = {}
        cache.put("b", "vb");   // overflow: a → softMap(fits). cacheMap = {b}, softMap = {a}
        cache.resetSpy();

        cache.put("c", "vc");   // overflow: b cannot fit in softMap → entryRemoved("b","vb",true)

        assertTrue(removedFiredWith(cache.removed, "b", "vb", true),
                "C4: entryRemoved(b, vb, expired=true) must fire when cacheMap overflows "
                + "into a full softMap; absent record signals VOID_METHOD_CALLS on "
                + "`entryRemoved(key, value, true)` in cacheMapOverflowRemoved. "
                + "Actual removed calls: " + dumpLog(cache.removed));
    }
}
