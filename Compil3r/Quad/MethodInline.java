// MethodInline.java, created Wed Mar 13  1:39:18 2002 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Compil3r.Quad;

import java.util.Collection;

import Clazz.jq_Class;
import Clazz.jq_Method;
import Clazz.jq_Primitive;
import Clazz.jq_Type;
import Compil3r.BytecodeAnalysis.BytecodeVisitor;
import Compil3r.Quad.Operand.ConditionOperand;
import Compil3r.Quad.Operand.IConstOperand;
import Compil3r.Quad.Operand.ParamListOperand;
import Compil3r.Quad.Operand.RegisterOperand;
import Compil3r.Quad.Operand.TargetOperand;
import Compil3r.Quad.Operand.TypeOperand;
import Compil3r.Quad.Operator.Goto;
import Compil3r.Quad.Operator.InstanceOf;
import Compil3r.Quad.Operator.IntIfCmp;
import Compil3r.Quad.Operator.Invoke;
import Compil3r.Quad.Operator.Move;
import Compil3r.Quad.Operator.Return;
import Compil3r.Quad.ProgramLocation.QuadProgramLocation;
import Compil3r.Quad.RegisterFactory.Register;
import Util.Assert;
import Util.Templates.ListIterator;

/**
 * @author John Whaley <jwhaley@alum.mit.edu>
 * @version $Id: MethodInline.java,v 1.12 2003/07/08 13:24:05 joewhaley Exp $
 */
public class MethodInline implements ControlFlowGraphVisitor {

    public static final boolean TRACE = false;
    public static final boolean TRACE_ORACLE = false;
    public static final boolean TRACE_DECISIONS = false;
    public static final java.io.PrintStream out = System.out;

    Oracle oracle;
    CallGraph cg;

    public MethodInline(Oracle o) {
        this.oracle = o;
    }
    public MethodInline(CallGraph cg) {
        this(new InlineSmallSingleTargetCalls(cg));
        this.cg = cg;
    }
    public MethodInline() {
        this(new InlineSmallSingleTargetCalls(CHACallGraph.INSTANCE));
        this.cg = CHACallGraph.INSTANCE;
    }

    public static interface Oracle {
        InliningDecision shouldInline(ControlFlowGraph caller, BasicBlock bb, Quad callSite);
    }
    
    public static abstract class InliningDecision {
        public abstract void inlineCall(ControlFlowGraph caller, BasicBlock bb, Quad q);
    }
    
    public static final class DontInline extends InliningDecision {
        private DontInline() {}
        public void inlineCall(ControlFlowGraph caller, BasicBlock bb, Quad q) {
        }
        public static final DontInline INSTANCE = new DontInline();
    }
    
    public static class NoCheckInliningDecision extends InliningDecision {
        ControlFlowGraph callee;
        public NoCheckInliningDecision(jq_Method target) {
            this(CodeCache.getCode(target));
        }
        public NoCheckInliningDecision(ControlFlowGraph target) {
            this.callee = target;
        }
        public void inlineCall(ControlFlowGraph caller, BasicBlock bb, Quad q) {
            inlineNonVirtualCallSite(caller, bb, q, callee);
        }
        public String toString() { return callee.getMethod().toString(); }
    }
    
    public static class TypeCheckInliningDecision extends InliningDecision {
        jq_Class expectedType;
        ControlFlowGraph callee;
        public TypeCheckInliningDecision(jq_Method target) {
            this.expectedType = target.getDeclaringClass();
            this.callee = CodeCache.getCode(target);
        }
        public void inlineCall(ControlFlowGraph caller, BasicBlock bb, Quad q) {
            inlineVirtualCallSiteWithTypeCheck(caller, bb, q, callee, expectedType);
        }
        public String toString() { return callee.getMethod().toString(); }
    }
    
    public static class InlineSmallSingleTargetCalls implements Oracle {
        
        protected CallGraph cg;
        int bcThreshold;
        public static final int DEFAULT_THRESHOLD = 25;
        
        public InlineSmallSingleTargetCalls(CallGraph cg) {
            this(cg, DEFAULT_THRESHOLD);
        }
        public InlineSmallSingleTargetCalls(CallGraph cg, int bcThreshold) {
            this.cg = cg;
            this.bcThreshold = bcThreshold;
        }
    
