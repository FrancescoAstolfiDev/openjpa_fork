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
import java.util.Arrays;
import java.util.Comparator;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.openjpa.enhance.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Control-Flow (Adequacy) tests for
 * {@link ProxyManagerImpl#newCustomProxy(Object, boolean)}.
 *
 * <p>The BVA suite ({@code Isw2ProxyManagerImplNewCustomProxyBVATest}) covers
 * the most common dispatch branches of {@code newCustomProxy} but leaves a
 * residual set of lines and branches uncovered. According to the JaCoCo
 * report on the BVA baseline, the {@code newCustomProxy} method shows
 * <b>83% instruction coverage</b> and <b>75% branch coverage</b>, with
 * 3 missed lines (out of 33) and 5 missed branches (out of 13).</p>
 *
 * <h3>1. Coverage gaps targeted by this class</h3>
 * <p>Each test in this class is designed to cover a specific gap identified
 * on the BVA baseline:</p>
 * <pre>
 * +-------+--------------------------------------------+-----------------------------------------------------+
 * | Gap   | Source location                            | Trigger                                             |
 * +-------+--------------------------------------------+-----------------------------------------------------+
 * | CF-1  | L316 true-branch, L317                     | orig is a manageable entity (PersistenceCapable)    |
 * | CF-2  | L322 true-branch, L323                     | orig is a SortedSet (TreeSet with Comparator)       |
 * | CF-3  | L330 true-branch, L331                     | orig is a SortedMap (TreeMap with Comparator)       |
 * | CF-4  | L343 false-branch, L350, L351 (proxy!=null)| orig is a non-collection bean with copy constructor |
 * | CF-5  | L351 ternary (proxy==null)                 | orig is a bean without an applicable copy ctor      |
 * +-------+--------------------------------------------+-----------------------------------------------------+
 * </pre>
 *
 * <p>Together with the existing BVA suite, these five cases drive the
 * {@code newCustomProxy} method to 100% line coverage and exercise all
 * remaining branches that are reachable in a unit-test context (i.e. those
 * not gated by the bytecode-generation backend).</p>
 *
 * <h3>2. Test strategy</h3>
 * <p>Each test is a standalone {@code @Test} method. A fresh
 * {@link ProxyManagerImpl} is built before every test via {@code @BeforeEach},
 * left in its default configuration. No Mockito is used: manageability is
 * achieved with a minimal hand-written stub of {@link PersistenceCapable}
 * (registered with {@link PCRegistry} so that {@link ImplHelper#isManageable}
 * returns {@code true}), and the bean cases use plain nested POJOs.</p>
 *
 * <h3>3. Oracle</h3>
 * <p>Control-flow tests assert the <i>observed return value</i> of the
 * method along the targeted path. The goal is to confirm that the path is
 * actually executed and produces the documented contractual result: for
 * gaps CF-1 and CF-5 the contract is "return null silently", for CF-2 and
 * CF-3 it is "return a proxy whose comparator is preserved", for CF-4 it is
 * "return a non-null bean proxy whose state mirrors the original".</p>
 */
@DisplayName("ProxyManagerImpl — Control Flow: newCustomProxy(Object, boolean)")
public class Isw2ProxyManagerImplControlFlowTest {

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

    /* ============================================================
     * CF-1 — Manageable entity path (L316 true → L317)
     * ============================================================ */

    /**
     * CF-1 — When {@code orig} is a manageable entity (i.e. an instance of a
     * class implementing {@link PersistenceCapable}, registered with
     * {@link PCRegistry}), {@code newCustomProxy} must return {@code null}
     * without attempting any proxy dispatch.
     *
     * <p>Covers: L316 true-branch, L317 ({@code return null}).</p>
     *
     * <p>The stub class {@link ManageableStub} implements
     * {@code PersistenceCapable} with empty bodies and is registered eagerly
     * in the test so that {@code ImplHelper.isManageable(orig)} returns
     * {@code true} for any of its instances.</p>
     */
    @Test
    @DisplayName("CF-1 — manageable entity → returns null silently")
    void cf1_manageableEntity_returnsNull() {
        // Register the stub class so isManageable() will resolve to true.
        ManageableStub.ensureRegistered();

        ManageableStub orig = new ManageableStub();
        assertTrue(ImplHelper.isManageable(orig),
                "precondition: stub must be recognised as manageable");

        Object result = pm.newCustomProxy(orig, false);

        assertNull(result,
                "CF-1: newCustomProxy must return null on a manageable entity");
    }

    /* ============================================================
     * CF-2 — SortedSet path (L322 true → L323 comparator preservation)
     * ============================================================ */

    /**
     * CF-2 — When {@code orig} is a {@link SortedSet} (e.g. {@link TreeSet}
     * with a custom {@link Comparator}), {@code newCustomProxy} must enter
     * the {@code Collection} branch, evaluate the {@code instanceof SortedSet}
     * check as {@code true}, retrieve the comparator and propagate it to the
     * collection proxy factory.
     *
     * <p>Covers: L322 true-branch, L323 ({@code ((SortedSet) orig).comparator()}).</p>
     */
    @Test
    @DisplayName("CF-2 — SortedSet (TreeSet) → proxy preserves comparator")
    void cf2_sortedSet_comparatorPropagated() {
        Comparator<String> reverse = Comparator.reverseOrder();
        TreeSet<String> orig = new TreeSet<>(reverse);
        orig.add("b");
        orig.add("a");
        orig.add("c");

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result, "CF-2: proxy must be non-null on a SortedSet");
        assertTrue(result instanceof Proxy,
                "CF-2: result must implement Proxy");
        assertTrue(result instanceof SortedSet,
                "CF-2: result must be a SortedSet (Collection dispatch on a SortedSet input)");

        SortedSet<?> sortedProxy = (SortedSet<?>) result;
        assertNotNull(sortedProxy.comparator(),
                "CF-2: proxy comparator must be non-null when orig has one");
        // The comparator carried by the proxy must produce the same ordering
        // as the one carried by orig (reverse natural order: 'c','b','a').
        assertEquals(Arrays.asList("c", "b", "a"), new ArrayList<>(sortedProxy),
                "CF-2: proxy must preserve the comparator-induced ordering");
    }

    /* ============================================================
     * CF-3 — SortedMap path (L330 true → L331 comparator preservation)
     * ============================================================ */

    /**
     * CF-3 — When {@code orig} is a {@link SortedMap} (e.g. {@link TreeMap}
     * with a custom {@link Comparator}), {@code newCustomProxy} must enter
     * the {@code Map} branch, evaluate the {@code instanceof SortedMap}
     * check as {@code true}, retrieve the comparator and propagate it to the
     * map proxy factory.
     *
     * <p>Covers: L330 true-branch, L331 ({@code ((SortedMap) orig).comparator()}).</p>
     */
    @Test
    @DisplayName("CF-3 — SortedMap (TreeMap) → proxy preserves comparator")
    void cf3_sortedMap_comparatorPropagated() {
        Comparator<String> reverse = Comparator.reverseOrder();
        TreeMap<String, Integer> orig = new TreeMap<>(reverse);
        orig.put("b", 2);
        orig.put("a", 1);
        orig.put("c", 3);

        Object result = pm.newCustomProxy(orig, false);

        assertNotNull(result, "CF-3: proxy must be non-null on a SortedMap");
        assertTrue(result instanceof Proxy,
                "CF-3: result must implement Proxy");
        assertTrue(result instanceof SortedMap,
                "CF-3: result must be a SortedMap (Map dispatch on a SortedMap input)");

        SortedMap<?, ?> sortedProxy = (SortedMap<?, ?>) result;
        assertNotNull(sortedProxy.comparator(),
                "CF-3: proxy comparator must be non-null when orig has one");
        // Reverse natural order on keys → "c","b","a".
        assertEquals(Arrays.asList("c", "b", "a"), new ArrayList<>(sortedProxy.keySet()),
                "CF-3: proxy must preserve the comparator-induced key ordering");
    }

    /* ============================================================
     * CF-4 — Bean fall-through path with copy constructor
     * (L343 false → L350 → L351 proxy != null)
     * ============================================================ */

    /**
     * CF-4 — When {@code orig} is neither a Collection, nor a Map, nor a
     * Date, nor a Calendar, but is a proxyable class with an applicable copy
     * constructor, the method falls through to the bean-proxy section and
     * delegates to {ProxyManagerImpl#getFactoryProxyBean}. The
     * resulting {@code proxy != null} branch of the final ternary returns a
     * non-null bean proxy.
     *
     * <p>Covers: L343 false-branch (skip of {@code Calendar}), L350
     * ({@code getFactoryProxyBean}), L351 with {@code proxy != null}.</p>
     *
     * <p>Note: bean proxy generation requires a successful bytecode
     * synthesis on the target class. If the environment cannot generate the
     * proxy class (e.g. ASM unavailable, classloader restrictions), the
     * method legitimately returns {@code null} via the
     * {@code proxy == null} branch — which is the very oracle of CF-5. The
     * test therefore accepts either outcome on the result reference but
     * verifies that the dispatch reached the bean section (i.e. no exception
     * was raised by the unhandled fall-through).</p>
     */
    @Test
    @DisplayName("CF-4 — bean with copy constructor → bean section reached without exception")
    void cf4_bean_withCopyConstructor() {
        SimpleBean orig = new SimpleBean("hello", 42);

        // No exception must propagate when control reaches the bean
        // fall-through. The result is either a non-null bean proxy (proxy
        // factory succeeded) or null (factory rejected the class), both of
        // which are legitimate exits along the targeted path.
        Object result = pm.newCustomProxy(orig, false);

        // Categorical non-null assertion — kills the PIT mutation on L351
        // (`replaced return value with null for newCustomProxy`). Without this
        // line the conditional block below would silently skip when the mutant
        // forces a null return, leaving the mutation undetected.
        assertNotNull(result,
                "CF-4: newCustomProxy must return a non-null proxy for a bean "
                        + "with an applicable copy constructor; a null result here is the "
                        + "signature of the L351 `replaced return value with null` mutation");

        if (result != null) {
            assertTrue(result instanceof Proxy,
                    "CF-4: when non-null, result must implement Proxy");
            // The bean proxy must preserve the original state.
            assertTrue(result instanceof SimpleBean,
                    "CF-4: when non-null, result must be a SimpleBean");
            SimpleBean asBean = (SimpleBean) result;
            assertEquals(orig.getName(), asBean.getName(),
                    "CF-4: bean proxy must preserve 'name' from original");
            assertEquals(orig.getCount(), asBean.getCount(),
                    "CF-4: bean proxy must preserve 'count' from original");
        }
        // No assertion on null vs non-null: CF-5 covers the null sub-case.
    }

    /* ============================================================
     * CF-5 — Bean fall-through path with NO applicable copy constructor
     * (L351 ternary, proxy == null branch)
     * ============================================================ */

    /**
     * CF-5 — When {@code orig} reaches the bean fall-through section but the
     * proxy factory cannot produce a proxy bean for its class (no applicable
     * copy constructor, or bytecode generation fails), the final ternary at
     * L351 takes the {@code proxy == null} branch and the method returns
     * {@code null} silently.
     *
     * <p>Covers: L351 ternary, {@code proxy == null} branch.</p>
     *
     * <p>{@link UnsuitableBean} is a non-final, non-abstract, non-interface
     * class without a {@code UnsuitableBean(UnsuitableBean)} copy constructor;
     * the bean proxy factory therefore refuses to generate a proxy for it
     * and returns {@code null}.</p>
     */
    @Test
    @DisplayName("CF-5 — bean without copy constructor → returns null silently")
    void cf5_bean_withoutCopyConstructor() {
        UnsuitableBean orig = new UnsuitableBean();

        Object result = pm.newCustomProxy(orig, false);

        assertNull(result,
                "CF-5: newCustomProxy must return null for a bean lacking an applicable "
                        + "copy constructor");
    }

    /* ============================================================
     * Helper stubs (no Mockito)
     * ============================================================ */

    /**
     * Minimal {@link PersistenceCapable} stub. All callbacks have empty
     * bodies; only its presence in {@link PCRegistry} is required so that
     * {@link ImplHelper#isManageable} returns {@code true}.
     */
    @SuppressWarnings("rawtypes")
    static class ManageableStub implements PersistenceCapable {

        private static volatile boolean registered = false;

        /**
         * Registers the stub class with {@link PCRegistry} once per JVM, so
         * that subsequent {@code ImplHelper.isManageable} checks return
         * {@code true} on any instance.
         */
        static void ensureRegistered() {
            if (!registered) {
                synchronized (ManageableStub.class) {
                    if (!registered) {
                        PCRegistry.register(
                                ManageableStub.class,
                                new String[0],          // field names
                                new Class[0],           // field types
                                new byte[0],            // field flags
                                ManageableStub.class,   // persistence-capable superclass
                                "ManageableStub",       // alias
                                new ManageableStub());  // template instance
                        registered = true;
                    }
                }
            }
        }

        // ---------------- PersistenceCapable methods ----------------

        @Override public int pcGetEnhancementContractVersion() { return 0; }
        @Override public Object pcGetGenericContext()          { return null; }
        @Override public StateManager pcGetStateManager()      { return null; }
        @Override public void pcReplaceStateManager(StateManager sm) { /* no-op */ }
        @Override public void pcProvideField(int fieldIndex)   { /* no-op */ }
        @Override public void pcProvideFields(int[] fieldIndices) { /* no-op */ }
        @Override public void pcReplaceField(int fieldIndex)   { /* no-op */ }
        @Override public void pcReplaceFields(int[] fieldIndex){ /* no-op */ }
        @Override public void pcCopyFields(Object other, int[] fields) { /* no-op */ }
        @Override public void pcDirty(String fieldName)        { /* no-op */ }
        @Override public Object pcFetchObjectId()              { return null; }
        @Override public Object pcGetVersion()                 { return null; }
        @Override public boolean pcIsDirty()                   { return false; }
        @Override public boolean pcIsTransactional()           { return false; }
        @Override public boolean pcIsPersistent()              { return false; }
        @Override public boolean pcIsNew()                     { return false; }
        @Override public boolean pcIsDeleted()                 { return false; }
        @Override public Boolean pcIsDetached()                { return Boolean.FALSE; }
        @Override public PersistenceCapable pcNewInstance(StateManager sm, boolean clear) { return new ManageableStub(); }
        @Override public PersistenceCapable pcNewInstance(StateManager sm, Object oid, boolean clear) { return new ManageableStub(); }
        @Override public Object pcNewObjectIdInstance()        { return null; }
        @Override public Object pcNewObjectIdInstance(Object o){ return null; }
        @Override public void pcCopyKeyFieldsToObjectId(Object oid) { /* no-op */ }
        @Override public void pcCopyKeyFieldsToObjectId(FieldSupplier fs, Object oid) { /* no-op */ }
        @Override public void pcCopyKeyFieldsFromObjectId(FieldConsumer fc, Object oid) { /* no-op */ }
        @Override public Object pcGetDetachedState()           { return null; }
        @Override public void pcSetDetachedState(Object o)     { /* no-op */ }
    }

    /**
     * Simple bean for CF-4 — declares a copy constructor
     * {@code SimpleBean(SimpleBean)} which the proxy factory can use to
     * generate a bean proxy.
     */
    public static class SimpleBean {
        private String name;
        private int count;


        public SimpleBean(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public SimpleBean(SimpleBean other) {
            this.name = other.name;
            this.count = other.count;
        }

        public String getName()             { return name; }
        public void setName(String name)    { this.name = name; }
        public int getCount()               { return count; }
        public void setCount(int count)     { this.count = count; }
    }

    /**
     * Bean for CF-5 — no copy constructor, so {@code getFactoryProxyBean}
     * cannot produce a proxy and L351 takes the {@code proxy == null} branch.
     * The class is non-final, non-abstract and non-interface so it passes
     * { ProxyManagerImpl#isProxyable} and reaches the bean fall-through.
     */
    public static class UnsuitableBean {
        public UnsuitableBean() { /* no copy constructor on purpose */ }
    }
}
