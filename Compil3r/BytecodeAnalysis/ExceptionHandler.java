// ExceptionHandler.java, created Fri Jan 11 17:28:36 2002 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Compil3r.BytecodeAnalysis;

import Clazz.jq_Class;

/*
 * @author  John Whaley <jwhaley@alum.mit.edu>
 * @version $Id: ExceptionHandler.java,v 1.3 2003/05/12 10:05:13 joewhaley Exp $
 */
public class ExceptionHandler {

    final jq_Class exceptionType;
    final BasicBlock[] handledBlocks;
    final BasicBlock entry;
    
    ExceptionHandler(jq_Class exceptionType,
                        int numOfHandledBlocks,
                        BasicBlock entry) {
        this.exceptionType = exceptionType;
        this.handledBlocks = new BasicBlock[numOfHandledBlocks];
        this.entry = entry;
    }
    
    public jq_Class getExceptionType() { return exceptionType; }
    public BasicBlock getEntry() { return entry; }
    public BasicBlock[] getHandledBlocks() { return handledBlocks; }
}
