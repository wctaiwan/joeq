// AndersenPointerAnalysis.java, created Thu Apr 25 16:32:26 2002 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Compil3r.Quad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import Clazz.jq_Class;
import Clazz.jq_Field;
import Clazz.jq_Initializer;
import Clazz.jq_Method;
import Clazz.jq_Reference;
import Clazz.jq_StaticField;
import Clazz.jq_Type;
import Compil3r.BytecodeAnalysis.CallTargets;
import Compil3r.Quad.AndersenInterface.AndersenClass;
import Compil3r.Quad.AndersenInterface.AndersenClassInitializer;
import Compil3r.Quad.AndersenInterface.AndersenField;
import Compil3r.Quad.AndersenInterface.AndersenMethod;
import Compil3r.Quad.AndersenInterface.AndersenReference;
import Compil3r.Quad.AndersenInterface.AndersenType;
import Compil3r.Quad.MethodSummary.CallSite;
import Compil3r.Quad.MethodSummary.ConcreteTypeNode;
import Compil3r.Quad.MethodSummary.FieldNode;
import Compil3r.Quad.MethodSummary.GlobalNode;
import Compil3r.Quad.MethodSummary.Node;
import Compil3r.Quad.MethodSummary.NodeSet;
import Compil3r.Quad.MethodSummary.OutsideNode;
import Compil3r.Quad.MethodSummary.ParamNode;
import Compil3r.Quad.MethodSummary.PassedParameter;
import Compil3r.Quad.MethodSummary.ReturnValueNode;
import Compil3r.Quad.MethodSummary.ThrownExceptionNode;
import Compil3r.Quad.MethodSummary.UnknownTypeNode;
import Compil3r.Quad.SSAReader.SSAMethod;
import Compil3r.Quad.SelectiveCloning.AccessPath;
import Util.Assert;
import Util.Strings;
import Util.Collections.HashCodeComparator;
import Util.Collections.LinearSet;
import Util.Collections.Pair;
import Util.Collections.SetFactory;
import Util.Collections.SetRepository;
import Util.Collections.SortedArraySet;

/**
 *
 * @author  John Whaley <jwhaley@alum.mit.edu>
 * @version $Id: AndersenPointerAnalysis.java,v 1.53 2003/07/04 07:28:26 joewhaley Exp $
 */
public class AndersenPointerAnalysis {

    /**
     * Output stream for trace information.
     */
    public static java.io.PrintStream out = System.out;
    
    /**
     * Controls the output of trace information.
     * This is useful in debugging the analysis, but causes
     * a LOT of information to be dumped.
     */
    public static /*final*/ boolean TRACE = false;
    
    /**
     * Output the cause of the *first* change in each iteration.
     */
    public static final boolean TRACE_CHANGE = false;
    
    /**
     * Output debugging information on the collapsing of cycles.
     */
    public static final boolean TRACE_CYCLES = false;
    
    /**
     * Enable/disable assertion checking.
     */
    public static final boolean VerifyAssertions = false;
    
    /**
     * Dump the call graph after analysis has completed.
     */
    public static boolean FULL_DUMP = false;
    
    /**
     * Compare our result to RTA, and dump the statistics.
     */
    public static boolean COMPARE_RTA = false;
    
    /**
     * Do the analysis twice, and report timings for each.
     */
    public static boolean DO_TWICE = false;
    
    /**
     * Don't explicitly model the calling of <clinit> methods.
     */
    public static boolean IGNORE_CLINIT = false;
    
    /**
     * Controls the handling of references that escape to native
     * methods or threads.
     * "true" means it takes a pessimistic view of threads and
     * native methods, assuming that they can update any reference
     * passed into them in arbitrary ways.
     * "false" means it takes an optimistic view, assuming that
     * they make no modifications that will matter to the pointer
     * analysis.
     */
    public static final boolean HANDLE_ESCAPE = false;
    
    /**
     * Controls the use of soft references for the lookup cache.
     */
    public static final boolean USE_SOFT_REFERENCES = false;
    
    /**
     * Force a garbage collection after every iteration of the algorithm.
     */
    public static boolean FORCE_GC = false;
    
    /**
     * Reuse the lookup cache across multiple iterations of the algorithm.
     */
    public static final boolean REUSE_CACHES = true;
    
    /**
     * Keep track of whether cache entries change between iterations,
     * to avoid the reconstruction and reduce the number of set union
     * operations.
     */
    public static final boolean TRACK_CHANGES = true;
    
    /**
     * Track which fields have changed between iterations.
     * ***DOESN'T GIVE CORRECT ANSWERS IN SOME CASES***
     */
    public static final boolean TRACK_CHANGED_FIELDS = false; // doesn't work.
    
    /**
     * Keep track of the reason why each inclusion edge was
     * added to the graph.
     */
    public static boolean TRACK_REASONS = true;
    
    /**
     * Keep track of inclusion back edges.
     */
    public static final boolean INCLUSION_BACK_EDGES = false;
    
    /**
     * Use a set repository, rather than a set factory.
     * The set repository attempts to reduce memory usage by
     * reusing set data structures.
     */
    public static final boolean USE_SET_REPOSITORY = false;

    private static MethodSummary getMethodSummary(AndersenMethod method) {
        if (method instanceof SSAMethod) {
            // This is for C++ analysis, the method classes are not jq_Methods, but instead
            // SSAMethods
            return ((SSAMethod)method).getSummary();
        }
        
        MethodSummary s = MethodSummary.getSummary(CodeCache.getCode((jq_Method)method));
        s.mergeGlobal();
        return s;
    }

    private static MethodSummary getMethodSummary(AndersenMethod method, CallSite cs) {
        if (method instanceof SSAMethod) {
            // This is for C++ analysis, the method classes are not jq_Methods, but instead
            // SSAMethods
            return ((SSAMethod)method).getSummary();
        }
        
        MethodSummary s = MethodSummary.getSummary(CodeCache.getCode((jq_Method)method), cs);
        s.mergeGlobal();
        return s;
    }
    
    /**
     * Add a control flow graph to the root set.
     * We get the method summary for the given control flow graph, and add
     * that to the root set.
     * 
     * @param cfg control flow graph to add
     * @return boolean whether the root set changed
     */
    public boolean addToRootSet(ControlFlowGraph cfg) {
        if (TRACE) out.println("Adding "+cfg.getMethod()+" to root set.");
        MethodSummary s = MethodSummary.getSummary(cfg);
        s.mergeGlobal();
        return this.rootSet.add(s);
    }
    
    public boolean addToRootSet(MethodSummary s) {
        if (TRACE) out.println("Adding "+s.getMethod()+" to root set.");
        return this.rootSet.add(s);
    }
    
