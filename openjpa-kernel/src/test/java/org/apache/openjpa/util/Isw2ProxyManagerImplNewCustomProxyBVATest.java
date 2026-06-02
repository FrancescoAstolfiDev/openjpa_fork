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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.apache.openjpa.util.proxy.ProxyCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Category Partition (CP) and Boundary Value Analysis (BVA) tests for
 * {@link ProxyManagerImpl#newCustomProxy(Object, boolean)}.
 *
 * <p>The {@code newCustomProxy} method is the entry point for proxy creation
 * in OpenJPA. It accepts an existing object and dispatches — via a chain of
 * {@code instanceof} checks — to the appropriate proxy factory based on the
 * runtime type of the source. Coherently with the role of {@link ProxyManagerImpl}
 * within OpenJPA, the method operates exclusively on instances belonging to
 * the categories of <i>second class objects</i> contemplated by the framework:
 * collections, maps, dates and calendars, plus generic beans equipped with a
 * copy constructor.</p>
 *
 * <h3>1. Test Strategy &amp; Environment (Setup)</h3>
 * <p>Each table row is a standalone {@code @Test} method. A fresh
 * {@link ProxyManagerImpl} is built before every test via {@code @BeforeEach}
 * so that no side effect (blacklist entries, proxy-class cache) leaks across
 * cases. The manager is left in its default configuration
 * ({@code _trackChanges = true}, {@code _assertType = false},
 * {@code _delayedCollectionLoading = false}) so that {@code autoOff} can be
 * observed against an active change-tracking baseline.</p>
 *
 * <h3>2. Round Trip Tests</h3>
 * <ul>
 * <li><b>Round Trip 1 — Identity by copy:</b> invokes
 *     {@code Proxy p = pm.newCustomProxy(orig, autoOff)} and verifies that
 *     {@code ((Proxy) p).copy(p)} equals the original source object.</li>
 * <li><b>Round Trip 2 — Observable mutation:</b> applies a type-appropriate
 *     mutation and verifies it is reflected on the proxy:
 *     <ul>
 *       <li>{@code Collection} → {@code add(elem)}, verify size, membership
 *           and tracker registration after manual activation</li>
 *       <li>{@code Map} → {@code put(k,v)}, verify size and retrieval</li>
 *       <li>{@code Date} → {@code setTime(t')}, verify {@code getTime()}</li>
 *       <li>{@code Timestamp} → {@code setTime(t')} + {@code setNanos(n')}</li>
 *       <li>{@code Calendar} → {@code setTimeInMillis(t')}</li>
 *     </ul>
 * </li>
 * </ul>
 *
 * <h3>3. Behavioural observations on cases #1 and #2</h3>
 * <p>Cases #1 ({@code autoOff = false}) and #2 ({@code autoOff = true}) are
 * structurally identical and exercise the same {@code add} mutation against a
 * freshly built {@code ArrayList} proxy. They are encoded as two separate test
 * methods so that the {@code autoOff} boundary is explicit in the test
 * registry; however, the observable behaviour of the two configurations turns
 * out to be indistinguishable under the conditions exercised here.</p>
 *
 * <p>Two empirical observations emerged from the comparison:</p>
 * <ol>
 *   <li><b>Anomaly A — Tracker inactive at proxy creation regardless of
 *       {@code autoOff}.</b> Immediately after
 *       {@code pm.newCustomProxy(orig, autoOff)}, the {@code ChangeTracker}
 *       returned by {@link Proxy#getChangeTracker()} reports
 *       {@code isTracking() == false} for both {@code autoOff = false} and
 *       {@code autoOff = true}. The flag does not differentiate the initial
 *       state of the tracker — a custom-created proxy is born unattached to
 *       change tracking and only becomes active once associated to a managed
 *       state manager. In the unit-test context this association is absent,
 *       so tracking must be enabled explicitly via
 *       {@link ChangeTracker#startTracking()} for any event to be recorded.</li>
 *   <li><b>Confirmation B — Manual {@code startTracking()} restores event
 *       recording.</b> Once {@code tracker.startTracking()} is invoked,
 *       {@code isTracking()} flips to {@code true} and a subsequent
 *       {@code coll.add(elem)} is properly intercepted by the proxy's
 *       {@code ProxyCollections.afterAdd} hook, populating
 *       {@code tracker.getAdded()}. The element is therefore visible in two
 *       layers: physically in the underlying {@code ArrayList} (via
 *       {@code contains(elem)}) and logically in the tracker's add-set.
 *       This holds identically for {@code autoOff = false} and
 *       {@code autoOff = true}, confirming that {@code autoOff} has no
 *       observable effect on a custom proxy detached from a state manager.</li>
 * </ol>
 *
 * <p>Both observations are asserted directly in cases #1 and #2, in line with
 * the report's general policy of pinning observed behaviour as the test
 * oracle (see e.g. DbLedgerStorage case #5: {@code entryId = -1L} returning
 * the last entry, hypothesis vs. observed). The two test methods therefore
 * both pass with identical assertions, making the observational equivalence
 * of {@code autoOff = false} and {@code autoOff = true} explicit at the test
 * level.</p>
 *
 * <h3>4. Category Partition (CP) Table</h3>
 * <pre>
 * +----+----------------------------------------+---------+----------------------------------------------------------+
 * | #  | orig                                   | autoOff | Expected Outcome                                         |
 * +----+----------------------------------------+---------+----------------------------------------------------------+
 * | 1  | Collection (ArrayList)                 | false   | Proxy created, RT1 ok, anomaly A, RT2 ok after manual    |
 * |    |                                        |         | startTracking() (add reflected physically + on tracker)  |
 * | 2  | Collection (ArrayList)                 | true    | Same observable behaviour as case #1                     |
 * | 3  | Map (HashMap)                          | false   | Proxy created, RT1 ok, RT2 ok (put reflected)            |
 * | 4  | Date non-Timestamp (java.util.Date)    | false   | Proxy created, RT1 ok, RT2 ok (setTime reflected)        |
 * | 5  | Timestamp                              | false   | Proxy created, RT1 ok, RT2 ok (setTime + setNanos)       |
 * | 6  | Calendar (GregorianCalendar)           | false   | Proxy created, RT1 ok, RT2 ok (setTimeInMillis)          |
 * | 7  | Final, non-proxyable class (Integer)   | false   | Returns null (silently, no exception)                    |
 * | 8  | Class blacklisted via setUnproxyable   | false   | Returns null                                             |
 * | 9  | Already a Proxy instance               | false   | Returns the same instance (identity preserved)           |
 * | 10 | null                                   | false   | Returns null (early exit, no exception)                  |
 * +----+----------------------------------------+---------+----------------------------------------------------------+
 * </pre>
 *
 * <h3>5. Boundary Value Analysis (BVA) Table — mapped 1-to-1 with CP rows</h3>
 * <pre>
 * +----+-------------------------------------------------+---------+--------------------------------------------------+
 * | #  | orig (dynamic type)                             | autoOff | Expected Output / Observation                    |
 * +----+-------------------------------------------------+---------+--------------------------------------------------+
 * | 1  | new ArrayList&lt;&gt;(asList("a","b"))           | false   | RT1 ok, anomaly A, RT2 ok after startTracking()  |
 * | 2  | new ArrayList&lt;&gt;(asList("a","b"))           | true    | Same observable behaviour as case #1             |
 * | 3  | new HashMap&lt;&gt;(...)                         | false   | Proxy created, RT1 ok, RT2 ok                    |
 * | 4  | new java.util.Date(t)                           | false   | Proxy created, RT1 ok, RT2 ok (setTime)          |
 * | 5  | new Timestamp(t).setNanos(n)                    | false   | Proxy created, RT1 ok, RT2 ok (setTime+setNanos) |
 * | 6  | GregorianCalendar with non-default TZ           | false   | Proxy created, RT1 ok, RT2 ok (setTimeInMillis)  |
 * | 7  | Integer.valueOf(0)                              | false   | Returns null                                     |
 * | 8  | String blacklisted via setUnproxyable           | false   | Returns null                                     |
 * | 9  | Result of a previous newCustomProxy             | false   | Returns the same instance                        |
 * | 10 | null                                            | false   | Returns null                                     |
 * +----+-------------------------------------------------+---------+--------------------------------------------------+
 * </pre>
 *
 * <p><b>Implementation note (case #5):</b> the {@code Timestamp} branch activates
 * an additional {@code setNanos} call beyond the base {@code Date} branch
 * (see {@link ProxyManagerImpl#newCustomProxy} lines around the
 * {@code orig instanceof Timestamp} check). This case is included as a critical
 * BVA boundary on the {@code Date} / {@code Timestamp} dispatch transition.</p>
 *
 * <p><b>Implementation note (case #9):</b> when {@code orig} is already a
 * {@code Proxy}, the method returns the original instance unchanged and does
 * <i>not</i> create a new proxy wrapper, preserving reference identity.</p>
 */
@DisplayName("ProxyManagerImpl — BVA: newCustomProxy(Object, boolean)")
public class Isw2ProxyManagerImplNewCustomProxyBVATest {

    /* ---------------------- environmental constants --------------------- */

    /** Fixed reference epoch used by the Date / Timestamp / Calendar cases. */
    private static final long REFERENCE_TIME_MS = 1_700_000_000_000L;
    /** Nanosecond payload exercised by the Timestamp boundary case. */
    private static final int  REFERENCE_NANOS   = 123_456_789;
    /** Non-default time zone used by the Calendar boundary case. */
    private static final TimeZone REFERENCE_TZ  = TimeZone.getTimeZone("Asia/Tokyo");
    /** Mutated epoch for RT2 on Date/Timestamp/Calendar branches. */
    private static final long RT2_MUTATED_TIME_MS = 1_800_000_000_000L;
    /** Mutated nanos for RT2 on the Timestamp branch. */
    private static final int  RT2_MUTATED_NANOS   = 987_654_321;

    /* ----------------------------- lifecycle ---------------------------- */

    private ProxyManagerImpl pm;

    @BeforeEach
    void setUp() {
        pm = new ProxyManagerImpl();
        pm.setTrackChanges(true);
        pm.setAssertAllowedType(false);
        pm.setDelayCollectionLoading(false);
        assertNotNull(pm, "fixture construction failed");
    }

    @AfterEach
    void tearDown() {
        pm = null;
    }

    /* -------------------------- shared helper --------------------------- */

    /**
     * Runs the Collection branch scenario with a configurable {@code autoOff}
     * value, asserting Anomaly A (tracker inactive at creation) and
     * Confirmation B (manual {@code startTracking()} restores event recording).
     * The body is shared between cases #1 and #2 to make the observational
     * equivalence of the two configurations <b>structural</b>: any future
     * deviation in behaviour will require diverging the two methods, exposing
     * the change at review time.
     *
     * @param autoOff the value of the {@code autoOff} flag passed to
     *                {@link ProxyManagerImpl#newCustomProxy(Object, boolean)}
     * @param caseTag short identifier used in assertion messages
     */
    private void runCollectionScenario(boolean autoOff, String caseTag) {
        ArrayList<String> orig = new ArrayList<>(Arrays.asList("a", "b"));

        Object result = pm.newCustomProxy(orig, autoOff);

        // Basic proxy contract.
        assertNotNull(result, "[" + caseTag + "] proxy must be non-null");
        assertTrue(result instanceof Proxy,
                "[" + caseTag + "] result must implement Proxy");
        assertTrue(result instanceof Collection,
                "[" + caseTag + "] result must be a Collection");
        assertTrue(result instanceof ProxyCollection,
                "[" + caseTag + "] result must implement ProxyCollection");

        // RT1: the unproxied copy must equal the original.
        assertEquals(orig, ((Proxy) result).copy(result),
                "[" + caseTag + "] RT1 failed: copy not equal to original");

        ProxyCollection proxyColl = (ProxyCollection) result;
        ChangeTracker tracker = proxyColl.getChangeTracker();
        assertNotNull(tracker,
                "[" + caseTag + "] ChangeTracker must be present on a Collection proxy");

        // -------------------------------------------------------------
        // Anomaly A: tracker is inactive at creation time, regardless of
        // autoOff. In the unit-test context, no such binding exists, so
        // isTracking() is false for both autoOff = false and autoOff = true.
        // -------------------------------------------------------------
        assertFalse(tracker.isTracking(),
                "[" + caseTag + "] ANOMALY A: tracker is expected to be inactive at "
                        + "proxy creation regardless of autoOff (observed identical for "
                        + "autoOff=false and autoOff=true)");

        // Activate the tracker manually to restore event recording.
        tracker.startTracking();
        assertTrue(tracker.isTracking(),
                "[" + caseTag + "] startTracking() must flip isTracking() to true");

        // Apply the RT2 mutation: add a fresh element to the proxy.
        @SuppressWarnings("unchecked")
        Collection<Object> coll = (Collection<Object>) result;
        int sizeBefore = coll.size();
        coll.add("rt2-added");

        // Physical state of the proxy: the element IS there.
        assertEquals(sizeBefore + 1, coll.size(),
                "[" + caseTag + "] RT2 failed: size not incremented after add()");
        assertTrue(coll.contains("rt2-added"),
                "[" + caseTag + "] RT2 failed: added element not retrievable");

        // -------------------------------------------------------------
        // Confirmation B: with the tracker manually started, the add()
        // mutation IS recorded into the tracker's add-set. The proxy's
        // ProxyCollections.afterAdd hook fires correctly when tracking is
        // active, so the element appears both physically (in the underlying
        // ArrayList) and logically (in tracker.getAdded()). This holds
        // identically for autoOff=false and autoOff=true.
        // -------------------------------------------------------------
        CollectionChangeTracker cct = (CollectionChangeTracker) tracker;
        assertNotNull(cct.getAdded(),
                "[" + caseTag + "] tracker.getAdded() must not be null");
        assertTrue(cct.getAdded().contains("rt2-added"),
                "[" + caseTag + "] CONFIRMATION B: tracker must register the add() event "
                        + "after manual startTracking() (observed identical for "
                        + "autoOff=false and autoOff=true)");
        assertFalse(cct.getAdded().isEmpty(),
                "[" + caseTag + "] CONFIRMATION B: tracker.getAdded() must be non-empty "
                        + "after a proxy-level add() with tracking manually enabled");
    }

    /* ----------------------------- test methods ------------------------- */

    /**
     * Case #1 — Collection (ArrayList), {@code autoOff = false}.
     *
     * <p>Proxy is created and RT1 succeeds. RT2 is observed in two layers:</p>
     * <ul>
     *   <li><b>Anomaly A:</b> the tracker is inactive at proxy creation,
     *       despite {@code _trackChanges = true} on the manager.</li>
     *   <li><b>Confirmation B:</b> after manually starting the tracker via
     *       {@code startTracking()}, the subsequent {@code add()} is
     *       correctly recorded in {@code getAdded()} alongside the physical
     *       mutation on the proxy.</li>
     * </ul>
     */
    @Test
    @DisplayName("#1 Collection, autoOff=false — RT1 ok; anomaly A; RT2 ok after manual startTracking()")
    void case1_collection_autoOffFalse() {
        runCollectionScenario(false, "#1");
    }

    /**
     * Case #2 — Collection (ArrayList), {@code autoOff = true}.
     *
     * <p>Structurally identical to case #1. The whole point of this case is to
     * make the observational equivalence of {@code autoOff = false} and
     * {@code autoOff = true} explicit at the test level: under the conditions
     * exercised here (custom-created proxy detached from any state manager),
     * the {@code autoOff} flag has <b>no observable effect</b> on:</p>
     * <ul>
     *   <li>the physical state of the proxy after {@code add()}</li>
     *   <li>the initial tracking state (Anomaly A)</li>
     *   <li>the tracker's correct recording of the event after manual
     *       {@code startTracking()} (Confirmation B)</li>
     * </ul>
     *
     * <p>The two methods share the same body via
     * {@link #runCollectionScenario(boolean, String)} so that any future
     * divergence in behaviour will require splitting them, surfacing the
     * change at review time.</p>
     */
    @Test
    @DisplayName("#2 Collection, autoOff=true — same observable behaviour as #1")
    void case2_collection_autoOffTrue() {
        runCollectionScenario(true, "#2");
    }

    /**
     * Case #3 — Map (HashMap), {@code autoOff = false}.
     * Proxy is created; RT1 and RT2 both succeed at the physical level.
     */
    @Test
    @DisplayName("#3 Map, autoOff=false — RT1 ok, RT2 ok (put reflected)")
    void case3_map_autoOffFalse() {
        Map<String, String> orig = new HashMap<>();
        orig.put("k1", "v1");
        orig.put("k2", "v2");

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result);
        assertTrue(result instanceof Proxy);
        assertTrue(result instanceof Map);

        // RT1
        assertEquals(orig, ((Proxy) result).copy(result));

        // RT2
        @SuppressWarnings("unchecked")
        Map<Object, Object> map = (Map<Object, Object>) result;
        int sizeBefore = map.size();
        map.put("rt2-key", "rt2-value");
        assertEquals(sizeBefore + 1, map.size());
        assertEquals("rt2-value", map.get("rt2-key"));
    }

    /**
     * Case #4 — Date (non-Timestamp), {@code autoOff = false}.
     * Proxy is created; RT1 preserves time; RT2 reflects {@code setTime}.
     */
    @Test
    @DisplayName("#4 Date non-Timestamp — RT1 ok, RT2 ok (setTime reflected)")
    void case4_dateNonTimestamp() {
        Date orig = new Date(REFERENCE_TIME_MS);

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result);
        assertTrue(result instanceof Proxy);
        assertTrue(result instanceof Date);
        assertFalse(result instanceof Timestamp);

        Date proxiedDate = (Date) result;

        // RT1: time preserved.
        assertEquals(orig.getTime(), proxiedDate.getTime());

        // RT2: setTime is reflected.
        proxiedDate.setTime(RT2_MUTATED_TIME_MS);
        assertEquals(RT2_MUTATED_TIME_MS, proxiedDate.getTime());
    }

    /**
     * Case #5 — Timestamp (nanos boundary), {@code autoOff = false}.
     * Proxy is created; RT1 preserves both time and nanos; RT2 reflects
     * both {@code setTime} and {@code setNanos} (critical boundary between
     * Date and Timestamp dispatch).
     */
    @Test
    @DisplayName("#5 Timestamp (nanos boundary) — RT1 ok, RT2 ok (setTime + setNanos reflected)")
    void case5_timestamp_nanosBoundary() {
        Timestamp orig = new Timestamp(REFERENCE_TIME_MS);
        orig.setNanos(REFERENCE_NANOS);

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result);
        assertTrue(result instanceof Proxy);
        assertTrue(result instanceof Timestamp);

        Timestamp proxiedTs = (Timestamp) result;

        // RT1: time and nanos preserved.
        assertEquals(orig.getTime(), proxiedTs.getTime());
        assertEquals(orig.getNanos(), proxiedTs.getNanos());

        // RT2: both mutations are reflected.
        proxiedTs.setTime(RT2_MUTATED_TIME_MS);
        assertEquals(RT2_MUTATED_TIME_MS, proxiedTs.getTime());
        proxiedTs.setNanos(RT2_MUTATED_NANOS);
        assertEquals(RT2_MUTATED_NANOS, proxiedTs.getNanos());
    }

    /**
     * Case #6 — GregorianCalendar with non-default TZ (Asia/Tokyo),
     * {@code autoOff = false}. Proxy is created; RT1 preserves both
     * {@code timeInMillis} and {@code TimeZone}; RT2 reflects
     * {@code setTimeInMillis}.
     */
    @Test
    @DisplayName("#6 GregorianCalendar (TZ Asia/Tokyo) — RT1 ok, RT2 ok (setTimeInMillis reflected)")
    void case6_gregorianCalendar_tokyoTZ() {
        Calendar orig = new GregorianCalendar(REFERENCE_TZ);
        orig.setTimeInMillis(REFERENCE_TIME_MS);

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result);
        assertTrue(result instanceof Proxy);
        assertTrue(result instanceof Calendar);

        Calendar proxiedCal = (Calendar) result;

        // RT1: timeInMillis and TimeZone preserved.
        assertEquals(orig.getTimeInMillis(), proxiedCal.getTimeInMillis());
        assertEquals(orig.getTimeZone(), proxiedCal.getTimeZone());

        // RT2: setTimeInMillis is reflected.
        proxiedCal.setTimeInMillis(RT2_MUTATED_TIME_MS);
        assertEquals(RT2_MUTATED_TIME_MS, proxiedCal.getTimeInMillis());
    }

    /**
     * Case #7 — final non-proxyable class (Integer), {@code autoOff = false}.
     * {@code newCustomProxy} returns {@code null} silently.
     */
    @Test
    @DisplayName("#7 final non-proxyable (Integer) — returns null")
    void case7_finalNonProxyable_integer() {
        Object result = pm.newCustomProxy(Integer.valueOf(0), false);
        assertNull(result);
    }

    /**
     * Case #8 — class blacklisted via
     * {@link ProxyManagerImpl#setUnproxyable}, {@code autoOff = false}.
     * {@code newCustomProxy} returns {@code null} silently.
     */
    @Test
    @DisplayName("#8 blacklisted via setUnproxyable — returns null")
    void case8_blacklisted_string() {
        pm.setUnproxyable(String.class.getName());
        Object result = pm.newCustomProxy("blacklisted-source", false);
        assertNull(result);
    }

    /**
     * Case #9 — {@code orig} is already a {@link Proxy}, {@code autoOff = false}.
     * {@code newCustomProxy} returns the exact same instance, preserving
     * reference identity.
     */
    @Test
    @DisplayName("#9 already a Proxy — returns same instance (identity preserved)")
    void case9_alreadyProxy() {
        Object bootstrap = pm.newCustomProxy(new ArrayList<>(Arrays.asList("x", "y")), false);
        assertNotNull(bootstrap, "precondition: bootstrap proxy must be non-null");
        assertTrue(bootstrap instanceof Proxy, "precondition: bootstrap must implement Proxy");

        Object result = pm.newCustomProxy(bootstrap, false);

        assertNotNull(result);
        assertSame(bootstrap, result);
    }

    /**
     * Case #10 — {@code orig} is {@code null}, {@code autoOff = false}.
     * {@code newCustomProxy} returns {@code null} without throwing.
     */
    @Test
    @DisplayName("#10 null — returns null (early exit, no exception)")
    void case10_null() {
        Object result = pm.newCustomProxy(null, false);
        assertNull(result);
    }
}
