// x86ReferenceLinker.java, created Mon Feb  5 23:23:21 2001 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Compil3r.Reference.x86;

import Bootstrap.PrimordialClassLoader;
import Clazz.jq_Class;
import Clazz.jq_InstanceField;
import Clazz.jq_InstanceMethod;
import Clazz.jq_Method;
import Clazz.jq_Reference;
import Clazz.jq_StaticField;
import Clazz.jq_StaticMethod;
import Memory.Address;
import Memory.CodeAddress;
import Memory.HeapAddress;
import Memory.StackAddress;
import Run_Time.SystemInterface;
import Run_Time.Unsafe;
import Util.Assert;

/**
 * @author  John Whaley <jwhaley@alum.mit.edu>
 * @version $Id: x86ReferenceLinker.java,v 1.12 2003/05/12 10:05:16 joewhaley Exp $
 */
public abstract class x86ReferenceLinker {

    public static /*final*/ boolean TRACE = false;
    
    static void patchCaller(jq_Method m, CodeAddress retloc) {
        if (retloc.offset(-6).peek2() == (short)0xE890) {
            // patch static call
            retloc.offset(-4).poke4(m.getDefaultCompiledVersion().getEntrypoint().difference(retloc));
        }
        if (!m.isStatic() && ((jq_InstanceMethod)m).isVirtual()) {
            ((Address[])m.getDeclaringClass().getVTable())[((jq_InstanceMethod)m).getOffset()>>2] = m.getDefaultCompiledVersion().getEntrypoint();
        }
    }
    
