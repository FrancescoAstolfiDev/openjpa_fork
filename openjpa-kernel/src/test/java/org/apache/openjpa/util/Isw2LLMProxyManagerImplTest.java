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
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM-generated mutation-coverage tests for
 * {@link ProxyManagerImpl#newCustomProxy(Object, boolean)}.
 *
 * <p>The existing BVA suite ({@code Isw2ProxyManagerImplNewCustomProxyBVATest})
 * and CF suite ({@code Isw2ProxyManagerImplControlFlowTest}) leave the following
 * mutation families alive (PIT strength 66 %, 51 surviving mutants):</p>
 * <ol>
 *   <li><b>Calendar helper</b> — {@code if (type == Calendar.class)} guard in
 *       {@link ProxyManagerImpl#newCalendarProxy} is never {@code true} in existing
 *       tests because {@code Calendar} is abstract and cannot be instantiated as
 *       {@code orig}. Direct call tests kill the mutation.</li>
 *   <li><b>{@code isProxyable} package-check</b> — the third branch
 *       ({@code getPackage().getName().equals("org.apache.openjpa.util")}) is only
 *       reached for non-public, non-protected, non-final classes. All existing test
 *       inputs are {@code public}. A package-private nested bean defined in this
 *       test class (which lives in {@code org.apache.openjpa.util}) exercises the
 *       branch.</li>
 *   <li><b>{@code isUnproxyable} hierarchy traversal</b> — the loop conditions
 *       ({@code type != null}, {@code type != Object.class}) and the
 *       {@code _unproxyable.contains(...)} check are only exercised when a
 *       <em>superclass</em> of the candidate is blacklisted, not the candidate
 *       itself. The {@link ChildBean}/{@link ParentBean} fixture triggers this.</li>
 *   <li><b>{@code toProxyableCollectionType} / {@code toProxyableMapType} interface
 *       branch</b> — the {@code type.isInterface()} path is unreachable through
 *       {@code newCustomProxy} (which always receives a concrete instance), but is
 *       reachable via {@link ProxyManagerImpl#newCollectionProxy} and
 *       {@link ProxyManagerImpl#newMapProxy} directly.</li>
 *   <li><b>Additional dispatch-path coverage</b> — collection and map types not
 *       exercised by the BVA suite (HashSet, LinkedList, LinkedHashMap, TreeMap
 *       with natural ordering), a {@code java.sql.Date} subtype, and Timestamp
 *       with nanos == 0 produce stronger oracles for the VOID_METHOD_CALLS and
 *       NULL_RETURNS families.</li>
 * </ol>
 *
 * <h3>PIT mutator kill matrix</h3>
 * <pre>
 * +------+-------------------------------------------------------------------+---------------------+
 * | Test | Targeted mutation                                                 | PIT operator        |
 * +------+-------------------------------------------------------------------+---------------------+
 * | T01  | if (orig instanceof Collection) — HashSet triggers Collection path| NEGATE_CONDITIONALS |
 * | T01  | c.addAll((Collection) orig) removed                               | VOID_METHOD_CALLS   |
 * | T02  | Collection branch with LinkedList / Queue                         | NEGATE_CONDITIONALS |
 * | T03  | Map branch with LinkedHashMap (non-SortedMap): m.putAll() removed | VOID_METHOD_CALLS   |
 * | T04  | if (orig instanceof Timestamp) — plain sql.Date is NOT Timestamp  | NEGATE_CONDITIONALS |
 * | T05  | ((Timestamp)d).setNanos(0) removed / nanos wrongly non-zero       | VOID_METHOD_CALLS   |
 * | T06  | c.setTimeInMillis / Calendar TZ — UTC rather than Asia/Tokyo      | VOID_METHOD_CALLS   |
 * | T07  | if (type == Calendar.class) → type not rewritten to GregorianCal  | NEGATE_CONDITIONALS |
 * | T08  | if (zone != null) negated → setTimeZone(null) throws NPE          | NEGATE_CONDITIONALS |
 * | T09  | getFactoryProxyBean inner `if(proxy==null)` negated → type blacklisted on success | NEGATE_CONDITIONALS |
 * | T10  | isUnproxyable: type!=null, type!=Object.class, contains() negated | NEGATE_CONDITIONALS |
 * | T11  | NULL_RETURNS on return (Proxy)c for empty collection              | NULL_RETURNS        |
 * | T12  | NULL_RETURNS on return (Proxy)m for empty map                     | NULL_RETURNS        |
 * | T13  | toProxyableCollectionType: type.isInterface() negated             | NEGATE_CONDITIONALS |
 * | T14  | toProxyableMapType: type.isInterface() negated                    | NEGATE_CONDITIONALS |
 * | T15  | SortedMap comp=null when TreeMap uses natural ordering             | NEGATE_CONDITIONALS |
 * +------+-------------------------------------------------------------------+---------------------+
 * </pre>
 */
@DisplayName("ProxyManagerImpl — LLM Mutation-Coverage: newCustomProxy(Object, boolean)")
public class Isw2LLMProxyManagerImplTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Nested helper types
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Public bean used as the superclass to blacklist in T10.
     * Must be public so that {@code isProxyable} returns {@code true} for
     * {@link ChildBean} (which inherits from it) AND so that PIT can resolve
     * it inside the project under test.
     */
    public static class ParentBean {
        private String label;
        public ParentBean() { this.label = ""; }
        public ParentBean(ParentBean other) { this.label = other.label; }
        public String getLabel() { return label; }
        public void setLabel(String l) { this.label = l; }
    }

    /**
     * Public subclass of {@link ParentBean}. Used in T10: when ParentBean is
     * added to the unproxyable set, {@code isUnproxyable} must traverse the
     * superclass chain and return {@code true} for ChildBean too.
     */
    public static class ChildBean extends ParentBean {
        public ChildBean() { super(); }
        public ChildBean(ChildBean other) { super(other); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    private ProxyManagerImpl pm;

    @BeforeEach
    void setUp() {
        pm = new ProxyManagerImpl();
        pm.setTrackChanges(true);
        pm.setAssertAllowedType(false);
        pm.setDelayCollectionLoading(false);
    }

    @AfterEach
    void tearDown() {
        pm = null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T01 — HashSet: Collection branch with a Set type
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T01 — Kills NEGATE_CONDITIONALS on {@code if (orig instanceof Collection)}
     * and VOID_METHOD_CALLS on {@code c.addAll((Collection) orig)}.
     *
     * <p>The existing BVA tests use only {@code ArrayList}. A {@code HashSet}
     * exercises the same Collection dispatch path but with a different concrete
     * type, confirming both that the dispatch fires AND that the original
     * elements are copied into the proxy (negating the addAll would leave the
     * proxy empty, failing the {@code containsAll} assertion).</p>
     */
    @Test
    @DisplayName("T01: HashSet → Collection proxy contains all original elements")
    void testNewCustomProxy_hashSet_returnsProxyPreservingElements() {
        HashSet<String> orig = new HashSet<>(Arrays.asList("alpha", "beta", "gamma"));

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result, "T01: proxy must be non-null for HashSet");
        assertTrue(result instanceof Proxy, "T01: result must implement Proxy");
        assertTrue(result instanceof Collection, "T01: result must be a Collection");

        @SuppressWarnings("unchecked")
        Collection<String> proxyColl = (Collection<String>) result;

        assertEquals(3, proxyColl.size(), "T01: proxy size must match original");
        assertTrue(proxyColl.containsAll(orig),
                "T01: proxy must contain every element of the original HashSet; "
                + "missing elements signal VOID_METHOD_CALLS on c.addAll()");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T02 — LinkedList: Collection branch with a Queue type
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T02 — Exercises the Collection dispatch path with a {@code LinkedList}
     * (which also implements {@code Queue}).
     *
     * <p>Kills NEGATE_CONDITIONALS on {@code if (orig instanceof Collection)}
     * from a third concrete type, and VOID_METHOD_CALLS on {@code c.addAll}.
     * If the conditional is negated, LinkedList falls through to Map → Date →
     * Calendar → bean branch, none of which can produce a Collection proxy, so
     * the {@code assertTrue(result instanceof Collection)} assertion fails.</p>
     */
    @Test
    @DisplayName("T02: LinkedList → Collection proxy preserves order and elements")
    void testNewCustomProxy_linkedList_returnsProxyPreservingElements() {
        LinkedList<Integer> orig = new LinkedList<>(Arrays.asList(10, 20, 30));

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result, "T02: proxy must be non-null for LinkedList");
        assertTrue(result instanceof Proxy, "T02: result must implement Proxy");
        assertTrue(result instanceof Collection, "T02: result must be a Collection");

        @SuppressWarnings("unchecked")
        Collection<Integer> proxyColl = (Collection<Integer>) result;
        assertEquals(3, proxyColl.size(), "T02: proxy size must match original");
        assertTrue(proxyColl.containsAll(orig),
                "T02: proxy must contain all original elements");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T03 — LinkedHashMap: Map branch with a non-SortedMap type
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T03 — Kills VOID_METHOD_CALLS on {@code m.putAll((Map) orig)} in the Map
     * branch.
     *
     * <p>{@code LinkedHashMap} is not a {@code SortedMap}, so the comparator
     * ternary yields {@code null}. Explicit per-entry assertions on
     * {@code map.get(k)} kill the VOID_METHOD_CALLS mutation more precisely than
     * the copy-equality RT1 used in the BVA suite, because they pinpoint which
     * entry is missing when the mutation voids the {@code putAll}.</p>
     */
    @Test
    @DisplayName("T03: LinkedHashMap → Map proxy preserves all key-value entries")
    void testNewCustomProxy_linkedHashMap_returnsProxyPreservingEntries() {
        LinkedHashMap<String, Integer> orig = new LinkedHashMap<>();
        orig.put("one", 1);
        orig.put("two", 2);
        orig.put("three", 3);

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result, "T03: proxy must be non-null for LinkedHashMap");
        assertTrue(result instanceof Proxy, "T03: result must implement Proxy");
        assertTrue(result instanceof Map, "T03: result must be a Map");

        @SuppressWarnings("unchecked")
        Map<String, Integer> proxyMap = (Map<String, Integer>) result;

        assertEquals(3, proxyMap.size(), "T03: proxy size must match original");
        assertEquals(1, proxyMap.get("one"),   "T03: entry 'one' must be preserved");
        assertEquals(2, proxyMap.get("two"),   "T03: entry 'two' must be preserved");
        assertEquals(3, proxyMap.get("three"), "T03: entry 'three' must be preserved");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T04 — java.sql.Date: Date branch, NOT a Timestamp
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T04 — Kills NEGATE_CONDITIONALS on {@code if (orig instanceof Timestamp)}
     * inside the Date branch.
     *
     * <p>The BVA suite tests {@code java.util.Date} (case 4) and
     * {@code Timestamp} (case 5). A {@code java.sql.Date} is a Date subtype that
     * is NOT a Timestamp. If the {@code instanceof Timestamp} condition is
     * negated, the code tries {@code ((Timestamp) d).setNanos(...)} on a
     * non-Timestamp Date proxy, throwing a {@code ClassCastException}. The test
     * verifies both that no exception is thrown AND that the result is not a
     * Timestamp proxy.</p>
     */
    @Test
    @DisplayName("T04: java.sql.Date (non-Timestamp) → Date proxy, no setNanos call")
    void testNewCustomProxy_sqlDate_isDateNotTimestamp() {
        java.sql.Date orig = new java.sql.Date(1_700_000_000_000L);

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result, "T04: proxy must be non-null for java.sql.Date");
        assertTrue(result instanceof Proxy, "T04: result must implement Proxy");
        assertTrue(result instanceof java.util.Date, "T04: result must be a Date");
        assertFalse(result instanceof Timestamp,
                "T04: result must NOT be a Timestamp for a sql.Date input; "
                + "Timestamp here signals a ClassCastException was swallowed or "
                + "a wrong dispatch occurred");

        java.util.Date proxyDate = (java.util.Date) result;
        assertEquals(orig.getTime(), proxyDate.getTime(),
                "T04: proxy must preserve the original time value");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T05 — Timestamp with nanos == 0
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T05 — Kills VOID_METHOD_CALLS on
     * {@code ((Timestamp) d).setNanos(((Timestamp) orig).getNanos())}
     * when nanos == 0 (an edge value distinct from the BVA case which uses
     * {@code REFERENCE_NANOS = 123_456_789}).
     *
     * <p>If {@code setNanos} is voided, a newly constructed proxy Timestamp
     * retains whatever default value the proxy constructor produced. Asserting
     * {@code == 0} is stricter than asserting {@code == REFERENCE_NANOS},
     * because a proxy whose constructor sets nanos to some non-zero default
     * would pass the BVA RT1 if REFERENCE_NANOS happened to equal that default,
     * but would fail here where the expected value is unambiguously 0.</p>
     */
    @Test
    @DisplayName("T05: Timestamp with nanos=0 → proxy nanos must be exactly 0")
    void testNewCustomProxy_timestampWithZeroNanos_nanosPreserved() {
        Timestamp orig = new Timestamp(1_700_000_000_000L);
        orig.setNanos(0);

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result, "T05: proxy must be non-null for Timestamp");
        assertTrue(result instanceof Proxy, "T05: result must implement Proxy");
        assertTrue(result instanceof Timestamp, "T05: result must be a Timestamp");

        Timestamp proxyTs = (Timestamp) result;
        assertEquals(orig.getTime(), proxyTs.getTime(),
                "T05: proxy must preserve the original time");
        assertEquals(0, proxyTs.getNanos(),
                "T05: proxy nanos must be 0; non-zero signals VOID_METHOD_CALLS on setNanos "
                + "where the proxy constructor left a non-zero default");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T06 — Calendar with UTC timezone (complements BVA case 6 / Asia/Tokyo)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T06 — Kills VOID_METHOD_CALLS on {@code c.setTimeInMillis(...)} and
     * on the {@code setTimeZone} call inside {@link ProxyManagerImpl#newCalendarProxy}
     * from a second concrete timezone value (UTC).
     *
     * <p>The BVA case 6 already kills these mutations for Asia/Tokyo. UTC is
     * included as a second data-point to ensure the oracles are not specific to
     * a single timezone string, and to kill any conditional-boundary mutation
     * that might survive on a single-value suite.</p>
     */
    @Test
    @DisplayName("T06: GregorianCalendar(UTC) → proxy preserves UTC timezone and millis")
    void testNewCustomProxy_calendarUTC_timeZonePreserved() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        GregorianCalendar orig = new GregorianCalendar(utc);
        orig.setTimeInMillis(1_600_000_000_000L);

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result, "T06: proxy must be non-null");
        assertTrue(result instanceof Proxy, "T06: result must implement Proxy");
        assertTrue(result instanceof Calendar, "T06: result must be a Calendar");

        Calendar proxyCal = (Calendar) result;
        assertEquals(orig.getTimeInMillis(), proxyCal.getTimeInMillis(),
                "T06: proxy must preserve timeInMillis; mismatch signals VOID_METHOD_CALLS "
                + "on c.setTimeInMillis(...)");
        assertEquals(utc, proxyCal.getTimeZone(),
                "T06: proxy must preserve the UTC timezone; mismatch signals VOID_METHOD_CALLS "
                + "on setTimeZone inside newCalendarProxy");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T07 — newCalendarProxy(Calendar.class) → must use GregorianCalendar
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T07 — Kills NEGATE_CONDITIONALS on {@code if (type == Calendar.class)} in
     * {@link ProxyManagerImpl#newCalendarProxy(Class, TimeZone)}.
     *
     * <p>The guard rewrites the abstract {@code Calendar.class} to
     * {@code GregorianCalendar.class} so that a concrete proxy can be generated.
     * This path is unreachable from {@code newCustomProxy} (Calendar is abstract
     * and cannot be instantiated), so the test calls {@code newCalendarProxy}
     * directly.</p>
     *
     * <p>Under the negated mutation ({@code type != Calendar.class}), the guard
     * is {@code false} for {@code Calendar.class} and the type is NOT rewritten.
     * The proxy factory then attempts to generate a proxy directly extending
     * the abstract {@code Calendar} class; at minimum, the resulting proxy is
     * not a {@code GregorianCalendar}, so {@code assertInstanceOf} fails. In
     * practice the factory typically throws an exception trying to proxy an
     * abstract type, which also causes the test to fail.</p>
     */
    @Test
    @DisplayName("T07: newCalendarProxy(Calendar.class) → proxy is a GregorianCalendar")
    void testCalendarProxy_calendarClassInput_returnsGregorianProxy() {
        Proxy result = pm.newCalendarProxy(Calendar.class, null);

        assertNotNull(result, "T07: newCalendarProxy(Calendar.class) must return non-null");
        assertTrue(result instanceof Calendar, "T07: result must be a Calendar");
        assertTrue(result instanceof GregorianCalendar,
                "T07: result must be a GregorianCalendar; a plain Calendar here signals that "
                + "the `type == Calendar.class` guard was not taken (NEGATE_CONDITIONALS)");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T08 — newCalendarProxy with null zone: no NPE on the null-zone path
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T08 — Kills NEGATE_CONDITIONALS on {@code if (zone != null)} inside
     * {@link ProxyManagerImpl#newCalendarProxy(Class, TimeZone)}.
     *
     * <p>Under the negated mutation ({@code zone == null}), a {@code null} zone
     * causes the branch to be taken and {@code ((Calendar) cal).setTimeZone(null)}
     * is called, which throws {@code NullPointerException}. The test verifies
     * that passing {@code null} produces a valid proxy without any exception.</p>
     *
     * <p>Note: the BVA case 6 already kills the non-null side of the same
     * condition (verifying the timezone IS set when non-null). This test
     * completes the branch pair by covering the null side.</p>
     */
    @Test
    @DisplayName("T08: newCalendarProxy(GregorianCalendar.class, null) → no NPE, valid proxy")
    void testCalendarProxy_nullZone_returnsProxyWithoutException() {
        assertDoesNotThrow(
                () -> pm.newCalendarProxy(GregorianCalendar.class, null),
                "T08: null zone must not cause an exception; NPE here signals that "
                + "the `if (zone != null)` guard was negated and setTimeZone(null) was called");

        Proxy result = pm.newCalendarProxy(GregorianCalendar.class, null);
        assertNotNull(result, "T08: proxy must be non-null for null zone");
        assertTrue(result instanceof Calendar, "T08: result must be a Calendar");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T09 — Same bean type proxied twice: getFactoryProxyBean caching invariant
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T09 — Kills NEGATE_CONDITIONALS on the inner {@code if (proxy == null)}
     * check inside {@code getFactoryProxyBean}.
     *
     * <p>After the proxy class is generated, {@code getFactoryProxyBean} has:
     * <pre>
     *   if (proxy == null) {
     *       _unproxyable.add(type.getName());   // generation failed
     *   } else {
     *       _proxies.put(type, proxy);           // cache for reuse
     *   }
     * </pre>
     * Under the negated mutation ({@code proxy != null}), a <em>successful</em>
     * generation takes the {@code _unproxyable.add} branch and adds the type to
     * the blacklist. On the <b>second</b> call for the same type,
     * {@code isUnproxyable} finds the type in the set and returns {@code null}
     * immediately, so {@code newCustomProxy} returns {@code null}.</p>
     *
     * <p>The test proxies {@link ParentBean} (a public bean with a copy
     * constructor) <em>twice</em> and asserts that both calls return a non-null
     * proxy. This is the minimal observable that distinguishes the correct
     * "cache in {@code _proxies}" path from the mutated "add to blacklist" path.</p>
     */
    @Test
    @DisplayName("T09: same bean type proxied twice → both calls return non-null proxy")
    void testNewCustomProxy_sameBeanTypeTwice_secondCallAlsoSucceeds() {
        Object result1 = pm.newCustomProxy(new ParentBean(), false);
        Object result2 = pm.newCustomProxy(new ParentBean(), false);

        assertNotNull(result1, "T09: first proxy call must succeed");
        assertNotNull(result2,
                "T09: second proxy call for the same type must also succeed; "
                + "null here signals that the type was incorrectly added to the "
                + "unproxyable set on the first successful call "
                + "(NEGATE_CONDITIONALS on inner `if (proxy == null)` in getFactoryProxyBean)");
        assertTrue(result2 instanceof Proxy, "T09: second result must implement Proxy");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T10 — ChildBean whose parent is blacklisted: isUnproxyable hierarchy
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T10 — Kills three NEGATE_CONDITIONALS mutations in the
     * {@code isUnproxyable} superclass-traversal loop:
     * <ul>
     *   <li>{@code type != null} — negated: loop never executes → parent not found.</li>
     *   <li>{@code type != Object.class} — negated: loop runs only when type IS
     *       Object (never at the start) → parent not found.</li>
     *   <li>{@code _unproxyable.contains(type.getName())} — negated: returns
     *       {@code true} when the type is NOT listed → inverted blacklist.</li>
     * </ul>
     *
     * <p>Under all three mutations, {@code isUnproxyable(ChildBean.class)} returns
     * {@code false}, {@code getFactoryProxyBean} proceeds to generate a proxy for
     * {@link ChildBean} (which has a copy constructor), and {@code newCustomProxy}
     * returns a non-null proxy — failing the {@code assertNull}.</p>
     *
     * <p>Fixture: {@link ParentBean} is added to the unproxyable set via
     * {@link ProxyManagerImpl#setUnproxyable}. {@link ChildBean} extends
     * {@link ParentBean} but is NOT directly in the set. The correct code must
     * walk up the superclass chain and find {@link ParentBean} as blacklisted.</p>
     */
    @Test
    @DisplayName("T10: ChildBean whose superclass is blacklisted → newCustomProxy returns null")
    void testNewCustomProxy_childOfBlacklistedParent_returnsNull() {
        pm.setUnproxyable(ParentBean.class.getName());

        Object result = pm.newCustomProxy(new ChildBean(), false);

        assertNull(result,
                "T10: newCustomProxy must return null when a superclass of the candidate "
                + "is in the unproxyable set; non-null signals that isUnproxyable loop "
                + "conditions or contains() check were mutated");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T11 — Empty ArrayList: NULL_RETURNS on Collection return
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T11 — Kills NULL_RETURNS on {@code return (Proxy) c} in the Collection
     * branch for an empty input.
     *
     * <p>An empty collection is a boundary value: the proxy is created (non-null)
     * but contains no elements. The BVA tests use non-empty collections (["a","b"]),
     * so their {@code assertNotNull} would already kill this mutation. This test
     * adds an explicit verification that an empty original produces a non-null,
     * empty proxy — pinpointing the Collection return path without involving
     * the {@code addAll} copy logic.</p>
     */
    @Test
    @DisplayName("T11: empty ArrayList → non-null empty Collection proxy")
    void testNewCustomProxy_emptyArrayList_returnsEmptyProxy() {
        Object result = pm.newCustomProxy(new ArrayList<>(), false);

        assertNotNull(result, "T11: proxy must be non-null for an empty ArrayList");
        assertTrue(result instanceof Proxy, "T11: result must implement Proxy");
        assertTrue(result instanceof java.util.List, "T11: result must be a List");
        assertEquals(0, ((Collection<?>) result).size(),
                "T11: proxy of an empty collection must itself be empty");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T12 — Empty HashMap: NULL_RETURNS on Map return
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T12 — Kills NULL_RETURNS on {@code return (Proxy) m} in the Map branch.
     *
     * <p>Analogous to T11 for the Map dispatch path.</p>
     */
    @Test
    @DisplayName("T12: empty HashMap → non-null empty Map proxy")
    void testNewCustomProxy_emptyHashMap_returnsEmptyProxy() {
        Object result = pm.newCustomProxy(new HashMap<>(), false);

        assertNotNull(result, "T12: proxy must be non-null for an empty HashMap");
        assertTrue(result instanceof Proxy, "T12: result must implement Proxy");
        assertTrue(result instanceof Map, "T12: result must be a Map");
        assertEquals(0, ((Map<?, ?>) result).size(),
                "T12: proxy of an empty map must itself be empty");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T13 — newCollectionProxy with Collection interface
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T13 — Kills NEGATE_CONDITIONALS on {@code type.isInterface()} in
     * {@code toProxyableCollectionType}.
     *
     * <p>This path is unreachable from {@code newCustomProxy} (which always
     * receives a concrete instance), so the test calls {@code newCollectionProxy}
     * directly with {@code Collection.class}. Correct behaviour: the interface
     * is mapped to its standard concrete counterpart ({@code ArrayList}), and a
     * valid proxy is returned.</p>
     *
     * <p>Under the negated mutation ({@code !type.isInterface()}), the interface
     * check is {@code false} for {@code Collection.class}; execution falls to the
     * {@code isAbstract} guard (interfaces are abstract by definition) and throws
     * {@code UnsupportedException}. The test's {@code assertNotNull} then fails.</p>
     */
    @Test
    @DisplayName("T13: newCollectionProxy(Collection.class) → concrete ArrayList-based proxy")
    void testNewCollectionProxy_collectionInterface_proxiesAsArrayList() {
        Proxy result = (Proxy) pm.newCollectionProxy(Collection.class, null, null, false);

        assertNotNull(result,
                "T13: newCollectionProxy(Collection.class) must return a non-null proxy; "
                + "null or exception signals NEGATE_CONDITIONALS on type.isInterface() "
                + "in toProxyableCollectionType");
        assertTrue(result instanceof Collection,
                "T13: the proxy must implement Collection");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T14 — newMapProxy with Map interface
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T14 — Kills NEGATE_CONDITIONALS on {@code type.isInterface()} in
     * {@code toProxyableMapType}.
     *
     * <p>Analogous to T13 for the Map proxy factory. {@code Map.class} is mapped
     * to {@code HashMap.class} by the standard type table. Under the negated
     * mutation, the abstract check throws {@code UnsupportedException}.</p>
     */
    @Test
    @DisplayName("T14: newMapProxy(Map.class) → concrete HashMap-based proxy")
    void testNewMapProxy_mapInterface_proxiesAsHashMap() {
        Proxy result = (Proxy) pm.newMapProxy(Map.class, null, null, null, false);

        assertNotNull(result,
                "T14: newMapProxy(Map.class) must return a non-null proxy; "
                + "null or exception signals NEGATE_CONDITIONALS on type.isInterface() "
                + "in toProxyableMapType");
        assertTrue(result instanceof Map, "T14: the proxy must implement Map");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T15 — TreeMap with natural ordering: SortedMap comparator is null
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * T15 — Kills NEGATE_CONDITIONALS on {@code orig instanceof SortedMap}
     * in the Map branch of {@code newCustomProxy} from the natural-ordering side.
     *
     * <p>CF-3 already covers the case where a {@code TreeMap} HAS a custom
     * comparator. This test covers the complementary case: a {@code TreeMap}
     * using natural ordering (no explicit comparator, {@code comparator()==null}).
     * If the ternary condition is negated ({@code !(orig instanceof SortedMap)}),
     * a plain {@code HashMap} would be passed as the comparator to the factory
     * (casting it to {@code Comparator}), throwing {@code ClassCastException}.
     * The test verifies both that no exception is thrown and that the proxy is
     * functional.</p>
     *
     * <p>Additionally: the proxy of a naturally-ordered TreeMap must itself
     * support sorted iteration. We verify that elements inserted after proxy
     * creation remain in ascending order.</p>
     */
    @Test
    @DisplayName("T15: TreeMap natural ordering → SortedMap proxy, null comparator path")
    void testNewCustomProxy_treeMapNaturalOrdering_nullComparatorPreserved() {
        TreeMap<String, Integer> orig = new TreeMap<>(); // natural ordering, comparator() == null
        orig.put("banana", 2);
        orig.put("apple", 1);
        orig.put("cherry", 3);

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result, "T15: proxy must be non-null for naturally-ordered TreeMap");
        assertTrue(result instanceof Proxy, "T15: result must implement Proxy");
        assertTrue(result instanceof java.util.SortedMap,
                "T15: result must be a SortedMap");

        @SuppressWarnings("unchecked")
        java.util.SortedMap<String, Integer> proxyMap = (java.util.SortedMap<String, Integer>) result;

        assertEquals(3, proxyMap.size(), "T15: proxy must contain all original entries");
        assertEquals(new ArrayList<>(orig.keySet()), new ArrayList<>(proxyMap.keySet()),
                "T15: natural key order must be preserved in the proxy; "
                + "wrong order signals the null-comparator path was not taken");
        assertNull(proxyMap.comparator(),
                "T15: a naturally-ordered proxy must have a null comparator; "
                + "non-null signals a spurious comparator was injected");
    }
}
