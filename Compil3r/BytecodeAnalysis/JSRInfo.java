// JSRInfo.java, created Thu Jan 31 23:05:20 2002 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Compil3r.BytecodeAnalysis;

/**
 *
 * @author  John Whaley <jwhaley@alum.mit.edu>
 * @version $Id: JSRInfo.java,v 1.3 2003/05/12 10:05:14 joewhaley Exp $
 */
public class JSRInfo {

    public BasicBlock entry_block;
    public BasicBlock exit_block;
    public boolean[] changedLocals;
    
    public JSRInfo(BasicBlock entry, BasicBlock exit, boolean[] changed) {
        this.entry_block = entry;
        this.exit_block = exit;
        this.changedLocals = changed;
    }
    
}