    static void getstatic4(jq_StaticField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        k.cls_initialize();
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching getstatic4 "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_getstatic4(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void getstatic8(jq_StaticField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        k.cls_initialize();
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching getstatic8 "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_getstatic8(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void putstatic4(jq_StaticField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        k.cls_initialize();
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching putstatic4 "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_putstatic4(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void putstatic8(jq_StaticField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        k.cls_initialize();
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching putstatic8 "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_putstatic8(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void getfield1(jq_InstanceField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        Assert._assert(k.isClsInitialized());
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching getfield1 "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_getfield1(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void cgetfield(jq_InstanceField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        Assert._assert(k.isClsInitialized());
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching cgetfield "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_cgetfield(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void sgetfield(jq_InstanceField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        Assert._assert(k.isClsInitialized());
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching sgetfield "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_sgetfield(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void getfield4(jq_InstanceField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        Assert._assert(k.isClsInitialized());
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching getfield4 "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_getfield4(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void getfield8(jq_InstanceField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        Assert._assert(k.isClsInitialized());
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching getfield8 "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_getfield8(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void putfield1(jq_InstanceField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        Assert._assert(k.isClsInitialized());
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching putfield1 "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_putfield1(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void putfield2(jq_InstanceField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        Assert._assert(k.isClsInitialized());
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching putfield2 "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_putfield2(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void putfield4(jq_InstanceField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        Assert._assert(k.isClsInitialized());
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching putfield4 "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_putfield4(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void putfield8(jq_InstanceField f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        Assert._assert(k.isClsInitialized());
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching putfield8 "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_putfield8(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void invokevirtual(jq_InstanceMethod f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        Assert._assert(k.isClsInitialized());
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching invokevirtual "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_invokevirtual(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void invokestatic(jq_Method f) {
        f = (jq_Method)f.resolve();
        jq_Class k = f.getDeclaringClass();
        k.cls_initialize();
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching invokestatic "+f+" ip: "+retloc.stringRep());
        int patchsize = x86ReferenceCompiler.patch_invokestatic(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static void invokespecial(jq_InstanceMethod f) {
        f = f.resolve1();
        jq_Class k = f.getDeclaringClass();
        k.cls_initialize();
        f = jq_Class.getInvokespecialTarget(k, f);
        CodeAddress retloc = (CodeAddress) StackAddress.getBasePointer().offset(4).peek();
        if (TRACE) SystemInterface.debugwriteln("backpatching invokespecial "+f+" ip: "+retloc.stringRep());
        // special invocation is now directly bound.
        int patchsize = x86ReferenceCompiler.patch_invokestatic(retloc, f);
        // change our return address to reexecute patched region
        StackAddress.getBasePointer().offset(4).poke(retloc.offset(-patchsize));
    }
    static long invokeinterface(jq_InstanceMethod f) throws Throwable {
        f = f.resolve1();
        int n_paramwords = f.getParamWords();
        StackAddress obj_location = (StackAddress) StackAddress.getBasePointer().offset((n_paramwords+2)<<2);
        Object o = ((HeapAddress) obj_location.peek()).asObject();
        jq_Reference t = jq_Reference.getTypeOf(o);
        if (!t.implementsInterface(f.getDeclaringClass()))
            throw new IncompatibleClassChangeError(t+" does not implement interface "+f.getDeclaringClass());
        jq_InstanceMethod m = t.getVirtualMethod(f.getNameAndDesc());
        if (m == null)
            throw new AbstractMethodError();
        //if (TRACE) SystemInterface.debugwriteln("invokeinterface "+f+" on object type "+t+" resolved to "+m);
        jq_Class k = m.getDeclaringClass();
        k.cls_initialize();
        for (int i=0; i<n_paramwords; ++i) {
            int v = StackAddress.getBasePointer().offset((n_paramwords-i+2)<<2).peek4();
            Unsafe.pushArg(v);
        }
        return Unsafe.invoke(m.getDefaultCompiledVersion().getEntrypoint());
    }
    static void abstractMethodError() throws AbstractMethodError {
        SystemInterface.debugwriteln("Unimplemented abstract method!");
        throw new AbstractMethodError();
    }
    static void nativeMethodError() throws LinkageError {
        SystemInterface.debugwriteln("Unimplemented native method!");
        throw new LinkageError();
    }
    
    public static final jq_Class _class;
    public static final jq_StaticMethod _getstatic4;
    public static final jq_StaticMethod _getstatic8;
    public static final jq_StaticMethod _putstatic4;
    public static final jq_StaticMethod _putstatic8;
    public static final jq_StaticMethod _getfield1;
    public static final jq_StaticMethod _cgetfield;
    public static final jq_StaticMethod _sgetfield;
    public static final jq_StaticMethod _getfield4;
    public static final jq_StaticMethod _getfield8;
    public static final jq_StaticMethod _putfield1;
    public static final jq_StaticMethod _putfield2;
    public static final jq_StaticMethod _putfield4;
    public static final jq_StaticMethod _putfield8;
    public static final jq_StaticMethod _invokevirtual;
    public static final jq_StaticMethod _invokestatic;
    public static final jq_StaticMethod _invokespecial;
    public static final jq_StaticMethod _invokeinterface;
    public static final jq_StaticMethod _abstractMethodError;
    public static final jq_StaticMethod _nativeMethodError;
    static {
        _class = (jq_Class)PrimordialClassLoader.loader.getOrCreateBSType("LCompil3r/Reference/x86/x86ReferenceLinker;");
        _getstatic4 = _class.getOrCreateStaticMethod("getstatic4", "(LClazz/jq_StaticField;)V");
        _getstatic8 = _class.getOrCreateStaticMethod("getstatic8", "(LClazz/jq_StaticField;)V");
        _putstatic4 = _class.getOrCreateStaticMethod("putstatic4", "(LClazz/jq_StaticField;)V");
        _putstatic8 = _class.getOrCreateStaticMethod("putstatic8", "(LClazz/jq_StaticField;)V");
        _getfield1 = _class.getOrCreateStaticMethod("getfield1", "(LClazz/jq_InstanceField;)V");
        _sgetfield = _class.getOrCreateStaticMethod("sgetfield", "(LClazz/jq_InstanceField;)V");
        _cgetfield = _class.getOrCreateStaticMethod("cgetfield", "(LClazz/jq_InstanceField;)V");
        _getfield4 = _class.getOrCreateStaticMethod("getfield4", "(LClazz/jq_InstanceField;)V");
        _getfield8 = _class.getOrCreateStaticMethod("getfield8", "(LClazz/jq_InstanceField;)V");
        _putfield1 = _class.getOrCreateStaticMethod("putfield1", "(LClazz/jq_InstanceField;)V");
        _putfield2 = _class.getOrCreateStaticMethod("putfield2", "(LClazz/jq_InstanceField;)V");
        _putfield4 = _class.getOrCreateStaticMethod("putfield4", "(LClazz/jq_InstanceField;)V");
        _putfield8 = _class.getOrCreateStaticMethod("putfield8", "(LClazz/jq_InstanceField;)V");
        _invokevirtual = _class.getOrCreateStaticMethod("invokevirtual", "(LClazz/jq_InstanceMethod;)V");
        _invokestatic = _class.getOrCreateStaticMethod("invokestatic", "(LClazz/jq_Method;)V");
        _invokespecial = _class.getOrCreateStaticMethod("invokespecial", "(LClazz/jq_InstanceMethod;)V");
        _invokeinterface = _class.getOrCreateStaticMethod("invokeinterface", "(LClazz/jq_InstanceMethod;)J");
        _abstractMethodError = _class.getOrCreateStaticMethod("abstractMethodError", "()V");
        _nativeMethodError = _class.getOrCreateStaticMethod("nativeMethodError", "()V");
    }
}
