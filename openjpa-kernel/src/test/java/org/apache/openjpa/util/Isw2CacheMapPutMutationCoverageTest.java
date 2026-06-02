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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-coverage tests for {@link CacheMap#put(Object, Object)}.
 *
 * <p>This class complements {@link Isw2CacheMapPutBVATest}, whose category-partition
 * and boundary-value rows already exercise the externally observable contract of
 * {@code put} (return values, retrievability via {@code get}, pinned-map
 * segregation, overflow). The PIT report on that suite revealed a residue of
 * surviving mutations confined to a single semantic family: removed calls to the
 * {@code entryAdded(Object, Object)} and
 * {@code entryRemoved(Object, Object, boolean)} hooks, plus the two internal
 * {@code if (val == null)} branches that decide which of the two hooks is invoked.
 * None of these mutations is detectable by black-box assertions on the public
 * {@code Map} API, because both hooks are {@code protected} with empty default
 * bodies — their effect is, by design, observable only by subclasses that
 * override them.
 *
 * <p><b>Approach.</b> A test-local subclass ({@link SpyCacheMap}) overrides both
 * hooks and records every invocation in two append-only lists. Each test in this
 * class then asserts the exact sequence of hook calls produced by a specific
 * code path inside {@code put}. The asserted sequences correspond 1-to-1 with
 * the source-level pairs {@code (entryRemoved? + entryAdded)} that PIT mutates,
 * so a removed-call mutant immediately yields a missing entry in {@code added}
 * or {@code removed} and is killed.
 *
 * <p><b>Why this is legitimate, not score-gaming.</b> {@code entryAdded} and
 * {@code entryRemoved} are not implementation details: they are {@code protected}
 * extension points declared in the public surface of {@link CacheMap} (used,
 * for example, by {@code DataCacheStoreManager} subclasses to invalidate
 * dependent caches). A suite that never observes them leaves a real contract
 * untested. The mutations PIT generates on those callbacks are therefore real
 * faults a subclass author would suffer from, not artificial targets.
 *
 * <h3>Mutation kill matrix (mutators referenced by line/role in the source)</h3>
 * <pre>
 * +------+------------------------------------------------+---------------------------+
 * | Mut. | Source role                                    | Killed by test            |
 * +------+------------------------------------------------+---------------------------+
 * |  #3  | if (val == null) — pinned branch, negated      | testPinnedInsert_emptyPin |
 * |  #4  | _pinnedSize++ → --                             | testPinnedInsert_emptyPin |
 * |  #5  | entryAdded removed — pinned branch, val null   | testPinnedInsert_emptyPin |
 * |  #6  | entryRemoved removed — pinned branch, val nn   | testPinnedUpdate          |
 * |  #7  | entryAdded removed — pinned branch, val nn     | testPinnedUpdate          |
 * |  #11 | if (val == null) — softMap branch, negated     | testFreshInsert           |
 * |  #12 | entryAdded removed — fresh insert path         | testFreshInsert           |
 * |  #13 | entryRemoved removed — softMap promotion       | testPromotionFromSoftMap  |
 * |  #14 | entryAdded removed — softMap promotion         | testPromotionFromSoftMap  |
 * |  #15 | entryRemoved removed — cacheMap update         | testCacheMapUpdate        |
 * |  #16 | entryAdded removed — cacheMap update           | testCacheMapUpdate        |
 * +------+------------------------------------------------+---------------------------+
 * </pre>
 *
 * <p><b>Determinism note for the softMap-promotion path.</b> Mutants #13 and
 * #14 live exclusively in the {@code else} branch of the inner
 * {@code if (val == null)} that follows {@code softMap.remove}. Reaching that
 * branch requires that the key under test be in the softMap, not the cacheMap,
 * at the moment of the asserted put. The fixture of
 * {@code testPromotionFromSoftMap} therefore enables LRU eviction
 * ({@code lru=true}): with {@code lru=false} the cacheMap is a
 * {@code ConcurrentHashMap} whose overflow victim is iteration-order dependent
 * and not guaranteed to be the key the test wants to demote, which routes the
 * asserted put through the cacheMap-update branch instead and leaves #13/#14
 * unreached. See the Javadoc on that test for the full argument.
 *
 * <p><b>Mutations explicitly left out of scope.</b>
 * <ul>
 *   <li>{@code writeUnlock()} removed (analogous to the {@code readUnlock}
 *       residue on {@code get}; killing it deterministically would require a
 *       concurrent test with explicit timeout — see project report, "Mutation
 *       Testing — CacheMap" section).</li>
 * </ul>
 */
@DisplayName("CacheMap — Mutation coverage: put(Object, Object) hooks")
public class Isw2CacheMapPutMutationCoverageTest {

    // Constructor parameters mirror Isw2CacheMapPutBVATest.BASELINE_MAX_SIZE
    // so the two suites share the same fixture dimensioning.
    private static final int BASELINE_MAX_SIZE = 2;

    private SpyCacheMap cache;

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.clear();
            cache = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test-local subclass: records every invocation of the two protected hooks
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Subclass of {@link CacheMap} that exposes the two protected hooks
     * {@code entryAdded} and {@code entryRemoved} as observable append-only
     * lists. The hooks are part of the public extension surface of
     * {@code CacheMap} (declared {@code protected}, not {@code private}),
     * so overriding them is a legitimate observation channel rather than a
     * reflection-based intrusion.
     */
    private static final class SpyCacheMap extends CacheMap {

        /** Records (key, value) pairs of every {@code entryAdded} invocation. */
        final List<Object[]> added = new ArrayList<>();
        /** Records (key, value, expired) triples of every {@code entryRemoved} invocation. */
        final List<Object[]> removed = new ArrayList<>();

        SpyCacheMap(boolean lru, int max, int size, float load) {
            super(lru, max, size, load);
        }

        @Override
        protected void entryAdded(Object key, Object value) {
            added.add(new Object[] { key, value });
        }

        @Override
        protected void entryRemoved(Object key, Object value, boolean expired) {
            removed.add(new Object[] { key, value, expired });
        }

        /**
         * Clears both invocation logs without touching the cache contents.
         * Used after fixture setup to start tests from a clean observation state,
         * isolating the assertions to the single {@code put} under test.
         */
        void resetSpy() {
            added.clear();
            removed.clear();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Asserts that the spy recorded at least one {@code (key, value)} pair in
     * {@code list}. Used instead of strict size-and-order checks because the
     * internal sequencing of hooks is not part of {@code CacheMap}'s public
     * contract — only the fact that a given hook fires on a given transition.
     */
    private static void assertHookCalledWith(List<Object[]> list,
                                             String hookName,
                                             Object expectedKey,
                                             Object expectedValue,
                                             String context) {
        for (Object[] tuple : list) {
            if (eq(tuple[0], expectedKey) && eq(tuple[1], expectedValue)) {
                return;
            }
        }
        throw new AssertionError(
                "[" + context + "] expected " + hookName + "(" + expectedKey
                        + ", " + expectedValue + ") to have been called, but the recorded "
                        + "invocations were: " + dump(list));
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String dump(List<Object[]> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Object[] t = list.get(i);
            if (i > 0) sb.append(", ");
            sb.append("(").append(t[0]).append(", ").append(t[1]);
            if (t.length > 2) sb.append(", expired=").append(t[2]);
            sb.append(")");
        }
        return sb.append("]").toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // S1 — pinned insert on an empty pinned slot
    // Targets PIT mutants #3 (negated `if (val == null)` in pinned branch)
    //               and #5 (removed entryAdded call on the val-null sub-branch).
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pin a key WITHOUT first inserting a value for it. The pinnedMap therefore
     * holds {@code (k, null)}. The first {@code put(k, v)} on this key produces
     * {@code val == null} inside the pinned branch and is supposed to invoke
     * {@code entryAdded(k, v)} exactly once and {@code entryRemoved} zero times.
     *
     * <p>This scenario kills:
     * <ul>
     *   <li><b>#3</b>: if the {@code if (val == null)} branch is negated, the
     *       code would invoke {@code entryRemoved(k, null, false) + entryAdded},
     *       producing an unexpected {@code removed} record.</li>
     *   <li><b>#5</b>: if the {@code entryAdded} call is removed, the
     *       {@code added} list stays empty.</li>
     * </ul>
     */
    @Test
    @DisplayName("S1: pinned-insert (empty pinned slot) → entryAdded only")
    void testPinnedInsert_emptyPin() {
        cache = new SpyCacheMap(false, BASELINE_MAX_SIZE, BASELINE_MAX_SIZE, 0.75f);

        // Fixture: pin a key that has no prior value. The pinnedMap entry is
        // (key, null) at this point. We clear the spy before the asserted put,
        // so the assertions reason exclusively about the put under test.
        cache.pin("p1");
        cache.resetSpy();

        Object prev = cache.put("p1", "v1");

        // Map#put contract: putting onto a (k, null) pinned slot returns the
        // previous value, which is null here.
        assertEquals(null, prev, "S1: previous value of an empty pinned slot must be null");
        assertEquals("v1", cache.get("p1"), "S1: pinned value not retrievable");

        // Kill #5: entryAdded MUST have fired with (p1, v1).
        assertHookCalledWith(cache.added, "entryAdded", "p1", "v1", "S1");

        // Kill #3: entryRemoved MUST NOT have fired at all on this path
        // (negated `if (val == null)` would route execution through the
        // remove+add branch and produce a spurious entryRemoved record).
        assertTrue(cache.removed.isEmpty(),
                "S1: entryRemoved must not fire when filling an empty pinned slot; "
                        + "actual: " + dump(cache.removed)
                        + " — likely signature of a negated `if (val == null)` mutation");

        // Kill #4 (_pinnedSize++ → --): the public CacheMap.size() reads as
        // `_pinnedSize + cacheMap.size() + softMap.size()`. Before this put,
        // pin("p1") on an empty cache left _pinnedSize=0 and pinnedMap={(p1,null)}.
        // The asserted put hits the val==null branch and must increment
        // _pinnedSize to 1; the cacheMap and softMap are both empty, so the
        // observed size() must be exactly 1. If the increment is mutated to a
        // decrement, _pinnedSize becomes -1 and size() returns -1, failing
        // this assertion. This is the only public observable that depends
        // monotonically on the sign of the _pinnedSize update.
        assertEquals(1, cache.size(),
                "S1: cache.size() must reflect the incremented _pinnedSize; "
                        + "a value of -1 (or any value other than 1) is the signature of "
                        + "the `_pinnedSize++ → --` mutation");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // S2 — pinned update over a populated pinned slot
    // Targets PIT mutants #6 (entryRemoved removed) and #7 (entryAdded removed)
    //         on the val-non-null sub-branch of the pinned path.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pin a key with a pre-existing value, then overwrite it. Both hooks must
     * fire on this transition: {@code entryRemoved(k, oldVal, false)} followed
     * by {@code entryAdded(k, newVal)}.
     */
    @Test
    @DisplayName("S2: pinned-update (populated pinned slot) → both hooks fire")
    void testPinnedUpdate() {
        cache = new SpyCacheMap(false, BASELINE_MAX_SIZE, BASELINE_MAX_SIZE, 0.75f);

        // Fixture: put then pin, so the pinnedMap holds ("p1", "v_old").
        cache.put("p1", "v_old");
        cache.pin("p1");
        cache.resetSpy();

        Object prev = cache.put("p1", "v_new");

        assertEquals("v_old", prev, "S2: previous pinned value must be returned");
        assertEquals("v_new", cache.get("p1"), "S2: pinned value not updated");

        // Kill #6: entryRemoved MUST fire with (p1, v_old).
        assertHookCalledWith(cache.removed, "entryRemoved", "p1", "v_old", "S2");
        // Kill #7: entryAdded MUST fire with (p1, v_new).
        assertHookCalledWith(cache.added, "entryAdded", "p1", "v_new", "S2");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // S3 — fresh insertion into an empty cacheMap (no softMap, no pinning)
    // Targets PIT mutants #11 (negated `if (val == null)` after softMap.remove)
    //               and #12 (removed entryAdded call on the insert path).
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Insert a brand-new key into an empty cache. The pinnedMap branch is
     * skipped, the cacheMap branch runs and produces {@code val == null} after
     * {@code softMap.remove}; the only hook that should fire is
     * {@code entryAdded(k, v)}.
     */
    @Test
    @DisplayName("S3: fresh insert (empty cache) → entryAdded only")
    void testFreshInsert() {
        cache = new SpyCacheMap(false, BASELINE_MAX_SIZE, BASELINE_MAX_SIZE, 0.75f);
        // No fixture beyond construction. We do NOT call resetSpy here because
        // construction itself does not invoke the hooks, but for clarity:
        cache.resetSpy();

        Object prev = cache.put("k1", "v1");

        assertEquals(null, prev, "S3: previous value for a brand-new key must be null");
        assertEquals("v1", cache.get("k1"), "S3: value not retrievable after fresh insert");

        // Kill #12: entryAdded MUST fire with (k1, v1).
        assertHookCalledWith(cache.added, "entryAdded", "k1", "v1", "S3");

        // Kill #11: entryRemoved MUST NOT fire — softMap is empty, so
        // `val == null` after the soft probe; a negated condition would route
        // execution into the entryRemoved branch and leave a spurious record.
        assertTrue(cache.removed.isEmpty(),
                "S3: entryRemoved must not fire on a fresh insert; "
                        + "actual: " + dump(cache.removed)
                        + " — likely signature of a negated `if (val == null)` mutation "
                        + "after softMap.remove");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // S4 — update of a key already living in cacheMap (hard-ref update)
    // Targets PIT mutants #15 (entryRemoved removed) and #16 (entryAdded removed)
    //         on the val-non-null sub-branch of the cacheMap update path.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Update a key that already lives in the cacheMap. Both hooks must fire:
     * {@code entryRemoved(k, oldVal, false)} for the eviction of the old
     * binding, then {@code entryAdded(k, newVal)} for the new one.
     */
    @Test
    @DisplayName("S4: cacheMap update → both hooks fire")
    void testCacheMapUpdate() {
        cache = new SpyCacheMap(false, BASELINE_MAX_SIZE, BASELINE_MAX_SIZE, 0.75f);
        cache.put("k1", "v_old");
        cache.resetSpy();

        Object prev = cache.put("k1", "v_new");

        assertEquals("v_old", prev, "S4: previous cacheMap value must be returned");
        assertEquals("v_new", cache.get("k1"), "S4: cacheMap value not updated");

        // Kill #15: entryRemoved MUST fire with (k1, v_old).
        assertHookCalledWith(cache.removed, "entryRemoved", "k1", "v_old", "S4");
        // Kill #16: entryAdded MUST fire with (k1, v_new).
        assertHookCalledWith(cache.added, "entryAdded", "k1", "v_new", "S4");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // S5 — promotion: key lives in softMap, put() should hoist it back to cacheMap
    // Targets PIT mutants #13 (entryRemoved removed) and #14 (entryAdded removed)
    //         on the promotion sub-branch (cacheMap.put returns null, then
    //         softMap.remove returns the old value).
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Force a key into the softMap by overflowing the cacheMap, then re-put it.
     * The pinned branch is skipped; the cacheMap branch runs and finds
     * {@code val == null} after the local put but {@code val != null} after
     * {@code softMap.remove}, triggering both hooks.
     *
     * <p><b>LRU=true is mandatory here</b>, not a stylistic choice. With
     * {@code lru=false} the underlying cacheMap is a {@code ConcurrentHashMap}
     * whose overflow victim is selected via {@code Iterator.next()} on an
     * unspecified iteration order. In that mode there is no guarantee that
     * {@code k_soft} (the first inserted key) is the one demoted to softMap by
     * the overflow caused by {@code put("k_b", ...)} — depending on the JVM
     * and the hash layout, {@code k_a} or {@code k_b} could be chosen instead,
     * leaving {@code k_soft} in cacheMap. When that happens, the asserted
     * {@code put("k_soft", "v_promoted")} below executes the
     * <i>cacheMap-update</i> branch (val != null on the inner put) rather than
     * the <i>softMap-promotion</i> branch (val == null on the inner put, then
     * val != null after softMap.remove), and PIT mutants #13 and #14 — which
     * live exclusively in the latter branch — are never reached, hence never
     * killed. With {@code lru=true} the cacheMap becomes an {@code LRUMap}
     * whose eviction is deterministic: the least-recently-used entry is
     * always the victim, and since the setup below never re-accesses
     * {@code k_soft} after inserting it, {@code k_soft} is guaranteed to be
     * the demoted entry.
     *
     * <p>Fixture: same overflow recipe used by {@code Isw2CacheMapPutBVATest}
     * (CACHE_FULL setup) but with LRU enabled to make the demotion target
     * deterministic.
     */
    @Test
    @DisplayName("S5: promotion from softMap → both hooks fire on softMap branch")
    void testPromotionFromSoftMap() {
        // LRU=true is required: see the Javadoc above for the rationale.
        cache = new SpyCacheMap(true, BASELINE_MAX_SIZE, BASELINE_MAX_SIZE, 0.75f);

        // Fixture: k_soft is inserted first and never re-accessed, making it
        // the LRU victim when the third put overflows the cacheMap.
        // Post-condition: cacheMap = {k_a, k_b}, softMap = {k_soft}.
        cache.put("k_soft", "v_soft");
        cache.put("k_a", "v_a");
        cache.put("k_b", "v_b");
        cache.resetSpy();

        // Sanity precondition before exercising the promotion: k_soft must
        // still be retrievable through the public API (softMap is transparent).
        // Note: this only proves the key is reachable, not that it lives in
        // softMap specifically — but together with the deterministic LRU
        // eviction above, the actual softMap residency is guaranteed.
        assertEquals("v_soft", cache.get("k_soft"),
                "S5: precondition violated — k_soft not reachable after overflow");

        // Re-put k_soft. The cacheMap.put returns null (k_soft is not in
        // cacheMap), softMap.remove returns "v_soft" → val becomes non-null →
        // both hooks must fire.
        Object prev = cache.put("k_soft", "v_promoted");

        assertEquals("v_soft", prev, "S5: previous soft value must be returned");
        assertEquals("v_promoted", cache.get("k_soft"),
                "S5: promoted value not retrievable");

        // Kill #13: entryRemoved MUST fire with (k_soft, v_soft).
        assertHookCalledWith(cache.removed, "entryRemoved", "k_soft", "v_soft", "S5");
        // Kill #14: entryAdded MUST fire with (k_soft, v_promoted).
        assertHookCalledWith(cache.added, "entryAdded", "k_soft", "v_promoted", "S5");
    }
}
