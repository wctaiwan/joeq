// jq_TryCatchBC.java, created Mon Feb  5 23:23:20 2001 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Clazz;

import Run_Time.TypeCheck;

/*
 * @author  John Whaley <jwhaley@alum.mit.edu>
 * @version $Id: jq_TryCatchBC.java,v 1.7 2003/05/12 10:05:13 joewhaley Exp $
 */
public class jq_TryCatchBC {

    // NOTE: startPC is inclusive, endPC is exclusive
    private char startPC, endPC, handlerPC;
    private jq_Class exType;

    public jq_TryCatchBC(char startPC, char endPC, char handlerPC, jq_Class exType) {
        this.startPC = startPC;
        this.endPC = endPC;
        this.handlerPC = handlerPC;
        this.exType = exType;
    }

    public boolean catches(int pc, jq_Class t) {
        t.prepare();
        if (pc < startPC) return false;
        if (pc >= endPC) return false;
        if (exType != null && !TypeCheck.isAssignable(t, exType)) return false;
        return true;
    }
    
    public char getStartPC() { return startPC; }
    public char getEndPC() { return endPC; }
    public char getHandlerPC() { return handlerPC; }
    public jq_Class getExceptionType() { return exType; }
    
    public String toString() {
        return "(start="+(int)startPC+",end="+(int)endPC+",handler="+(int)handlerPC+",type="+exType+")";
    }
}
