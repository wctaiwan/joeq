// GCVisitor.java, created Wed Sep 25  7:09:24 2002 by laudney
// Copyright (C) 2001-3 laudney <laudney@acm.org>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package GC;

import Scheduler.jq_NativeThread;
import Scheduler.jq_RegisterState;

/**
 * GCVisitor
 *
 * @author laudney <laudney@acm.org>
 * @version $Id: GCVisitor.java,v 1.7 2003/05/12 10:05:17 joewhaley Exp $
 */
public interface GCVisitor {
    public void visit(jq_RegisterState state);
    public void farewell(jq_NativeThread[] nt);
}
