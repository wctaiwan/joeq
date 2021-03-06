// GCBitsManager.java, created Fri Sep 27  0:24:48 2002 by laudney
// Copyright (C) 2001-3 laudney <laudney@acm.org>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package GC;

import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;

import Allocator.HeapAllocator.HeapPointer;
import Memory.HeapAddress;

/**
 * GCBitsManager
 *
 * @author laudney <laudney@acm.org>
 * @version $Id: GCBitsManager.java,v 1.12 2003/05/12 10:05:17 joewhaley Exp $
 */
public class GCBitsManager {

    private static TreeMap pool = new TreeMap();
    private static HashSet units = new HashSet();

    public static void register(GCBits newcomer) {
        pool.put(new HeapPointer(newcomer.blockEnd), newcomer);
    }

    // In order for addr to be valid, totally 3 conditions must be met
    public static boolean isValidHeapAddr(HeapAddress addr) {
        // First Condition: addr is within a memory block
        HeapPointer key = (HeapPointer) pool.tailMap(new HeapPointer(addr)).firstKey();
        GCBits value = (GCBits) pool.get(key);
        int difference;
        if ((difference = addr.difference(value.blockHead)) < 0) {
            return false;
        }

        // Second Condition: addr must be 8-byte aligned

        // NOTES: the size of memory allocated for an object
        // is the sum of header size and object size. That
        // sum is involved in 8-byte alignment.
        // However, the passed addr here excludes the header
        // size. Currently, OBJECT_HEADER_SIZE = 8 bytes and
        // ARRAY_HEADER_SIZE = 12. Therefore, special attention
        //should be paid to arrays only.
        if (difference % 8 != 0 && difference % 8 != 4) {
            return false;
        }

        // Third Condition: addr must be set correspondingly in allocbits
        if (difference % 8 == 4) { // Maybe an array
            return value.isSet((HeapAddress) (addr.offset(-12)));
        } else { // Maybe an object
            return value.isSet((HeapAddress) (addr.offset(-8)));
        }
    }

    public static void mark(HeapAddress addr) {
        HeapPointer key = (HeapPointer) pool.tailMap(new HeapPointer(addr)).firstKey();
        GCBits value = (GCBits) pool.get(key);
        value.mark(addr);
    }

    public static void diff() {
        Iterator iter = pool.values().iterator();
        while (iter.hasNext()) {
            GCBits bits = (GCBits) iter.next();
            units.addAll(bits.diff());
        }
    }

    public static HashSet getUnits() {
        return units;
    }
}