    public static final class Visitor implements ControlFlowGraphVisitor {
        public static boolean added_hook = false;
        public void visitCFG(ControlFlowGraph cfg) {
            INSTANCE.addToRootSet(cfg);
            if (!added_hook) {
                added_hook = true;
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    public void run() {
                        doIt();
                    }
                });
                if (TRACE) out.println("Added Andersen shutdown hook.");
            }
        }
        public static void doIt() {
            Set rootSet = INSTANCE.rootSet;
            long time = System.currentTimeMillis();
            INSTANCE.iterate();
            time = System.currentTimeMillis() - time;
            System.out.println("First time: "+time);

            if (DO_TWICE) {
                long mem1 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                System.out.println("Used memory before gc: "+mem1);
                INSTANCE = new AndersenPointerAnalysis(true);
                INSTANCE.rootSet.addAll(rootSet);
                System.gc();
                long mem2 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                System.out.println("Used memory after gc: "+mem2);
                time = System.currentTimeMillis();
                INSTANCE.iterate();
                time = System.currentTimeMillis() - time;
            
                long mem3 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                System.out.println("Used memory before gc: "+mem3);
                System.gc();
                long mem4 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                System.out.println("Used memory after gc: "+mem4);

                System.out.println("Our analysis: "+(time/1000.)+" seconds, "+(mem2-mem2)+" bytes of memory");
            }
            
            System.out.println("Result of Andersen pointer analysis:");
            CallGraph cg = INSTANCE.getCallGraph();
            if (FULL_DUMP)
                System.out.println(cg);
            System.out.println(INSTANCE.computeStats());
            System.out.println(cg.computeHistogram("call site", "target"));
            
            if (COMPARE_RTA) {
                calcRTA();

                System.out.println("Compare to CHA/RTA:");
                HashMap cha_rta_callSiteToTargets = new HashMap();
                for (Iterator i=INSTANCE.callSiteToTargets.entrySet().iterator(); i.hasNext(); ) {
                    Map.Entry e = (Map.Entry)i.next();
                    CallSite cs = (CallSite)e.getKey();
                    CallTargets ct = cs.m.getCallTargets();
                    cha_rta_callSiteToTargets.put(cs, ct);
                }
                cg = new AndersenCallGraph(cha_rta_callSiteToTargets, INSTANCE.rootSet);
                if (FULL_DUMP)
                    System.out.println(cg);
                System.out.println(cg.computeHistogram("call site", "target"));
            }
        }
        public static void calcRTA() {
            Collection/*<jq_Type>*/ s = Bootstrap.PrimordialClassLoader.loader.getAllTypes();
            for (;;) {
                int size = s.size();
                Iterator i = new ArrayList(s).iterator();
                while (i.hasNext()) {
                    jq_Type t = (jq_Type)i.next();
                    t.prepare();
                }
                s = Bootstrap.PrimordialClassLoader.loader.getAllTypes();
                //if (s.size() == size)
                    break;
            }
            System.out.println("Number of RTA classes: "+s.size());
            int nMethods = 0;
            Iterator i = s.iterator();
            Set methods = new HashSet();
            while (i.hasNext()) {
                jq_Type t = (jq_Type)i.next();
                if (t instanceof jq_Class) {
                    jq_Class k = (jq_Class)t;
                    k.load();
                    jq_Method[] ms = k.getDeclaredInstanceMethods();
                    for (int j=0; j<ms.length; ++j) {
                        methods.add(ms[j]);
                    }
                    ms = k.getDeclaredStaticMethods();
                    for (int j=0; j<ms.length; ++j) {
                        methods.add(ms[j]);
                    }
                }
            }
            System.out.println("Number of RTA methods: "+methods.size());
            int nInvokes = 0, nTargets = 0; long nBytecodes = 0;
            Iterator k = methods.iterator();
            while (k.hasNext()) {
                jq_Method m = (jq_Method)k.next();
                if (m.getBytecode() == null) continue;
                nBytecodes += m.getBytecode().length;
                InvokeCounter ic = new InvokeCounter(m);
                ic.forwardTraversal();
                nInvokes += ic.invokeCount; nTargets += ic.targetCount;
            }
            System.out.println("Number of RTA invocations: "+nInvokes);
            System.out.println("Number of RTA call graph edges: "+nTargets);
            System.out.println("Number of RTA bytecodes: "+nBytecodes);
        }
        
        static class InvokeCounter extends Compil3r.BytecodeAnalysis.BytecodeVisitor {
            int invokeCount = 0; int targetCount = 0;
            InvokeCounter(jq_Method m) { super(m); }
            void visitInvoke(byte op, jq_Method f) {
                invokeCount++;
                CallTargets ct = CallTargets.getTargets(method.getDeclaringClass(), f, op, true);
                targetCount += ct.size();
            }
            public void visitIINVOKE(byte op, jq_Method f) {
                visitInvoke(op, f);
            }
            public void visitLINVOKE(byte op, jq_Method f) {
                visitInvoke(op, f);
            }
            public void visitFINVOKE(byte op, jq_Method f) {
                visitInvoke(op, f);
            }
            public void visitDINVOKE(byte op, jq_Method f) {
                visitInvoke(op, f);
            }
            public void visitAINVOKE(byte op, jq_Method f) {
                visitInvoke(op, f);
            }
            public void visitVINVOKE(byte op, jq_Method f) {
                visitInvoke(op, f);
            }
        }
        
        public static void doIt_output() {
            INSTANCE.iterate();
            System.out.println("Result of Andersen pointer analysis:");
            CallGraph cg = INSTANCE.getCallGraph();
            System.out.println(cg);
            System.out.println(INSTANCE.computeStats());
            System.out.println(cg.computeHistogram("call site", "target"));

            if (COMPARE_RTA) {
                System.out.println("Compare to CHA/RTA:");
                calcRTA();
                HashMap cha_rta_callSiteToTargets = new HashMap();
                for (Iterator i=INSTANCE.callSiteToTargets.entrySet().iterator(); i.hasNext(); ) {
                    Map.Entry e = (Map.Entry)i.next();
                    CallSite cs = (CallSite)e.getKey();
                    CallTargets ct = cs.m.getCallTargets();
                    cha_rta_callSiteToTargets.put(cs, ct);
                }
                cg = new AndersenCallGraph(cha_rta_callSiteToTargets, INSTANCE.rootSet);
                System.out.println(cg);
                System.out.println(cg.computeHistogram("call site", "target"));
            }
        }
    }
    
    public void initializeStatics(boolean addMethodsToVisit) {
        // add initializations for System.in/out/err
        jq_Class fd = (jq_Class)Bootstrap.PrimordialClassLoader.loader.getOrCreateBSType("Ljava/io/FileDescriptor;");
        fd.load();
        ConcreteTypeNode fd_n1 = new ConcreteTypeNode(fd);
        jq_Initializer fd_init = (jq_Initializer)fd.getOrCreateInstanceMethod("<init>", "(I)V");
        Assert._assert(fd_init.isLoaded());
        ProgramLocation mc_fd_init = new ProgramLocation.QuadProgramLocation(fd_init, null);
        fd_n1.recordPassedParameter(mc_fd_init, 0);
        
        jq_Class fis = (jq_Class)Bootstrap.PrimordialClassLoader.loader.getOrCreateBSType("Ljava/io/FileInputStream;");
        fis.load();
        ConcreteTypeNode fis_n = new ConcreteTypeNode(fis);
        jq_Initializer fis_init = (jq_Initializer)fis.getOrCreateInstanceMethod("<init>", "(Ljava/io/FileDescriptor;)V");
        Assert._assert(fis_init.isLoaded());
        ProgramLocation mc_fis_init = new ProgramLocation.QuadProgramLocation(fis_init, null);
        fis_n.recordPassedParameter(mc_fis_init, 0);
        fd_n1.recordPassedParameter(mc_fis_init, 1);
        jq_Class bis = (jq_Class)Bootstrap.PrimordialClassLoader.loader.getOrCreateBSType("Ljava/io/BufferedInputStream;");
        bis.load();
        ConcreteTypeNode bis_n = new ConcreteTypeNode(bis);
        jq_Initializer bis_init = (jq_Initializer)bis.getOrCreateInstanceMethod("<init>", "(Ljava/io/InputStream;)V");
        Assert._assert(bis_init.isLoaded());
        ProgramLocation mc_bis_init = new ProgramLocation.QuadProgramLocation(bis_init, null);
        bis_n.recordPassedParameter(mc_bis_init, 0);
        fis_n.recordPassedParameter(mc_bis_init, 1);
        
        jq_Class jls = Bootstrap.PrimordialClassLoader.getJavaLangSystem();
        jls.load();
        jq_StaticField si = jls.getOrCreateStaticField("in", "Ljava/io/InputStream;");
        Assert._assert(si.isLoaded());
        GlobalNode.GLOBAL.addEdge(si, bis_n, null);
        
        MethodSummary fd_init_summary = getMethodSummary(fd_init);
        OutsideNode on = fd_init_summary.getParamNode(0);
        addInclusionEdge(on, fd_n1, null);
        MethodSummary fis_init_summary = getMethodSummary(fis_init);
        on = fis_init_summary.getParamNode(0);
        addInclusionEdge(on, fis_n, null);
        on = fis_init_summary.getParamNode(1);
        addInclusionEdge(on, fd_n1, null);
        MethodSummary bis_init_summary = getMethodSummary(bis_init);
        on = bis_init_summary.getParamNode(0);
        addInclusionEdge(on, bis_n, null);
        on = bis_init_summary.getParamNode(1);
        addInclusionEdge(on, fis_n, null);
        
        ConcreteTypeNode fd_n2 = new ConcreteTypeNode(fd);
        fd_n2.recordPassedParameter(mc_fd_init, 0);
        jq_Class fos = (jq_Class)Bootstrap.PrimordialClassLoader.loader.getOrCreateBSType("Ljava/io/FileOutputStream;");
        fos.load();
        ConcreteTypeNode fos_n1 = new ConcreteTypeNode(fos);
        jq_Initializer fos_init = (jq_Initializer)fos.getOrCreateInstanceMethod("<init>", "(Ljava/io/FileDescriptor;)V");
        Assert._assert(fos_init.isLoaded());
        ProgramLocation mc_fos_init = new ProgramLocation.QuadProgramLocation(fos_init, null);
        fos_n1.recordPassedParameter(mc_fos_init, 0);
        fd_n2.recordPassedParameter(mc_fos_init, 1);
        jq_Class bos = (jq_Class)Bootstrap.PrimordialClassLoader.loader.getOrCreateBSType("Ljava/io/BufferedOutputStream;");
        bos.load();
        ConcreteTypeNode bos_n1 = new ConcreteTypeNode(bos);
        jq_Initializer bos_init = (jq_Initializer)bos.getOrCreateInstanceMethod("<init>", "(Ljava/io/OutputStream;I)V");
        Assert._assert(bos_init.isLoaded());
        ProgramLocation mc_bos_init = new ProgramLocation.QuadProgramLocation(bos_init, null);
        bos_n1.recordPassedParameter(mc_bos_init, 0);
        fos_n1.recordPassedParameter(mc_bos_init, 1);
        
        jq_Class ps = (jq_Class)Bootstrap.PrimordialClassLoader.loader.getOrCreateBSType("Ljava/io/PrintStream;");
        ps.load();
        ConcreteTypeNode ps_n1 = new ConcreteTypeNode(ps);
        jq_Initializer ps_init = (jq_Initializer)ps.getOrCreateInstanceMethod("<init>", "(Ljava/io/OutputStream;Z)V");
        Assert._assert(ps_init.isLoaded());
        ProgramLocation mc_ps_init = new ProgramLocation.QuadProgramLocation(ps_init, null);
        ps_n1.recordPassedParameter(mc_ps_init, 0);
        bos_n1.recordPassedParameter(mc_ps_init, 1);
        
        jq_StaticField so = jls.getOrCreateStaticField("out", "Ljava/io/PrintStream;");
        Assert._assert(so.isLoaded());
        GlobalNode.GLOBAL.addEdge(so, ps_n1, null);
        
        ConcreteTypeNode fd_n3 = new ConcreteTypeNode(fd);
        fd_n3.recordPassedParameter(mc_fd_init, 0);
        ConcreteTypeNode fos_n2 = new ConcreteTypeNode(fos);
        fos_n2.recordPassedParameter(mc_fos_init, 0);
        fd_n3.recordPassedParameter(mc_fos_init, 1);
        ConcreteTypeNode bos_n2 = new ConcreteTypeNode(bos);
        bos_n2.recordPassedParameter(mc_bos_init, 0);
        fos_n2.recordPassedParameter(mc_bos_init, 1);
        ConcreteTypeNode ps_n2 = new ConcreteTypeNode(ps);
        ps_n2.recordPassedParameter(mc_ps_init, 0);
        bos_n2.recordPassedParameter(mc_ps_init, 1);
        
        so = jls.getOrCreateStaticField("err", "Ljava/io/PrintStream;");
        Assert._assert(so.isLoaded());
        GlobalNode.GLOBAL.addEdge(so, ps_n2, null);
        
        on = fd_init_summary.getParamNode(0);
        addInclusionEdge(on, fd_n2, null);
        addInclusionEdge(on, fd_n3, null);
        MethodSummary fos_init_summary = getMethodSummary(fos_init);
        on = fos_init_summary.getParamNode(0);
        addInclusionEdge(on, fos_n1, null);
        addInclusionEdge(on, fos_n2, null);
        on = fos_init_summary.getParamNode(1);
        addInclusionEdge(on, fd_n2, null);
        addInclusionEdge(on, fd_n3, null);
        MethodSummary bos_init_summary = getMethodSummary(bos_init);
        on = bos_init_summary.getParamNode(0);
        addInclusionEdge(on, bos_n1, null);
        addInclusionEdge(on, bos_n2, null);
        on = bos_init_summary.getParamNode(1);
        addInclusionEdge(on, fos_n1, null);
        addInclusionEdge(on, fos_n2, null);
        MethodSummary ps_init_summary = getMethodSummary(ps_init);
        on = ps_init_summary.getParamNode(0);
        addInclusionEdge(on, ps_n1, null);
        addInclusionEdge(on, ps_n2, null);
        on = ps_init_summary.getParamNode(1);
        addInclusionEdge(on, bos_n1, null);
        addInclusionEdge(on, bos_n2, null);
        
        jq_Class nt = (jq_Class)Bootstrap.PrimordialClassLoader.loader.getOrCreateBSType("LScheduler/jq_NativeThread;");
        nt.load();
        ConcreteTypeNode nt_n1 = new ConcreteTypeNode(nt);
        Assert._assert(Scheduler.jq_NativeThread._nativeThreadEntry.isLoaded());
        ProgramLocation mc_nte = new ProgramLocation.QuadProgramLocation(Scheduler.jq_NativeThread._nativeThreadEntry, null);
        nt_n1.recordPassedParameter(mc_nte, 0);
        MethodSummary nte_summary = getMethodSummary(Scheduler.jq_NativeThread._nativeThreadEntry);
        on = nte_summary.getParamNode(0);
        addInclusionEdge(on, nt_n1, null);
        Assert._assert(Scheduler.jq_NativeThread._threadSwitch.isLoaded());
        ProgramLocation mc_ts = new ProgramLocation.QuadProgramLocation(Scheduler.jq_NativeThread._threadSwitch, null);
        nt_n1.recordPassedParameter(mc_ts, 0);
        MethodSummary ts_summary = getMethodSummary(Scheduler.jq_NativeThread._threadSwitch);
        on = ts_summary.getParamNode(0);
        addInclusionEdge(on, nt_n1, null);
        
        if (addMethodsToVisit) {
            methodSummariesToVisit.add(fd_init_summary);
            methodSummariesToVisit.add(fis_init_summary);
            methodSummariesToVisit.add(bis_init_summary);
            methodSummariesToVisit.add(fos_init_summary);
            methodSummariesToVisit.add(bos_init_summary);
            methodSummariesToVisit.add(ps_init_summary);
            methodSummariesToVisit.add(nte_summary);
            methodSummariesToVisit.add(ts_summary);
        }
    }
    
    /** Cache: Maps a node to its set of corresponding concrete nodes. */
    final HashMap nodeToConcreteNodes;
    
    /** Maps a node to its set of outgoing inclusion edges. */
    final HashMap nodeToInclusionEdges;
    
    /** Maps a node to its set of incoming inclusion edges.
     *  Only used if INCLUSION_BACK_EDGES is set. */
    final HashMap nodeToIncomingInclusionEdges;
    
    /** Maps an inclusion edge to the ProgramLocation that caused the edge.
     *  Only used if TRACK_REASONS is set. */
    final HashMap edgesToReasons;
    
    /** Set of all MethodSummary's that we care about. */
    final Set rootSet;
    final Set methodSummariesToVisit;
    
    /** Maps a call site to its set of targets. */
    final HashMap callSiteToTargets;
    
    /** The set of method call->targets that have already been linked. */
    final HashSet linkedTargets;
    
    /** Records if the cache for the node is current, and whether it has changed
     *  since the last iteration.  Only used if REUSE_CACHES is true. */
    final HashMap cacheIsCurrent;

    /** Records edges that have not yet been propagated.
     *  Only used if TRACK_CHANGES is true. */
    final HashSet unpropagatedEdges;
    
    /** Records nodes that have been collapsed, and which predecessors have
     *  seen the collapse.  Only used if TRACK_CHANGES is true. */
    final HashMap collapsedNodes;

    /** Records what fields have changed.  Only used if TRACK_CHANGED_FIELDS is true. */
    HashSet oldChangedFields;
    HashSet newChangedFields;
    HashSet changedFields_Methods;
    
    SetFactory cacheSetFactory;
    SetFactory inclusionEdgeSetFactory;
    
    /** Change flag, for iterations. */
    boolean change;
    
    /** Creates new AndersenPointerAnalysis */
    public AndersenPointerAnalysis(boolean addDefaults) {
        nodeToConcreteNodes = new HashMap();
        nodeToInclusionEdges = new HashMap();
        if (INCLUSION_BACK_EDGES)
            nodeToIncomingInclusionEdges = new HashMap();
        else
            nodeToIncomingInclusionEdges = null;
        rootSet = SortedArraySet.FACTORY.makeSet(HashCodeComparator.INSTANCE);
        methodSummariesToVisit = new LinkedHashSet();
        callSiteToTargets = new HashMap();
        linkedTargets = new HashSet();
        if (REUSE_CACHES)
            cacheIsCurrent = new HashMap();
        else
            cacheIsCurrent = null;
        if (TRACK_CHANGES) {
            unpropagatedEdges = new HashSet();
            collapsedNodes = new HashMap();
        } else {
            unpropagatedEdges = null;
            collapsedNodes = null;
        }
        if (TRACK_REASONS)
            edgesToReasons = new HashMap();
        else
            edgesToReasons = null;
        if (TRACK_CHANGED_FIELDS) {
            /*oldChangedFields =*/ newChangedFields = new HashSet();
            changedFields_Methods = new HashSet();
        }
        if (USE_SET_REPOSITORY) {
            cacheSetFactory = new SetRepository();
        } else {
            //cacheSetFactory = SetRepository.LinkedHashSetFactory.INSTANCE;
            //cacheSetFactory = Util.SortedArraySet.FACTORY;
            cacheSetFactory = NodeSet.FACTORY;
        }
        //inclusionEdgeSetFactory = SetRepository.LinkedHashSetFactory.INSTANCE;
        //inclusionEdgeSetFactory = Util.SortedArraySet.FACTORY;
        inclusionEdgeSetFactory = NodeSet.FACTORY;
        this.initializeStatics(addDefaults);
    }

    public static AndersenPointerAnalysis INSTANCE = new AndersenPointerAnalysis(true);
    
    public String computeStats() {
        StringBuffer sb = new StringBuffer();
        HashSet classes = new HashSet();
        HashSet methods = new HashSet();
        long bytecodes = 0;
        for (Iterator i=methodSummariesToVisit.iterator(); i.hasNext(); ) {
            MethodSummary ms = (MethodSummary)i.next();
            methods.add(ms.getMethod());
            bytecodes += ((jq_Method)ms.getMethod()).getBytecode().length;
            jq_Class c = ((jq_Method)ms.getMethod()).getDeclaringClass();
            while (c != null) {
                classes.add(c);
                c.load();
                c = c.getSuperclass();
            }
        }
        sb.append(" Classes: ");
        sb.append(classes.size());
        sb.append(" Methods: ");
        sb.append(methods.size());
        sb.append(" Summaries: ");
        sb.append(methodSummariesToVisit.size());
        sb.append(" Calls: ");
        sb.append(callSiteToTargets.size());
        sb.append(" Bytecodes ");
        sb.append(bytecodes);
        sb.append(" Iteration ");
        sb.append(count);
        sb.append(Strings.lineSep);
        return sb.toString();
    }
    public static Map buildOriginalCallGraph(Map m) {
        HashMap newCG = new HashMap();
        for (Iterator i=m.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry e = (Map.Entry)i.next();
            CallSite cs = (CallSite)e.getKey();
            Set s = (Set)e.getValue();
            ProgramLocation mc = cs.m;
            Set s2 = (Set) newCG.get(mc);
            if (s2 == null)
                newCG.put(mc, s2 = new HashSet());
            s2.addAll(s);
        }
        return newCG;
    }
    public static String compareWithOriginal(Map cg, Map original) {
        HashMap table = new HashMap();
        for (Iterator i=cg.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry e = (Map.Entry)i.next();
            CallSite cs = (CallSite)e.getKey();
            Set s = (Set)e.getValue();
            int x = s.size();
            ProgramLocation mc = cs.m;
            Set s_orig = (Set)original.get(mc);
            int y = s_orig.size();
            Object key = new Pair(new Integer(y),new Integer(x));
            HashSet k = (HashSet) table.get(key);
            if (k == null) table.put(key, k = new HashSet());
            k.add(mc);
        }
        StringBuffer sb = new StringBuffer();
        for (Iterator i=table.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry e = (Map.Entry)i.next();
            sb.append(e.getKey());
            sb.append(": ");
            Set s = (Set) e.getValue();
            sb.append(s.size());
            sb.append(Strings.lineSep);
        }
        return sb.toString();
    }
    public static final int HISTOGRAM_SIZE = 100;
    public static String computeHistogram2(Map m) {
        HashMap table = new HashMap();
        for (Iterator i=m.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry e = (Map.Entry)i.next();
            CallSite cs = (CallSite)e.getKey();
            ProgramLocation mc = cs.m;
            Set s = (Set)e.getValue();
            Set s2 = (Set)table.get(mc);
            if (s2 == null) table.put(mc, s2 = new LinearSet());
            s2.add(s);
        }
        StringBuffer sb = new StringBuffer();
        int[] histogram = new int[HISTOGRAM_SIZE];
        long total = 0;
        for (Iterator i=table.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry e = (Map.Entry)i.next();
            Set s = (Set)e.getValue();
            int x = s.size();
            int y = 0;
            for (Iterator j=s.iterator(); j.hasNext(); ) {
                y += ((Set)j.next()).size();
            }
            int foo = y / x;
            if (foo >= HISTOGRAM_SIZE) foo = HISTOGRAM_SIZE-1;
            histogram[foo]++;
            total += foo;
        }
        sb.append(" Total # of call graph edges: ");
        sb.append(total);
        sb.append('/');
        sb.append(total+histogram[0]);
        sb.append(Strings.lineSep);
        for (int i=0; i<HISTOGRAM_SIZE; ++i) {
            if (histogram[i] > 0) {
                if (i == HISTOGRAM_SIZE-1) sb.append(">=");
                sb.append(i);
                sb.append(" targets:\t");
                sb.append(histogram[i]);
                sb.append(" call site");
                if (histogram[i] > 1) sb.append('s');
                sb.append(Strings.lineSep);
            }
        }
        sb.append("Average # of targets: "+(double)total/(double)table.size());
        sb.append(Strings.lineSep);
        long total_multi = total - histogram[1];
        long cs_multi = table.size() - histogram[1] - histogram[0];
        sb.append("# of multi-target calls: "+cs_multi);
        sb.append(Strings.lineSep);
        sb.append("Total targets for multi-target calls: "+total_multi);
        sb.append(Strings.lineSep);
        sb.append("Average # of targets for multi: "+(double)total_multi/(double)cs_multi);
        sb.append(Strings.lineSep);
        return sb.toString();
    }
    
    int count;
    
    public void iterate() {
        methodSummariesToVisit.addAll(rootSet);
        count = 1;
        for (;;) {
            this.change = false;
            System.err.println("Iteration "+count+": "+methodSummariesToVisit.size()+" methods "+callSiteToTargets.size()+" call sites "+linkedTargets.size()+" call graph edges");
            doGlobals();
            LinkedList ll = new LinkedList();
            ll.addAll(methodSummariesToVisit);
            for (Iterator i=ll.iterator(); i.hasNext(); ) {
                MethodSummary ms = (MethodSummary)i.next();
                visitMethod(ms);
            }
            if (!change) break;
            if (REUSE_CACHES)
                cacheIsCurrent.clear();
            else
                nodeToConcreteNodes.clear();
            if (TRACK_CHANGED_FIELDS) {
                oldChangedFields = newChangedFields;
                System.err.println(oldChangedFields.size()+" changed fields");
                newChangedFields = new HashSet();
            }
            if (FORCE_GC) System.gc();
            ++count;
        }
    }
    
    void doGlobals() {
        if (TRACE) out.println("Doing global variables...");
        LinkedHashSet lhs = new LinkedHashSet();
        lhs.addAll(GlobalNode.GLOBAL.getAccessPathEdges());
        for (Iterator j=lhs.iterator(); j.hasNext(); ) {
            Map.Entry e = (Map.Entry)j.next();
            AndersenField f = (AndersenField)e.getKey();
            Object o = e.getValue();
            if (!IGNORE_CLINIT && !MethodSummary.IGNORE_STATIC_FIELDS) {
                AndersenClass c = f.and_getDeclaringClass();
                if (TRACE) out.println("Visiting edge: "+o+" = "+c+((f==null)?"[]":("."+f.getName())));
                c.load();
                AndersenClassInitializer clinit = c.and_getClassInitializer();
                if (clinit != null) {
                    MethodSummary ms = getMethodSummary(clinit);
                    if (methodSummariesToVisit.add(ms)) {
                        if ((TRACE_CHANGE && !this.change) || TRACE) {
                            out.println("Changed! New clinit method: "+clinit);
                        }
                        this.change = true;
                    }
                }
            }
            // o = n.f
            if (o instanceof Set) {
                addGlobalEdges((Set)o, f);
            } else {
                addGlobalEdges((FieldNode)o, f);
            }
        }
    }

    /* DMW- removed to avoid calling by mistake (doesn't work for C++
     * re-enable it if you need it for Java analysis only
    void visitMethod(ControlFlowGraph cfg) {
        MethodSummary ms = MethodSummary.getSummary(cfg);
        ms.mergeGlobal();
        this.visitMethod(ms);
    }
     */
    void visitMethod(MethodSummary ms) {
        if (TRACE) out.println("Visiting method: "+ms.getMethod());
        // find edges in graph
        for (Iterator i=ms.nodeIterator(); i.hasNext(); ) {
            Node n = (Node)i.next();
            for (Iterator j=n.getEdges().iterator(); j.hasNext(); ) {
                Map.Entry e = (Map.Entry)j.next();
                jq_Field f = (jq_Field)e.getKey();
                if (TRACK_CHANGED_FIELDS) {
                    if (!changedFields_Methods.contains(ms.getMethod())) {
                        newChangedFields.add(f);
                    }
                }
                Object o = e.getValue();
                if (TRACE) out.println("Visiting edge: "+n+((f==null)?"[]":("."+f.getName()))+" = "+o);
                // n.f = o
                if (o instanceof Set) {
                    addEdgesFromConcreteNodes(n, f, (Set)o);
                } else {
                    addEdgesFromConcreteNodes(n, f, (Node)o);
                }
            }
            if (HANDLE_ESCAPE) {
                if (n instanceof OutsideNode && n.getEscapes()) {
                    Set s = getConcreteNodes(n);
                    if (TRACE) out.println("Escaping node "+n+" corresponds to concrete nodes "+s);
                    for (Iterator j=s.iterator(); j.hasNext(); ) {
                        Node n2 = (Node)j.next();
                        if (!n2.getEscapes()) {
                            n2.setEscapes();
                            if ((TRACE_CHANGE && !this.change) || TRACE) {
                                out.println("Changed! Concrete node "+n2+" escapes");
                            }
                            this.change = true;
                        }
                    }
                }
            }
            for (Iterator j=n.getAccessPathEdges().iterator(); j.hasNext(); ) {
                Map.Entry e = (Map.Entry)j.next();
                jq_Field f = (jq_Field)e.getKey();
                if (TRACK_CHANGED_FIELDS) {
                    if (!changedFields_Methods.contains(ms.getMethod())) {
                        changedFields_Methods.add(ms.getMethod());
                    } else if (oldChangedFields != null && !oldChangedFields.contains(f) && !newChangedFields.contains(f)) continue;
                }
                Object o = e.getValue();
                if (TRACE) out.println("Visiting edge: "+o+" = "+n+((f==null)?"[]":("."+f.getName())));
                // o = n.f
                if (o instanceof Set) {
                    addInclusionEdgesToConcreteNodes((Set)o, n, f);
                } else {
                    addInclusionEdgesToConcreteNodes((FieldNode)o, n, f);
                }
            }
        }
        
        // find all methods that we call.
        for (Iterator i=ms.getCalls().iterator(); i.hasNext(); ) {
            ProgramLocation mc = (ProgramLocation)i.next();
            CallSite cs = new CallSite(ms, mc);
            if (TRACE) out.println("Found call: "+ms+": "+mc.toString());
            CallTargets ct = mc.getCallTargets();
            if (TRACE) out.println("Possible targets ignoring type information: "+ct);
            Set definite_targets = SortedArraySet.FACTORY.makeSet(HashCodeComparator.INSTANCE);
            //Assert._assert(!callSiteToTargets.containsKey(cs));
            callSiteToTargets.put(cs, definite_targets);
            if (ct.size() == 1 && ct.isComplete()) {
                // call can be statically resolved to a single target.
                if (TRACE) out.println("Call is statically resolved to a single target.");
                definite_targets.add(ct.iterator().next());
            } else {
                // use the type information about the receiver object to find targets.
                PassedParameter pp = new PassedParameter(mc, 0);
                Set set = ms.getNodesThatCall(pp);
                if (TRACE) out.println("Possible nodes for receiver object: "+set);
                for (Iterator j=set.iterator(); j.hasNext(); ) {
                    Node base = (Node)j.next();
                    if (TRACE) out.println("Checking base node: "+base);
                    Set s_cn = getConcreteNodes(base);
                    Set targets = mc.getCallTargets(s_cn);
                    definite_targets.addAll(targets);
                }
            }
            if (TRACE) out.println("Set of definite targets of "+mc+": "+definite_targets);
            for (Iterator j=definite_targets.iterator(); j.hasNext(); ) {
                AndersenMethod callee = (AndersenMethod)j.next();
                // temporary: skip multinewarray.
                if (callee == Run_Time.Arrays._multinewarray) continue;
                callee.and_getDeclaringClass().load();
                if (!callee.isBodyLoaded()) {
                    CallSite cs2 = new CallSite(null, mc);
                    if (linkedTargets.contains(cs2)) continue;
                    linkedTargets.add(cs2);
                    if ((TRACE_CHANGE && !this.change) || TRACE) {
                        out.println("Changed! New target for "+mc+": "+callee+" (unanalyzable)");
                    }
                    this.change = true;
                    if (TRACE) out.println(callee+" is a native method, skipping analysis...");
                    addParameterAndReturnMappings_native(ms, mc);
                    continue;
                }
                MethodSummary callee_summary = getMethodSummary(callee, cs);
                CallSite cs2 = new CallSite(callee_summary, mc);
                if (linkedTargets.contains(cs2)) continue;
                linkedTargets.add(cs2);
                if ((TRACE_CHANGE && !this.change) || TRACE) {
                    out.println("Changed! New target for "+mc+": "+callee_summary.getMethod());
                }
                this.change = true;
                addParameterAndReturnMappings(ms, mc, callee_summary);
                methodSummariesToVisit.add(callee_summary);
            }
        }
    }

    void addParameterAndReturnMappings_native(MethodSummary caller, ProgramLocation mc) {
        if (TRACE) out.println("Adding parameter and return mappings for "+mc+" from "+caller.getMethod()+" to an unanalyzable method.");
//        ParamListOperand plo = Invoke.getParamList(mc.getQuad());
//        jq_Method targetMethod = Invoke.getMethod(mc.getQuad()).getMethod();
        AndersenMethod targetMethod = mc.getTargetMethod();
        for (int i=0; i<mc.getNumParams(); ++i) {
            AndersenType t = mc.getParamType(i);
            if (!(t instanceof AndersenReference)) continue;
            // parameters passed into native methods escape.
            PassedParameter pp = new PassedParameter(mc, i);
            Set s = caller.getNodesThatCall(pp);
            for (Iterator j=s.iterator(); j.hasNext(); ) {
                Node n = (Node)j.next();
                if (!n.getEscapes()) {
                    n.setEscapes();
                    if ((TRACE_CHANGE && !this.change) || TRACE) {
                        out.println("Changed! Node "+n+" escapes");
                    }
                    this.change = true;
                }
                // TODO: add edges to escape.
            }
        }
        Set rvn_s;
        Object rvn_o = caller.callToRVN.get(mc);
        if (rvn_o instanceof Set) rvn_s = (Set) rvn_o;
        else if (rvn_o != null) rvn_s = Collections.singleton(rvn_o);
        else rvn_s = Collections.EMPTY_SET;
        for (Iterator i=rvn_s.iterator(); i.hasNext(); ) {
            ReturnValueNode rvn = (ReturnValueNode) i.next();
            if (rvn != null) {
                UnknownTypeNode utn = UnknownTypeNode.get((AndersenReference)targetMethod.and_getReturnType());
                if (TRACE) out.println("Adding return mapping "+rvn+" to "+utn);
                OutsideNode on = rvn;
                while (on.skip != null) on = on.skip;
                addInclusionEdge(on, utn, mc);
            }
        }
        Set ten_s;
        Object ten_o = caller.callToTEN.get(mc);
        if (ten_o instanceof Set) ten_s = (Set) ten_o;
        else if (ten_o != null) ten_s = Collections.singleton(ten_o);
        else ten_s = Collections.EMPTY_SET;
        for (Iterator i=ten_s.iterator(); i.hasNext(); ) {
            ThrownExceptionNode ten = (ThrownExceptionNode) i.next();
            if (ten != null) {
                UnknownTypeNode utn = UnknownTypeNode.get((jq_Reference)Bootstrap.PrimordialClassLoader.getJavaLangObject());
                if (TRACE) out.println("Adding thrown mapping "+ten+" to "+utn);
                OutsideNode on = ten;
                while (on.skip != null) on = on.skip;
                addInclusionEdge(on, utn, mc);
            }
        }
    }
    
    void addParameterAndReturnMappings(MethodSummary caller, ProgramLocation mc, MethodSummary callee) {
        if (TRACE) out.println("Adding parameter and return mappings for "+mc+" from "+caller.getMethod()+" to "+callee.getMethod());
//        ParamListOperand plo = Invoke.getParamList(mc.getQuad());
        for (int i=0; i<mc.getNumParams(); ++i) {
//            AndersenType t = mc.getParamType(i);
            if (i >= callee.getNumOfParams()) break;
            ParamNode pn = callee.getParamNode(i);
            if (pn == null) continue;
            PassedParameter pp = new PassedParameter(mc, i);
            Set s = caller.getNodesThatCall(pp);
            if (TRACE) out.println("Adding parameter mapping "+pn+" to set "+s);
            OutsideNode on = pn;
            while (on.skip != null) on = on.skip;
            addInclusionEdges(on, s, mc);
        }
        Set rvn_s;
        Object rvn_o = caller.callToRVN.get(mc);
        if (rvn_o instanceof Set) rvn_s = (Set) rvn_o;
        else if (rvn_o != null) rvn_s = Collections.singleton(rvn_o);
        else rvn_s = Collections.EMPTY_SET;
        for (Iterator i=rvn_s.iterator(); i.hasNext(); ) {
            ReturnValueNode rvn = (ReturnValueNode) i.next();
            if (rvn != null) {
                Set s = callee.returned;
                if (TRACE) out.println("Adding return mapping "+rvn+" to set "+s);
                OutsideNode on = rvn;
                while (on.skip != null) on = on.skip;
                addInclusionEdges(on, s, mc);
            }
        }
        Set ten_s;
        Object ten_o = caller.callToTEN.get(mc);
        if (ten_o instanceof Set) ten_s = (Set) ten_o;
        else if (ten_o != null) ten_s = Collections.singleton(ten_o);
        else ten_s = Collections.EMPTY_SET;
        for (Iterator i=ten_s.iterator(); i.hasNext(); ) {
            ThrownExceptionNode ten = (ThrownExceptionNode) i.next();
            if (ten != null) {
                Set s = callee.thrown;
                if (TRACE) out.println("Adding thrown mapping "+ten+" to set "+s);
                OutsideNode on = ten;
                while (on.skip != null) on = on.skip;
                addInclusionEdges(on, s, mc);
            }
        }
    }
    
    void addInclusionBackEdge(OutsideNode n, Node n2) {
        Set s2 = (Set) nodeToIncomingInclusionEdges.get(n2);
        if (s2 == null) {
            nodeToIncomingInclusionEdges.put(n2, s2 = inclusionEdgeSetFactory.makeSet());
        }
        s2.add(n);
    }
    
    void addInclusionBackEdges(OutsideNode n, Set s) {
        for (Iterator i=s.iterator(); i.hasNext(); ) {
            Object o = i.next();
            if (o instanceof OutsideNode) {
                OutsideNode on = (OutsideNode) o;
                while (on.skip != null) {
                    on = on.skip;
                }
                o = on;
            }
            addInclusionBackEdge(n, (Node) o);
        }
    }
    
    boolean addInclusionEdges(OutsideNode n, Set s, Object mc) {
        if (VerifyAssertions) Assert._assert(n.skip == null);
        Set s2 = (Set)nodeToInclusionEdges.get(n);
        if (s2 == null) {
            s = inclusionEdgeSetFactory.makeSet(s);
            nodeToInclusionEdges.put(n, s);
            if (INCLUSION_BACK_EDGES)
                addInclusionBackEdges(n, s);
            if ((TRACE_CHANGE && !this.change) || TRACE) {
                out.println("Changed! New set of inclusion edges for node "+n);
            }
            this.change = true;
            if (TRACK_CHANGES) {
                // we need to mark these edges so that they will be propagated
                // regardless of whether or not the target set has changed.
                if (cacheContains(n)) {
                    for (Iterator i=s.iterator(); i.hasNext(); ) {
                        Object o = i.next();
                        if (o instanceof OutsideNode) {
                            if (TRACE) out.println("Adding "+n+"->"+o+" as an unpropagated edge...");
                            recordUnpropagatedEdge(n, (OutsideNode)o);
                        }
                    }
                }
            }
            if (TRACK_REASONS) {
                for (Iterator i=s.iterator(); i.hasNext(); ) {
                    Object o = i.next();
                    edgesToReasons.put(new Pair(n, o), mc);
                }
            }
            return true;
        } else {
            for (Iterator i=s.iterator(); i.hasNext(); ) {
                Object o = i.next();
                if (o instanceof OutsideNode) {
                    OutsideNode on = (OutsideNode)o;
                    while (on.skip != null) {
                        on = on.skip;
                    }
                    o = on;
                }
                if (n == o) continue;
                if (s2.add(o)) {
                    if (INCLUSION_BACK_EDGES)
                        addInclusionBackEdge(n, (Node) o);
                    if ((TRACE_CHANGE && !this.change) || TRACE) {
                        out.println("Changed! New inclusion edge for node "+n+": "+o);
                    }
                    this.change = true;
                    if (TRACK_CHANGES) {
                        if (o instanceof OutsideNode) {
                            // we need to mark this edge so that it will be propagated
                            // regardless of whether or not the target set has changed.
                            if (cacheContains(n)) {
                                if (TRACE) out.println("Adding "+n+"->"+o+" as an unpropagated edge...");
                                recordUnpropagatedEdge(n, (OutsideNode)o);
                            }
                        }
                    }
                    if (TRACK_REASONS) {
                        edgesToReasons.put(new Pair(n, o), mc);
                    }
                }
            }
            return false;
        }
    }
    
    void addInclusionEdge(OutsideNode n, Node s, Object mc) {
        if (VerifyAssertions) Assert._assert(n.skip == null);
        if (s instanceof OutsideNode) {
            OutsideNode on = (OutsideNode)s;
            while (on.skip != null) {
                on = on.skip;
            }
            s = on;
        }
        if (n == s) return;
        Set s2 = (Set)nodeToInclusionEdges.get(n);
        if (s2 == null) {
            s2 = inclusionEdgeSetFactory.makeSet(); s2.add(s);
            nodeToInclusionEdges.put(n, s2);
            if (INCLUSION_BACK_EDGES)
                addInclusionBackEdge(n, s);
            if ((TRACE_CHANGE && !this.change) || TRACE) {
                out.println("Changed! New set of inclusion edges for node "+n);
            }
            this.change = true;
            if (TRACK_CHANGES) {
                if (s instanceof OutsideNode) {
                    // we need to mark this edge so that it will be propagated
                    // regardless of whether or not the target set has changed.
                    if (cacheContains(n)) {
                        if (TRACE) out.println("Adding "+n+"->"+s+" as an unpropagated edge...");
                        recordUnpropagatedEdge(n, (OutsideNode)s);
                    }
                }
            }
            if (TRACK_REASONS) {
                edgesToReasons.put(new Pair(n, s), mc);
            }
        } else if (s2.add(s)) {
            if (INCLUSION_BACK_EDGES)
                addInclusionBackEdge(n, s);
            if ((TRACE_CHANGE && !this.change) || TRACE) {
                out.println("Changed! New inclusion edge for node "+n+": "+s);
            }
            this.change = true;
            if (TRACK_CHANGES) {
                if (s instanceof OutsideNode) {
                    // we need to mark this edge so that it will be propagated
                    // regardless of whether or not the target set has changed.
                    if (cacheContains(n)) {
                        if (TRACE) out.println("Adding "+n+"->"+s+" as an unpropagated edge...");
                        recordUnpropagatedEdge(n, (OutsideNode)s);
                    }
                }
            }
            if (TRACK_REASONS) {
                edgesToReasons.put(new Pair(n, s), mc);
            }
        }
    }
    
    Set getInclusionEdges(Node n) { return (Set)nodeToInclusionEdges.get(n); }
    
    Set getFromCache(OutsideNode n) {
        if (USE_SOFT_REFERENCES) {
            java.lang.ref.SoftReference r = (java.lang.ref.SoftReference)nodeToConcreteNodes.get(n);
            return r != null ? (Set)r.get() : null;
        } else {
            return (Set)nodeToConcreteNodes.get(n);
        }
    }
    
    void addToCache(OutsideNode n, Set s) {
        if (USE_SOFT_REFERENCES) {
            nodeToConcreteNodes.put(n, new java.lang.ref.SoftReference(s));
        } else {
            nodeToConcreteNodes.put(n, s);
        }
    }
    
    boolean cacheContains(OutsideNode n) {
        // this needs to return whether or not the cache EVER contained the given node.
        if (false) { // if (USE_SOFT_REFERENCES) {
            java.lang.ref.SoftReference r = (java.lang.ref.SoftReference)nodeToConcreteNodes.get(n);
            return r == null || r.get() == null;
        } else {
            return nodeToConcreteNodes.containsKey(n);
        }
    }
    
    static boolean checkInvalidFieldAccess(Node n, jq_Field f) {
        AndersenReference rtype = n.getDeclaredType();
        if (rtype == null) {
            if (TRACE) out.println("Node "+n+" is null, so cannot hold field access");
            return true;
        }
        if (MethodSummary.IGNORE_INSTANCE_FIELDS) return false;
        if (f == null) {
            if (rtype instanceof jq_Class) {
                if (TRACE) out.println("Node "+n+" is a class type, so it cannot hold array access");
                return true;
            }
        } else {
            if (!(rtype instanceof jq_Class)) {
                if (TRACE) out.println("Node "+n+" is an array type, so it cannot hold field access");
                return true;
            }
            jq_Class rclass = (jq_Class)rtype;
            rclass.load();
            if (rclass.getInstanceField(f.getNameAndDesc()) != f) {
                if (TRACE) out.println("Node "+n+" does not contain field "+f);
                return true;
            }
        }
        return false;
    }
    
    // from.f = to
    void addEdgesFromConcreteNodes(Node from, jq_Field f, Set to) {
        Set s = getConcreteNodes(from);
        if (TRACE) out.println("Node "+from+" corresponds to concrete nodes "+s);
        for (Iterator i=s.iterator(); i.hasNext(); ) {
            Node n = (Node)i.next();
            if (checkInvalidFieldAccess(n, f)) continue;
            if (n.addEdges(f, to, from)) {
                if ((TRACE_CHANGE && !this.change) || TRACE) {
                    out.println("Changed! New edges for concrete node "+n+"."+f+": "+to);
                }
                if (TRACK_CHANGED_FIELDS) newChangedFields.add(f);
                this.change = true;
            }
        }
    }
    
    // from.f = to
    void addEdgesFromConcreteNodes(Node from, jq_Field f, Node to) {
        Set s = getConcreteNodes(from);
        if (TRACE) out.println("Node "+from+" corresponds to concrete nodes "+s);
        for (Iterator i=s.iterator(); i.hasNext(); ) {
            Node n = (Node)i.next();
            if (checkInvalidFieldAccess(n, f)) continue;
            if (n.addEdge(f, to, from)) {
                if ((TRACE_CHANGE && !this.change) || TRACE) {
                    out.println("Changed! New edge for concrete node "+n+"."+f+": "+to);
                }
                if (TRACK_CHANGED_FIELDS) newChangedFields.add(f);
                this.change = true;
            }
        }
    }
    
    // from = global.f
    void addGlobalEdges(OutsideNode from, AndersenField f) {
        Set result = GlobalNode.GLOBAL.getEdges(f);
        while (from.skip != null) from = from.skip;
        FieldNode fn = FieldNode.get(GlobalNode.GLOBAL, f, null);
        addInclusionEdges(from, result, fn);
    }
    
    // from = global.f
    void addGlobalEdges(Set from, AndersenField f) {
        Set result = GlobalNode.GLOBAL.getEdges(f);
        FieldNode fn = FieldNode.get(GlobalNode.GLOBAL, f, null);
        for (Iterator j=from.iterator(); j.hasNext(); ) {
            OutsideNode n2 = (OutsideNode)j.next();
            while (n2.skip != null) n2 = n2.skip;
            addInclusionEdges(n2, result, fn);
        }
    }
    
    // from = base.f
    void addInclusionEdgesToConcreteNodes(Set from, Node base, jq_Field f) {
        Set s = getConcreteNodes(base);
        if (TRACE) out.println("Node "+base+" corresponds to concrete nodes "+s);
        Set result = NodeSet.FACTORY.makeSet();
        for (Iterator j=s.iterator(); j.hasNext(); ) {
            Node n2 = (Node)j.next();
            n2.getEdges(f, result);
        }
        if (TRACE) out.println("Edges from "+base+((f==null)?"[]":("."+f.getName()))+" : "+result);
        for (Iterator j=from.iterator(); j.hasNext(); ) {
            OutsideNode n2 = (OutsideNode)j.next();
            FieldNode fn = (FieldNode)n2;
            while (n2.skip != null) n2 = n2.skip;
            addInclusionEdges(n2, result, base);
        }
    }
    
    // from = base.f
    void addInclusionEdgesToConcreteNodes(OutsideNode from, Node base, jq_Field f) {
        Set s = getConcreteNodes(base);
        if (TRACE) out.println("Node "+base+" corresponds to concrete nodes "+s);
        Set result = NodeSet.FACTORY.makeSet();
        for (Iterator j=s.iterator(); j.hasNext(); ) {
            Node n2 = (Node)j.next();
            n2.getEdges(f, result);
        }
        if (TRACE) out.println("Edges from "+base+((f==null)?"[]":("."+f.getName()))+" : "+result);
        FieldNode fn = (FieldNode)from;
        while (from.skip != null) from = from.skip;
        addInclusionEdges(from, result, base);
    }
    
    Set getConcreteNodes(Node from, AccessPath ap) {
        Set s = getConcreteNodes(from);
        if (ap == null) return s;
        AndersenField f = ap.first();
        Set result = NodeSet.FACTORY.makeSet();
        for (Iterator j=s.iterator(); j.hasNext(); ) {
            Node n2 = (Node)j.next();
            n2.getEdges(f, result);
        }
        Set result2 = NodeSet.FACTORY.makeSet();
        ap = ap.next();
        for (Iterator j=result.iterator(); j.hasNext(); ) {
            Node n2 = (Node)j.next();
            result2.addAll(getConcreteNodes(n2, ap));
        }
        return result2;
    }
    
    Set getConcreteNodes(Node from) {
        if (from instanceof OutsideNode) 
            return getConcreteNodes((OutsideNode)from, (Path)null);
        else
            return Collections.singleton(from);
    }
    
    boolean temp_change;
    
    Set getConcreteNodes(OutsideNode from, Path p) {
        while (from.skip != null) {
            from = from.skip;
        }
        if (from.visited) {
            Path p2 = p;
            if (TRACE_CYCLES) out.println("cycle detected! node="+from+" path="+p);
            Set s = (Set)nodeToInclusionEdges.get(from);
            if (VerifyAssertions) Assert._assert(s != null);
            OutsideNode last = from;
            for (;; p = p.cdr()) {
                OutsideNode n = p.car();
                if (TRACK_CHANGES) markCollapsedNode(n);
                if (n == from) break;
                if (TRACE) out.println("next in path: "+n+", merging into: "+from);
                if (VerifyAssertions) Assert._assert(n.skip == null);
                n.skip = from;
                Set s2 = (Set)nodeToInclusionEdges.get(n);
                //s2.remove(last);
                if (TRACE) out.println("Set of inclusion edges from node "+n+": "+s2);
                s.addAll(s2);
                nodeToInclusionEdges.put(n, s);
                if (INCLUSION_BACK_EDGES)
                    addInclusionBackEdges(n, s);
                last = n;
            }
            //s.remove(last);
            for (Iterator i=s.iterator(); i.hasNext(); ) {
                Object o = i.next();
                if (o instanceof OutsideNode) {
                    OutsideNode on = (OutsideNode)o;
                    while (on.skip != null) on = on.skip;
                    o = on;
                }
                if (from == o) {
                    if (TRACE) out.println("Node "+from+" contains transitive self-edge, removing.");
                    i.remove();
                }
            }
            if (TRACE) out.println("Final set of inclusion edges from node "+from+": "+s);
            return null;
        }
        Set result = getFromCache(from);
        boolean brand_new = false;
        if (REUSE_CACHES) {
            if (result == null) {
                if (TRACE) out.println("No cache for "+from+" yet, creating.");
                result = cacheSetFactory.makeSet();
                addToCache(from, result);
                brand_new = true;
            } else {
                Object b = cacheIsCurrent.get(from);
                if (b != null) {
                    if (TRACE) out.println("Cache for "+from+" is current: "+result+" changed since last iteration: "+b);
                    if (TRACK_CHANGES) this.temp_change = ((Boolean)b).booleanValue();
                    return result;
                } else {
                    if (TRACE) out.println("Cache for "+from+" "+result+" is not current, updating.");
                }
            }
        } else {
            if (result != null) {
                if (TRACE) out.println("Using cached result for "+from+".");
                return result;
            }
            result = cacheSetFactory.makeSet();
            addToCache(from, result);
        }
        Set s = (Set)nodeToInclusionEdges.get(from);
        if (s == null) {
            if (TRACE) out.println("No inclusion edges for "+from+", returning.");
            if (TRACK_CHANGES) {
                cacheIsCurrent.put(from, Boolean.FALSE);
                this.temp_change = false;
            } else if (REUSE_CACHES) {
                cacheIsCurrent.put(from, from);
            }
            return result;
        }
        p = new Path(from, p);
        boolean local_change = false;
        for (;;) {
            Iterator i = s.iterator();
            for (;;) {
                if (!i.hasNext()) {
                    if (TRACE) out.println("Finishing exploring "+from+", change in cache="+local_change+", cache="+result);
                    if (REUSE_CACHES) {
                        if (TRACK_CHANGES) {
                            cacheIsCurrent.put(from, local_change?Boolean.TRUE:Boolean.FALSE);
                            this.temp_change = local_change;
                        } else {
                            cacheIsCurrent.put(from, from);
                        }
                    }
                    return result;
                }
                Node to = (Node)i.next();
                if (to instanceof OutsideNode) {
                    if (TRACE) out.println("Visiting inclusion edge "+from+" --> "+to+"...");
                    from.visited = true;
                    Set s2 = getConcreteNodes((OutsideNode)to, p);
                    from.visited = false;
                    if (from.skip != null) {
                        if (TRACE) out.println("Node "+from+" is skipped...");
                        return null;
                    }
                    if (s2 == null) {
                        if (TRACE) out.println("Nodes were merged into "+from);
                        if (TRACE) out.println("redoing iteration on "+s);
                        if (TRACK_CHANGES) brand_new = true; // we have new children, so always union them.
                        if (VerifyAssertions) Assert._assert(nodeToInclusionEdges.get(from) == s);
                        break;
                    } else {
                        if (TRACK_CHANGES) {
                            boolean b = removeUnpropagatedEdge(from, (OutsideNode)to);
                            if (!brand_new && !b && !this.temp_change) {
                                if (TRACE) out.println("No change in cache of target "+to+", skipping union operation");
                                if (VerifyAssertions) Assert._assert(result.containsAll(s2), from+" result "+result+" should contain all of "+to+" result "+s2);
                                continue;
                            }
                        }
                        boolean change_from_union;
                        if (USE_SET_REPOSITORY) {
                            Set result2 = ((SetRepository.SharedSet)result).copyAndAddAll(s2, false);
                            if (result != result2) {
                                change_from_union = true;
                                addToCache(from, result = result2);
                            } else {
                                change_from_union = false;
                            }
                        } else {
                            change_from_union = result.addAll(s2);
                        }
                        if (change_from_union) {
                            if (TRACE) out.println("Unioning cache of target "+to+" changed our cache");
                            local_change = true;
                        }
                    }
                } else {
                    boolean change_from_add;
                    if (USE_SET_REPOSITORY) {
                        Set result2 = ((SetRepository.SharedSet)result).copyAndAddAll(Collections.singleton(to), false);
                        if (result != result2) {
                            change_from_add = true;
                            addToCache(from, result = result2);
                        } else {
                            change_from_add = false;
                        }
                    } else {
                        change_from_add = result.add(to);
                    }
                    if (change_from_add) {
                        if (TRACE) out.println("Adding concrete node "+to+" changed our cache");
                        local_change = true;
                    }
                }
            }
        }
    }
    
    void recordUnpropagatedEdge(OutsideNode from, OutsideNode to) {
        unpropagatedEdges.add(new Pair(from, to));
    }
    boolean removeUnpropagatedEdge(OutsideNode from, OutsideNode to) {
        if (unpropagatedEdges.remove(getPair(from, to))) return true;
        Set s = (Set)collapsedNodes.get(to);
        if (s == null) return false;
        if (s.contains(from)) return false;
        s.add(from);
        return true;
    }
    void markCollapsedNode(OutsideNode n) {
        Set s = (Set)collapsedNodes.get(n);
        if (s == null) collapsedNodes.put(n, s = new HashSet());
        else s.clear();
    }
    
    final Pair my_pair_list = new Pair(null, null);
    public Pair getPair(Object left, Object right) {
        my_pair_list.left = left; my_pair_list.right = right; return my_pair_list;
    }
    
    public static class Path {
        private final OutsideNode s;
        private final Path next;
        Path(OutsideNode s, Path n) { this.s = s; this.next = n; }
        OutsideNode car() { return s; }
        Path cdr() { return next; }
        public String toString() {
            if (next == null) return s.toString();
            return s.toString()+"->"+next.toString();
        }
    }
    
    public static class AndersenCallGraph extends CallGraph {

        private final Map callSiteToTargets;
        private final Set roots;
        private Set allMethods;
        
        public AndersenCallGraph(Map m, Set s) {
            callSiteToTargets = new HashMap();
            roots = new HashSet();
            for (Iterator i = s.iterator(); i.hasNext(); ) {
                MethodSummary ms = (MethodSummary) i.next();
                roots.add(ms.getMethod());
            }
            for (Iterator i = m.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry e = (Map.Entry) i.next();
                CallSite cs = (CallSite) e.getKey();
                Set callees = (Set) e.getValue();
                Object old = callSiteToTargets.put(cs.m, callees);
                if (old != null) callees.addAll((Set) old);
            }
        }

        private Set calculateAllMethods() {
            Set allMethods = new HashSet();
            allMethods.addAll(roots);
            for (Iterator i = callSiteToTargets.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry e = (Map.Entry) i.next();
                Set callees = (Set) e.getValue();
                allMethods.addAll(callees);
            }
            return allMethods;
        }

        /* (non-Javadoc)
         * @see Compil3r.Quad.CallGraph#getTargetMethods(java.lang.Object, Compil3r.Quad.ProgramLocation)
         */
        public Collection getTargetMethods(Object context, ProgramLocation callSite) {
            return (Collection) callSiteToTargets.get(callSite);
        }

        /* (non-Javadoc)
         * @see Compil3r.Quad.CallGraph#setRoots(java.util.Collection)
         */
        public void setRoots(Collection roots) {
            throw new UnsupportedOperationException();
        }

        /* (non-Javadoc)
         * @see Compil3r.Quad.CallGraph#getRoots()
         */
        public Collection getRoots() {
            return roots;
        }

        /* (non-Javadoc)
         * @see Compil3r.Quad.CallGraph#getAllMethods()
         */
        public Collection getAllMethods() {
            if (allMethods == null) allMethods = calculateAllMethods();
            return allMethods;
        }
        
    }
    
    public CallGraph getCallGraph() { return new AndersenCallGraph(callSiteToTargets, rootSet); }
    
}
