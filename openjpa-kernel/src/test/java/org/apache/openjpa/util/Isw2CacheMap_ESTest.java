/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openjpa.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.apache.openjpa.lib.util.LRUMap;
import org.apache.openjpa.lib.util.ReferenceHashMap;
import org.apache.openjpa.lib.util.SizedMap;
import org.apache.openjpa.lib.util.collections.AbstractReferenceMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

public class Isw2CacheMap_ESTest {

    @Test
    @Timeout(4)
    public void test00() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, (-9));
        cacheMap0.cacheMapOverflowRemoved((Object) null, "org.apache.openjpa.lib.util.collections.AbstractHashedMap$EntrySet");
        int int0 = cacheMap0.size();
        assertEquals(1, int0);
        assertEquals((-1), cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test01() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        cacheMap0.setSoftReferenceSize(0);
        assertEquals(0, cacheMap0.getSoftReferenceSize());
    }

    @Test
    @Timeout(4)
    public void test02() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        cacheMap0.setCacheSize(1000);
        assertEquals(1000, cacheMap0.getCacheSize());
        assertEquals((-1), cacheMap0.getSoftReferenceSize());
    }

    @Test
    @Timeout(4)
    public void test03() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, 0);
        assertEquals(0, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test04() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, 1000, (-317), 1.0F, 2115);
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test05() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, (-1665), (-738), 1.0F);
        assertEquals((-1), cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test06() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        Set set0 = cacheMap0.entrySet();
        assertEquals(1000, cacheMap0.getCacheSize());
        assertEquals(0, set0.size());
    }

    @Test
    @Timeout(4)
    public void test07() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, 1008);
        Collection collection0 = cacheMap0.values();
        cacheMap0.entryRemoved(collection0, collection0, false);
        assertEquals(1008, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test08() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        cacheMap0.entryAdded((Object) null, (Object) null);
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test09() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true);
        cacheMap0.put(cacheMap0, cacheMap0);
        int int0 = cacheMap0.size();
        assertEquals(1, int0);
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test10() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, 1000);
        Object object0 = cacheMap0.remove((Map) cacheMap0, (Object) cacheMap0);
        assertNull(object0);
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test11() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false);
        Object object0 = new Object();
        cacheMap0.put((Object) null, object0);
        Object object1 = cacheMap0.put((Map) cacheMap0, (Object) null, (Object) null);
        assertNotNull(object1);
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test12() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        Object object0 = cacheMap0.put((Object) null, cacheMap0);
        assertNull(object0);
        CacheMap cacheMap1 = (CacheMap) cacheMap0.put((Object) null, cacheMap0);
        assertEquals(1000, cacheMap1.getCacheSize());
        assertNotNull(cacheMap1);
    }

    @Test
    @Timeout(4)
    public void test13() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        cacheMap0.isLRU();
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test14() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        Object object0 = cacheMap0.get("fq lUKLj");
        assertNull(object0);
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test15() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true);
        cacheMap0.put((Object) null, cacheMap0);
        CacheMap cacheMap1 = (CacheMap) cacheMap0.get((Object) null);
        assertEquals(1000, cacheMap1.getCacheSize());
        assertNotNull(cacheMap1);
    }

    @Test
    @Timeout(4)
    public void test16() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, (-9));
        boolean boolean0 = cacheMap0.containsKey("org.apache.openjpa.lib.util.collections.AbstractHashedMap$EntrySet");
        assertEquals((-1), cacheMap0.getCacheSize());
        assertFalse(boolean0);
    }

    @Test
    @Timeout(4)
    public void test17() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, (-9));
        SizedMap sizedMap0 = cacheMap0.cacheMap;
        cacheMap0.put((Map) sizedMap0, (Object) null, (Object) null);
        assertThrows(IllegalArgumentException.class, () -> cacheMap0.setCacheSize(0));
    }

    @Test
    @Timeout(4)
    public void test18() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, (-1));
        assertThrows(ArithmeticException.class, () -> cacheMap0.remove((Object) null));
    }

    @Test
    @Timeout(4)
    public void test19() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        assertThrows(NullPointerException.class, () -> cacheMap0.putAll((Map) null, false));
    }

    @Test
    @Timeout(4)
    public void test20() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        assertThrows(NullPointerException.class, () -> cacheMap0.putAll((Map) null));
    }

    @Test
    @Timeout(4)
    public void test21() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true);
        Object object0 = new Object();
        assertThrows(NullPointerException.class, () -> cacheMap0.put((Map) null, (Object) null, object0));
    }

    @Test
    @Timeout(4)
    public void test22() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        AbstractReferenceMap.ReferenceStrength strength = AbstractReferenceMap.ReferenceStrength.WEAK;
        ReferenceHashMap referenceHashMap0 = new ReferenceHashMap(strength, strength);
        assertThrows(NullPointerException.class,
                () -> cacheMap0.put((Map) referenceHashMap0, (Object) null, (Object) null));
    }

    @Test
    @Timeout(4)
    public void test23() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        SizedMap sizedMap0 = cacheMap0.softMap;
        LRUMap lRUMap0 = new LRUMap(720);
        assertThrows(IllegalArgumentException.class,
                () -> cacheMap0.put((Map) sizedMap0, (Object) lRUMap0, (Object) null));
    }

    @Test
    @Timeout(4)
    public void test24() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, (-1));
        assertThrows(ArithmeticException.class, () -> cacheMap0.put((Object) null, (Object) null));
    }

    @Test
    @Timeout(4)
    public void test25() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, (-1));
        assertThrows(ArithmeticException.class, () -> cacheMap0.pin((Object) null));
    }

    @Test
    @Timeout(4)
    public void test26() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, (-1));
        Object object0 = new Object();
        assertThrows(ArithmeticException.class, () -> cacheMap0.get(object0));
    }

    @Test
    @Timeout(4)
    public void test27() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, (-1));
        assertThrows(ArithmeticException.class,
                () -> cacheMap0.containsKey("Either keys or values must use hard references."));
    }

    @Test
    @Timeout(4)
    public void test28() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true);
        assertThrows(IllegalArgumentException.class,
                () -> cacheMap0.cacheMapOverflowRemoved((Object) null, (Object) null));
    }

    @Test
    @Timeout(4)
    public void test29() throws Throwable {
        assertThrows(IllegalArgumentException.class,
                () -> new CacheMap(false, (-1130), (-1130), (-1130), (-1130)));
    }

    @Test
    @Timeout(4)
    public void test30() throws Throwable {
        assertThrows(IllegalArgumentException.class,
                () -> new CacheMap(false, 0, 0, 0.0F));
    }

    @Test
    @Timeout(4)
    public void test31() throws Throwable {
        assertThrows(IllegalArgumentException.class,
                () -> new CacheMap(true, (-1)));
    }

    @Test
    @Timeout(4)
    public void test32() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true);
        cacheMap0.put((Object) null, "t%+F");
        boolean boolean0 = cacheMap0.containsKey((Object) null);
        assertTrue(boolean0);
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test33() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, 3);
        Object object0 = new Object();
        cacheMap0.cacheMapOverflowRemoved(cacheMap0, object0);
        BiFunction<Object, Object, Object> biFunction0 = (k, v) -> null;
        cacheMap0.computeIfPresent(cacheMap0, biFunction0);
        assertEquals(3, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test34() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true);
        assertThrows(IllegalMonitorStateException.class, cacheMap0::writeUnlock);
    }

    @Test
    @Timeout(4)
    public void test35() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        cacheMap0.writeLock();
        cacheMap0.writeUnlock();
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test36() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, (-1));
        assertThrows(ArithmeticException.class, () -> cacheMap0.remove((Map) cacheMap0, (Object) null));
    }

    @Test
    @Timeout(4)
    public void test37() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        cacheMap0.readLock();
        cacheMap0.readUnlock();
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test38() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, 1008);
        assertThrows(IllegalMonitorStateException.class, cacheMap0::readUnlock);
    }

    @Test
    @Timeout(4)
    public void test39() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        boolean boolean0 = cacheMap0.pin(cacheMap0);
        assertFalse(boolean0);
        boolean boolean1 = cacheMap0.containsValue((Object) null);
        assertEquals(1000, cacheMap0.getCacheSize());
        assertTrue(boolean1);
    }

    @Test
    @Timeout(4)
    public void test40() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        boolean boolean0 = cacheMap0.containsValue((Object) null);
        assertFalse(boolean0);
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test41() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, (-9));
        cacheMap0.cacheMapOverflowRemoved((Object) null, "org.apache.openjpa.lib.util.collections.AbstractHashedMap$EntrySet");
        boolean boolean0 = cacheMap0.containsKey((Object) null);
        assertTrue(boolean0);
        assertEquals((-1), cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test42() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, (-9));
        boolean boolean0 = cacheMap0.isEmpty();
        assertEquals((-1), cacheMap0.getCacheSize());
        assertTrue(boolean0);
    }

    @Test
    @Timeout(4)
    public void test43() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true);
        cacheMap0.putIfAbsent((Object) null, (Object) null);
        boolean boolean0 = cacheMap0.isEmpty();
        assertEquals(1000, cacheMap0.getCacheSize());
        assertFalse(boolean0);
    }

    @Test
    @Timeout(4)
    public void test44() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false);
        Object object0 = new Object();
        Object object1 = cacheMap0.put((Map) cacheMap0, (Object) cacheMap0, object0);
        assertNull(object1);
        cacheMap0.clear();
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test45() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false);
        Object object0 = cacheMap0.remove((Object) cacheMap0);
        assertNull(object0);
        cacheMap0.put((Map) cacheMap0, (Object) cacheMap0, object0);
        cacheMap0.clear();
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test46() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, 1008);
        CacheMap cacheMap1 = new CacheMap(true);
        cacheMap0.put(cacheMap1, (Object) null);
        cacheMap1.putAll((Map) cacheMap0, false);
        assertEquals(1000, cacheMap1.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test47() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true);
        LRUMap lRUMap0 = new LRUMap();
        cacheMap0.put((Map) lRUMap0, (Object) null, (Object) null);
        cacheMap0.putAll((Map) lRUMap0, true);
        boolean boolean0 = cacheMap0.containsValue((Object) null);
        assertTrue(boolean0);
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test48() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, 1008);
        cacheMap0.put(cacheMap0, (Object) null);
        cacheMap0.putAll((Map) cacheMap0, false);
        assertEquals(1008, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test49() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        boolean boolean0 = cacheMap0.unpin("N+!3(a5jIr");
        assertEquals(1000, cacheMap0.getCacheSize());
        assertFalse(boolean0);
    }

    @Test
    @Timeout(4)
    public void test50() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        cacheMap0.put((Object) null, "N+!3(a5jIr");
        boolean boolean0 = cacheMap0.pin((Object) null);
        assertTrue(boolean0);
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test51() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false);
        int int0 = cacheMap0.getSoftReferenceSize();
        assertEquals(1000, cacheMap0.getCacheSize());
        assertEquals((-1), int0);
    }

    @Test
    @Timeout(4)
    public void test52() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true);
        cacheMap0.setSoftReferenceSize(2115);
        int int0 = cacheMap0.getSoftReferenceSize();
        assertEquals(2115, int0);
    }

    @Test
    @Timeout(4)
    public void test53() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, (-1));
        cacheMap0.setSoftReferenceSize((-1));
        assertEquals((-1), cacheMap0.getCacheSize());
        assertEquals((-1), cacheMap0.getSoftReferenceSize());
    }

    @Test
    @Timeout(4)
    public void test54() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, (-9));
        int int0 = cacheMap0.getCacheSize();
        assertEquals((-1), int0);
    }

    @Test
    @Timeout(4)
    public void test55() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, 1008);
        int int0 = cacheMap0.getCacheSize();
        assertEquals(1008, int0);
    }

    @Test
    @Timeout(4)
    public void test56() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(false, (-1));
        cacheMap0.setCacheSize((-1));
        assertEquals((-1), cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test57() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, (-9));
        SizedMap sizedMap0 = cacheMap0.cacheMap;
        cacheMap0.put((Map) sizedMap0,
                (Object) "org.apache.openjpa.lib.util.collections.AbstractHashedMap$EntrySet",
                (Object) "CacheMap:org.apache.openjpa.util.CacheMap$3@0000000004::org.apache.openjpa.util.CacheMap$1@0000000001");
        assertEquals((-1), cacheMap0.getCacheSize());
        cacheMap0.setCacheSize(0);
        assertEquals(0, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test58() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, (-9));
        cacheMap0.toString();
        assertTrue(cacheMap0.isLRU());
        assertEquals((-1), cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test59() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        cacheMap0.getPinnedKeys();
        assertFalse(cacheMap0.isLRU());
        assertEquals(1000, cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test60() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, (-9));
        int int0 = cacheMap0.size();
        assertEquals(0, int0);
        assertTrue(cacheMap0.isLRU());
        assertEquals((-1), cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test61() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true);
        boolean boolean0 = cacheMap0.isLRU();
        assertEquals(1000, cacheMap0.getCacheSize());
        assertTrue(boolean0);
    }

    @Test
    @Timeout(4)
    public void test62() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, (-9));
        Set set0 = cacheMap0.keySet();
        assertTrue(cacheMap0.isLRU());
        assertEquals(0, set0.size());
        assertEquals((-1), cacheMap0.getCacheSize());
    }

    @Test
    @Timeout(4)
    public void test63() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        Object object0 = new Object();
        cacheMap0.softMapOverflowRemoved(object0, (Object) null);
        assertEquals(1000, cacheMap0.getCacheSize());
        assertFalse(cacheMap0.isLRU());
    }

    @Test
    @Timeout(4)
    public void test64() throws Throwable {
        CacheMap cacheMap0 = new CacheMap();
        LRUMap lRUMap0 = new LRUMap();
        cacheMap0.putAll((Map) lRUMap0);
        assertEquals(1000, cacheMap0.getCacheSize());
        assertFalse(cacheMap0.isLRU());
    }

    @Test
    @Timeout(4)
    public void test65() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true);
        LRUMap lRUMap0 = new LRUMap();
        cacheMap0.softMapValueExpired(lRUMap0);
        assertEquals(1000, cacheMap0.getCacheSize());
        assertTrue(cacheMap0.isLRU());
    }

    @Test
    @Timeout(4)
    public void test66() throws Throwable {
        CacheMap cacheMap0 = new CacheMap(true, (-9));
        cacheMap0.cacheMapOverflowRemoved((Object) null, "org.apache.openjpa.lib.util.collections.AbstractHashedMap$EntrySet");
        Object object0 = cacheMap0.remove((Object) null);
        assertEquals((-1), cacheMap0.getCacheSize());
        assertNotNull(object0);
        assertTrue(cacheMap0.isLRU());
    }
}