        public InliningDecision shouldInline(ControlFlowGraph caller, BasicBlock bb, Quad callSite) {
            if (TRACE_ORACLE) out.println("Oracle evaluating "+callSite);
            if (Invoke.getMethod(callSite).getMethod().needsDynamicLink(caller.getMethod())) {
                if (TRACE_ORACLE) out.println("Skipping because call site needs dynamic link.");
                return null;
            }
            Invoke i = (Invoke) callSite.getOperator();
            jq_Method target;
            if (i.isVirtual()) {
                ProgramLocation pl = new QuadProgramLocation(caller.getMethod(), callSite);
                Collection c = cg.getTargetMethods(pl);
                if (c.size() != 1) {
                    if (TRACE_ORACLE) out.println("Skipping because call site has multiple targets.");
                    return null;
                }
                target = (jq_Method) c.iterator().next();
            } else {
                target = Invoke.getMethod(callSite).getMethod();
            }
            if (target.getBytecode() == null) {
                if (TRACE_ORACLE) out.println("Skipping because target method is native.");
                return null;
            }
            if (target.getBytecode().length > bcThreshold) {
                if (TRACE_ORACLE) out.println("Skipping because target method is too large ("+target.getBytecode().length+").");
                return null;
            }
            if (target == caller.getMethod()) {
                if (TRACE_ORACLE) out.println("Skipping because method is recursive.");
                return null;
            }
            // HACK: for interpreter.
            if (!Interpreter.QuadInterpreter.interpret_filter.isElement(target)) {
                if (TRACE_ORACLE) out.println("Skipping because the interpreter cannot handle the target method.");
                return null;
            }
            
            if (TRACE_ORACLE) out.println("Oracle says to inline!");
            return new NoCheckInliningDecision(target);
        }

    }
    
    public void visitCFG(ControlFlowGraph cfg) {
        QuadIterator qi = new QuadIterator(cfg);
        java.util.LinkedList inline_blocks = new java.util.LinkedList();
        java.util.LinkedList inline_quads = new java.util.LinkedList();
        java.util.LinkedList inline_decisions = new java.util.LinkedList();
        while (qi.hasNext()) {
            Quad q = qi.nextQuad();
            if (q.getOperator() instanceof Invoke) {
                BasicBlock bb = qi.getCurrentBasicBlock();
                InliningDecision d = oracle.shouldInline(cfg, bb, q);
                if (d != null) {
                    if (TRACE_DECISIONS) out.println("Decided to inline "+d);
                    inline_blocks.add(bb);
                    inline_quads.add(q);
                    inline_decisions.add(d);
                }
            }
        }
        // do the inlining backwards, so that basic blocks don't change.
        java.util.ListIterator li1 = inline_blocks.listIterator();
        java.util.ListIterator li2 = inline_quads.listIterator();
        java.util.ListIterator li3 = inline_decisions.listIterator();
        while (li1.hasNext()) { li1.next(); li2.next(); li3.next(); }
        while (li1.hasPrevious()) {
            BasicBlock bb = (BasicBlock)li1.previous();
            Quad q = (Quad)li2.previous();
            InliningDecision d = (InliningDecision)li3.previous();
            if (TRACE_DECISIONS) out.println("Performing inlining "+d);
            d.inlineCall(cfg, bb, q);
            if (cg instanceof CachedCallGraph && d instanceof NoCheckInliningDecision) {
                CachedCallGraph ccg = (CachedCallGraph) cg;
                jq_Method caller = cfg.getMethod();
                ProgramLocation pl = new ProgramLocation.QuadProgramLocation(caller, q);
                jq_Method callee = ((NoCheckInliningDecision) d).callee.getMethod();
                ccg.inlineEdge(caller, pl, callee);
            }
        }
    }
    
