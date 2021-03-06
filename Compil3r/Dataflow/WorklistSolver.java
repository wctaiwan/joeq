// WorklistSolver.java, created Thu Apr 25 16:32:26 2002 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Compil3r.Dataflow;

import java.util.Collection;
import java.util.Iterator;

import Util.Collections.MapFactory;
import Util.Graphs.Graph;
import Util.Graphs.Navigator;

/**
 * WorklistSolver
 * 
 * @author John Whaley
 * @version $Id: WorklistSolver.java,v 1.1 2003/06/17 02:37:51 joewhaley Exp $
 */
public abstract class WorklistSolver extends Solver {

    /** Navigator to navigate the graph of locations. */
    protected Navigator graphNavigator;
    /** The boundary locations. */
    protected Collection boundaries;

    protected WorklistSolver(MapFactory f) {
        super(f);
    }
    protected WorklistSolver() {
        super();
    }

    /** Get the predecessor locations of the given location. */
    protected Collection getPredecessors(Object c) { return graphNavigator.prev(c); }
    /** Get the successor locations of the given location. */
    protected Collection getSuccessors(Object c) { return graphNavigator.next(c); }
    
    /** (Re-)initialize the worklist. */
    protected abstract void initializeWorklist();
    /** Returns true if the worklist is not empty, false otherwise. */
    protected abstract boolean hasNext();
    /** Pull the next location off of the worklist. */
    protected abstract Object pull();
    /** Push all of the given locations onto the worklist. */
    protected abstract void pushAll(Collection c);

    /* (non-Javadoc)
     * @see Compil3r.Dataflow.Solver#boundaryLocations()
     */
    public Iterator boundaryLocations() {
        return boundaries.iterator();
    }
    
    /* (non-Javadoc)
     * @see Compil3r.Dataflow.Solver#initialize(Compil3r.Dataflow.Problem, Util.Graphs.Graph)
     */
    public void initialize(Problem p, Graph graph) {
        super.initialize(p, graph);
        graphNavigator = graph.getNavigator();
        boundaries = graph.getRoots();
    }

    /* (non-Javadoc)
     * @see Compil3r.Dataflow.Solver#solve()
     */
    public void solve() {
        initializeDataflowValueMap();
        initializeWorklist();
        while (hasNext()) {
            Object c = pull();
            Iterator j = direction()?
                         getPredecessors(c).iterator():
                         getSuccessors(c).iterator();
            Object p = j.next();
            Fact in = (Fact) dataflowValues.get(p);
            while (j.hasNext()) {
                p = j.next();
                Fact in2 = (Fact) dataflowValues.get(p);
                in = problem.merge(in, in2);
            }
            TransferFunction tf = problem.getTransferFunction(c);
            Fact out = problem.apply(tf, in);
            Fact old = (Fact) dataflowValues.put(c, out);
            if (!problem.compare(old, out)) {
                Collection next = direction()?
                                  getSuccessors(c):
                                  getPredecessors(c);
                pushAll(next);
            }
        }
    }

}
