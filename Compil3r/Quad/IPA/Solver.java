// Solver.java, created Jun 14, 2003 10:20:27 PM by joewhaley
// Copyright (C) 2003 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Compil3r.Quad.IPA;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import Clazz.jq_Method;
import Compil3r.Quad.CallGraph;
import Util.Assert;
import Util.Graphs.Navigator;
import Util.Graphs.SCCTopSortedGraph;
import Util.Graphs.SCComponent;
import Util.Graphs.Traversals;

/**
 * Solver
 * 
 * @author John Whaley
 * @version $Id: Solver.java,v 1.2 2003/07/14 19:38:17 joewhaley Exp $
 */
public abstract class Solver {
    
    CallGraph cg;
    Map predecessors;
    Collection roots;
    
    public static final boolean TIMINGS = false;
    public static final boolean TRACE = false;
    public static final boolean TRACE_WORKLIST = false;
    
    public abstract boolean visit(jq_Method m, boolean loop);
    
    public abstract void dispose(jq_Method m);
    
    void go() {
        
        long time = System.currentTimeMillis();
        
        /* Get the predecessor map and build SCCs. */
        if (TRACE) System.out.print("Building and sorting SCCs...");
        Navigator navigator = cg.getNavigator();
        predecessors = Traversals.buildPredecessorMap(navigator, roots);
        Set sccs = SCComponent.buildSCC(roots, navigator);
        SCCTopSortedGraph graph = SCCTopSortedGraph.topSort(sccs);
        if (TRACE) System.out.print("done.");
        
        if (TIMINGS) System.out.println("Initial setup:\t\t"+(System.currentTimeMillis()-time)/1000.+" seconds.");
        
        /* Walk through SCCs in reverse order. */
        SCComponent scc = graph.getLast();
        while (scc != null) {
            /* Visit each method in the SCC. */
            if (TRACE_WORKLIST) System.out.println("Visiting SCC"+scc.getId()+(scc.isLoop()?" (loop)":" (non-loop)"));
            Object[] nodes = scc.nodes();
            boolean change = false;
            for (int i=0; i<nodes.length; ++i) {
                jq_Method m = (jq_Method) nodes[i];
                if (visit(m, scc.isLoop())) {
                    if (TRACE_WORKLIST && scc.isLoop()) System.out.println(m+" changed.");
                    change = true;
                }
            }
            if (scc.isLoop() && change) {
                if (TRACE_WORKLIST) System.out.println("Loop changed, redoing SCC.");
                continue;
            }
            /* Finished SCC, remove edges from nodes in this SCC. */
            if (TRACE_WORKLIST) System.out.println("Finished SCC"+scc.getId());
            for (int i=0; i<nodes.length; ++i) {
                jq_Method m1 = (jq_Method) nodes[i];
                for (Iterator j=navigator.next(m1).iterator(); j.hasNext(); ) {
                    jq_Method m2 = (jq_Method) j.next();
                    Set ps = (Set) predecessors.get(m2);
                    boolean b = ps.remove(m1);
                    Assert._assert(b);
                    if (ps.isEmpty()) {
                        if (TRACE_WORKLIST) System.out.println(m2+" has no more predecessors, disposing.");
                        dispose(m2);
                    } else {
                        if (TRACE_WORKLIST) System.out.println(m2+" still has "+ps.size()+" predecessors.");
                    }
                }
            }
            scc = scc.prevTopSort();
        }
    }
}