    public static void inlineNonVirtualCallSite(ControlFlowGraph caller, BasicBlock bb, Quad q, ControlFlowGraph callee) {
        if (TRACE) out.println("Inlining "+q+" target "+callee.getMethod()+" in "+bb);

        int invokeLocation = bb.getQuadIndex(q);
        Assert._assert(invokeLocation != -1);

        if (TRACE) out.println("Code to inline:");
        if (TRACE) out.println(callee.fullDump());
        if (TRACE) out.println(callee.getRegisterFactory().fullDump());

        // copy the callee's control flow graph, renumbering the basic blocks,
        // registers and quads, and add the exception handlers.
        callee = caller.merge(callee);
        if (TRACE) out.println("After renumbering:");
        if (TRACE) out.println(callee.fullDump());
        if (TRACE) out.println(callee.getRegisterFactory().fullDump());
        callee.appendExceptionHandlers(bb.getExceptionHandlers());

        if (TRACE) out.println("Original basic block containing invoke:");
        if (TRACE) out.println(bb.fullDump());

        // split the basic block containing the invoke, and start splicing
        // in the callee's control flow graph.
        BasicBlock successor_bb = caller.createBasicBlock(callee.exit().getNumberOfPredecessors(), bb.getNumberOfSuccessors(), bb.size() - invokeLocation, bb.getExceptionHandlers());
        int bb_size = bb.size();
        for (int i=invokeLocation+1; i<bb_size; ++i) {
            successor_bb.appendQuad(bb.removeQuad(invokeLocation+1));
        }
        Quad invokeQuad = bb.removeQuad(invokeLocation);
        Assert._assert(invokeQuad == q);
        Assert._assert(bb.size() == invokeLocation);
        if (TRACE) out.println("Result of splitting:");
        if (TRACE) out.println(bb.fullDump());
        if (TRACE) out.println(successor_bb.fullDump());

        // add instructions to set parameters.
        ParamListOperand plo = Invoke.getParamList(q);
        jq_Type[] params = callee.getMethod().getParamTypes();
        for (int i=0, j=0; i<params.length; ++i, ++j) {
            Move op = Move.getMoveOp(params[i]);
            Register dest_r = callee.getRegisterFactory().getLocal(j, params[i]);
            RegisterOperand dest = new RegisterOperand(dest_r, params[i]);
            Register src_r = plo.get(i).getRegister();
            RegisterOperand src = new RegisterOperand(src_r, params[i]);
            Quad q2 = Move.create(caller.getNewQuadID(), op, dest, src);
            bb.appendQuad(q2);
            if (params[i].getReferenceSize() == 8) ++j;
        }
        if (TRACE) out.println("Result after adding parameter moves:");
        if (TRACE) out.println(bb.fullDump());

        // replace return instructions with moves and gotos, and
        // finish splicing in the control flow graph.
        for (ListIterator.BasicBlock it = bb.getSuccessors().basicBlockIterator();
             it.hasNext(); ) {
            BasicBlock next_bb = it.nextBasicBlock();
            next_bb.removePredecessor(bb);
            next_bb.addPredecessor(successor_bb);
            successor_bb.addSuccessor(next_bb);
        }
        bb.removeAllSuccessors();
        bb.addSuccessor(callee.entry().getFallthroughSuccessor());
        callee.entry().getFallthroughSuccessor().removeAllPredecessors();
        callee.entry().getFallthroughSuccessor().addPredecessor(bb);
        RegisterOperand dest = Invoke.getDest(q);
        Register dest_r = null;
        Move op = null;
        if (dest != null) {
            dest_r = dest.getRegister();
            op = Move.getMoveOp(callee.getMethod().getReturnType());
        }
outer:
        for (ListIterator.BasicBlock it = callee.exit().getPredecessors().basicBlockIterator();
             it.hasNext(); ) {
            BasicBlock pred_bb = it.nextBasicBlock();
            while (pred_bb.size() == 0) { 
                if (TRACE) System.out.println("Predecessor of exit has no quads? "+pred_bb);
                if (pred_bb.getNumberOfPredecessors() == 0) continue outer;
                pred_bb = pred_bb.getFallthroughPredecessor();
            }
            Quad lq = pred_bb.getLastQuad();
            if (lq.getOperator() instanceof Return.THROW_A) {
                pred_bb.removeAllSuccessors(); pred_bb.addSuccessor(caller.exit());
                caller.exit().addPredecessor(pred_bb);
                continue;
            }
            Assert._assert(lq.getOperator() instanceof Return);
            pred_bb.removeQuad(lq);
            if (dest_r != null) {
                RegisterOperand ldest = new RegisterOperand(dest_r, callee.getMethod().getReturnType());
                Operand src = Return.getSrc(lq).copy();
                Quad q3 = Move.create(caller.getNewQuadID(), op, ldest, src);
                pred_bb.appendQuad(q3);
            }
            Quad q2 = Goto.create(caller.getNewQuadID(),
                                  Goto.GOTO.INSTANCE,
                                  new TargetOperand(successor_bb));
            pred_bb.appendQuad(q2);
            pred_bb.removeAllSuccessors(); pred_bb.addSuccessor(successor_bb);
            successor_bb.addPredecessor(pred_bb);
        }
        
        if (TRACE) out.println("Final result:");
        if (TRACE) out.println(caller.fullDump());
        if (TRACE) out.println(caller.getRegisterFactory().fullDump());

        if (TRACE) out.println("Original code:");
        if (TRACE) out.println(CodeCache.getCode(callee.getMethod()).fullDump());
        if (TRACE) out.println(CodeCache.getCode(callee.getMethod()).getRegisterFactory().fullDump());
    }

