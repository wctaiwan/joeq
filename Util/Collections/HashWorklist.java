// HashWorklist.java, created Fri Mar 28 23:58:36 2003 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Util.Collections;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import Util.Assert;

/**
 * A worklist with a backing hash set to determine if an item is/was already on the list.
 * 
 * Careful: Although this implements java.util.Set, it does not obey the hashCode() and
 * equals() contract for Set.  It obeys the contract for java.util.List instead.
 * 
 * @author John Whaley <jwhaley@alum.mit.edu>
 * @version $Id: HashWorklist.java,v 1.4 2003/07/07 10:29:21 joewhaley Exp $
 */
public class HashWorklist extends AbstractList implements Worklist, Set {

    private final Set set;
    private final List list;
    private final boolean once;

    public HashWorklist(boolean once, SetFactory sf, ListFactory lf) {
        set = sf.makeSet();
        list = lf.makeList();
        this.once = once;
    }
    public HashWorklist(boolean once, ListFactory lf) {
        this(once, Factories.hashSetFactory(), lf);
    }
    public HashWorklist(boolean once, SetFactory sf) {
        this(once, sf, Factories.linkedListFactory());
    }
    public HashWorklist(boolean once) {
        this(once, Factories.hashSetFactory());
    }
    public HashWorklist() {
        this(false);
    }

    /* (non-Javadoc)
     * @see Util.Collections.Worklist#push(java.lang.Object)
     */
    public void push(Object item) {
        add(item);
    }

    /* (non-Javadoc)
     * @see Util.Collections.Worklist#pull()
     */
    public Object pull() {
        Object o = list.remove(0);
        if (!once) set.remove(o);
        return o;
    }

    /* (non-Javadoc)
     * @see java.util.AbstractList#get(int)
     */
    public Object get(int index) {
        return list.get(index);
    }

    /* (non-Javadoc)
     * @see java.util.AbstractCollection#size()
     */
    public int size() {
        return list.size();
    }

    /* (non-Javadoc)
     * @see java.util.Collection#add(java.lang.Object)
     */
    public boolean add(Object item) {
        if (set.add(item))
            return list.add(item);
        else
            return false;
    }

    /* (non-Javadoc)
     * @see java.util.Collection#contains(java.lang.Object)
     */
    public boolean contains(Object o) {
        return set.contains(o);
    }

    public Set getVisitedSet() {
        Assert._assert(once);
        if (false) {
            return Collections.unmodifiableSet(set);
        } else {
            return set;
        }
    }

}
