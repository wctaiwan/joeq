// jq_ThreadQueue.java, created Mon Apr  9  1:52:50 2001 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Scheduler;

import java.util.Iterator;

import Util.Assert;

/*
 * @author  John Whaley <jwhaley@alum.mit.edu>
 * @version $Id: jq_ThreadQueue.java,v 1.8 2003/05/12 10:05:20 joewhaley Exp $
 */
public class jq_ThreadQueue {

    private jq_Thread head, tail;
    
    public boolean isEmpty() {
        return head == null;
    }
    
    public void enqueue(jq_Thread t) {
        Assert._assert(t.next == null);
        if (head == null) head = t;
        else tail.next = t;
        tail = t;
    }
    
    public void enqueueFront(jq_Thread t) {
        Assert._assert(t.next == null);
        if (head == null) tail = t;
        else head.next = t;
        head = t;
    }
    
    public jq_Thread dequeue() {
        jq_Thread t = head;
        if (t == null) return null;
        head = t.next;
        t.next = null;
        if (head == null) tail = null;
        return t;
    }
    
    public boolean remove(jq_Thread t2) {
        jq_Thread p = head, q = null;
        while (p != t2) {
            if (p == null) return false;
            q = p;
            p = p.next;
        }
        if (q == null) {
            Assert._assert(head == t2);
            head = t2.next;
            if (head == null) tail = null;
            else t2.next = null;
        } else {
            q.next = p.next;
            if (p.next == null) {
                Assert._assert(p == tail);
                tail = q;
            } else {
                p.next = null;
            }
        }
        return true;
    }

    public Iterator threads() {
        final jq_Thread start = head;
        return new Iterator() {
            jq_Thread t = start;
            public boolean hasNext() {
                return t != null;
            }
            public Object next() {
                jq_Thread t2 = t;
                t = t.next;
                return t2;
            }
            public void remove() { throw new UnsupportedOperationException(); }
        };
    }
    
    public String toString() {
        StringBuffer s = new StringBuffer("{ ");
        Iterator i = threads();
        if (i.hasNext()) {
            s.append(i.next().toString());
            while (i.hasNext()) {
                s.append(", ");
                s.append(i.next().toString());
            }
        }
        s.append(" }");
        return s.toString();
    }
    
}