    public static void inlineVirtualCallSiteWithTypeCheck(ControlFlowGraph caller, BasicBlock bb, Quad q, ControlFlowGraph callee, jq_Class type) {
        if (TRACE) out.println("Inlining "+q+" in "+bb+" for target "+callee.getMethod());

        int invokeLocation = bb.getQuadIndex(q);
        Assert._assert(invokeLocation != -1);

        if (TRACE) out.println("Code to inline:");
        if (TRACE) out.println(callee.fullDump());
        if (TRACE) out.println(callee.getRegisterFactory().fullDump());

        // copy the callee's control flow graph, renumbering the basic blocks,
        // registers and quads, and add the exception handlers.
        callee = caller.merge(callee);
        if (TRACE) out.println("After renumbering:");
        if (TRACE) out.println(callee.fullDump());
        if (TRACE) out.println(callee.getRegisterFactory().fullDump());
        callee.appendExceptionHandlers(bb.getExceptionHandlers());

        if (TRACE) out.println("Original basic block containing invoke:");
        if (TRACE) out.println(bb.fullDump());

        // split the basic block containing the invoke, and start splicing
        // in the callee's control flow graph.
        BasicBlock successor_bb = caller.createBasicBlock(callee.exit().getNumberOfPredecessors() + 1, bb.getNumberOfSuccessors(), bb.size() - invokeLocation, bb.getExceptionHandlers());
        int bb_size = bb.size();
        for (int i=invokeLocation+1; i<bb_size; ++i) {
            successor_bb.appendQuad(bb.removeQuad(invokeLocation+1));
        }
        Quad invokeQuad = bb.removeQuad(invokeLocation);
        Assert._assert(invokeQuad == q);
        Assert._assert(bb.size() == invokeLocation);
        if (TRACE) out.println("Result of splitting:");
        if (TRACE) out.println(bb.fullDump());
        if (TRACE) out.println(successor_bb.fullDump());

        // create failsafe case block
        BasicBlock bb_fail = caller.createBasicBlock(1, 1, 2, bb.getExceptionHandlers());
        bb_fail.appendQuad(invokeQuad);
        Quad q2 = Goto.create(caller.getNewQuadID(),
                              Goto.GOTO.INSTANCE,
                              new TargetOperand(successor_bb));
        bb_fail.appendQuad(q2);
        bb_fail.addSuccessor(successor_bb);
        successor_bb.addPredecessor(bb_fail);
        if (TRACE) out.println("Fail-safe block:");
        if (TRACE) out.println(bb_fail.fullDump());
        
        // create success case block
        BasicBlock bb_success = caller.createBasicBlock(1, 1, Invoke.getParamList(q).length(), bb.getExceptionHandlers());
        
        // add test.
        jq_Type dis_t = Invoke.getParam(q, 0).getType();
        RegisterOperand dis_op = new RegisterOperand(Invoke.getParam(q, 0).getRegister(), dis_t);
        Register res = caller.getRegisterFactory().getNewStack(0, jq_Primitive.BOOLEAN);
        RegisterOperand res_op = new RegisterOperand(res, jq_Primitive.BOOLEAN);
        TypeOperand type_op = new TypeOperand(type);
        q2 = InstanceOf.create(caller.getNewQuadID(), InstanceOf.INSTANCEOF.INSTANCE, res_op, dis_op, type_op);
        bb.appendQuad(q2);
        res_op = new RegisterOperand(res, jq_Primitive.BOOLEAN);
        IConstOperand zero_op = new IConstOperand(0);
        ConditionOperand ne_op = new ConditionOperand(BytecodeVisitor.CMP_NE);
        TargetOperand success_op = new TargetOperand(bb_success);
        q2 = IntIfCmp.create(caller.getNewQuadID(), IntIfCmp.IFCMP_I.INSTANCE, res_op, zero_op, ne_op, success_op);
        bb.appendQuad(q2);
        
        // add instructions to set parameters.
        ParamListOperand plo = Invoke.getParamList(q);
        jq_Type[] params = callee.getMethod().getParamTypes();
        for (int i=0, j=0; i<params.length; ++i, ++j) {
            Move op = Move.getMoveOp(params[i]);
            Register dest_r = callee.getRegisterFactory().getLocal(j, params[i]);
            RegisterOperand dest = new RegisterOperand(dest_r, params[i]);
            Register src_r = plo.get(i).getRegister();
            RegisterOperand src = new RegisterOperand(src_r, params[i]);
            q2 = Move.create(caller.getNewQuadID(), op, dest, src);
            bb_success.appendQuad(q2);
            if (params[i].getReferenceSize() == 8) ++j;
        }
        if (TRACE) out.println("Success block after adding parameter moves:");
        if (TRACE) out.println(bb_success.fullDump());

        // replace return instructions with moves and gotos, and
        // finish splicing in the control flow graph.
        for (ListIterator.BasicBlock it = bb.getSuccessors().basicBlockIterator();
             it.hasNext(); ) {
            BasicBlock next_bb = it.nextBasicBlock();
            next_bb.removePredecessor(bb);
            next_bb.addPredecessor(successor_bb);
            successor_bb.addSuccessor(next_bb);
        }
        bb_success.addSuccessor(callee.entry().getFallthroughSuccessor());
        callee.entry().getFallthroughSuccessor().removeAllPredecessors();
        callee.entry().getFallthroughSuccessor().addPredecessor(bb_success);
        bb.removeAllSuccessors();
        bb.addSuccessor(bb_fail);
        bb_fail.addPredecessor(bb);
        bb.addSuccessor(bb_success);
        bb_success.addPredecessor(bb);
        RegisterOperand dest = Invoke.getDest(q);
        Register dest_r = null;
        Move op = null;
        if (dest != null) {
            dest_r = dest.getRegister();
            op = Move.getMoveOp(callee.getMethod().getReturnType());
        }
outer:
        for (ListIterator.BasicBlock it = callee.exit().getPredecessors().basicBlockIterator();
             it.hasNext(); ) {
            BasicBlock pred_bb = it.nextBasicBlock();
            while (pred_bb.size() == 0) { 
                if (TRACE) System.out.println("Predecessor of exit has no quads? "+pred_bb);
                if (pred_bb.getNumberOfPredecessors() == 0) continue outer;
                pred_bb = pred_bb.getFallthroughPredecessor();
            }
            Quad lq = pred_bb.getLastQuad();
            if (lq.getOperator() instanceof Return.THROW_A) {
                pred_bb.removeAllSuccessors(); pred_bb.addSuccessor(caller.exit());
                caller.exit().addPredecessor(pred_bb);
                continue;
            }
            Assert._assert(lq.getOperator() instanceof Return);
            pred_bb.removeQuad(lq);
            if (dest_r != null) {
                RegisterOperand ldest = new RegisterOperand(dest_r, callee.getMethod().getReturnType());
                Operand src = Return.getSrc(lq).copy();
                Quad q3 = Move.create(caller.getNewQuadID(), op, ldest, src);
                pred_bb.appendQuad(q3);
            }
            q2 = Goto.create(caller.getNewQuadID(),
                             Goto.GOTO.INSTANCE,
                             new TargetOperand(successor_bb));
            pred_bb.appendQuad(q2);
            pred_bb.removeAllSuccessors(); pred_bb.addSuccessor(successor_bb);
            successor_bb.addPredecessor(pred_bb);
        }
        
        if (TRACE) out.println("Final result:");
        if (TRACE) out.println(caller.fullDump());
        if (TRACE) out.println(caller.getRegisterFactory().fullDump());

        if (TRACE) out.println("Original code:");
        if (TRACE) out.println(CodeCache.getCode(callee.getMethod()).fullDump());
        if (TRACE) out.println(CodeCache.getCode(callee.getMethod()).getRegisterFactory().fullDump());
    }
}
