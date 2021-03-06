// ReflectiveInterpreter.java, created Mon Feb  5 23:23:21 2001 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Interpreter;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

import Bootstrap.PrimordialClassLoader;
import Clazz.jq_Array;
import Clazz.jq_Class;
import Clazz.jq_Initializer;
import Clazz.jq_Method;
import Clazz.jq_Primitive;
import Clazz.jq_StaticMethod;
import Clazz.jq_Type;
import Main.HostedVM;
import Run_Time.Reflection;
import Run_Time.Unsafe;
import UTF.Utf8;
import Util.Assert;

/**
 * @author  John Whaley <jwhaley@alum.mit.edu>
 * @version $Id: ReflectiveInterpreter.java,v 1.19 2003/05/12 10:05:17 joewhaley Exp $
 */
public class ReflectiveInterpreter extends BytecodeInterpreter {

    /** Creates new ReflectiveInterpreter */
    public ReflectiveInterpreter(State initialState) {
        super(new ReflectiveVMInterface(), initialState);
    }

    public Object invokeReflective(jq_Method m) throws Throwable {
        //System.out.println("Invoking reflectively: "+m);
        jq_Class t = m.getDeclaringClass();
        Assert._assert(t.isClsInitialized());
        Class c = Reflection.getJDKType(t);
        jq_Type[] param_jq = m.getParamTypes();
        int offset = 0;
        if (!m.isStatic()) offset = 1;
        Class[] param_jdk = new Class[param_jq.length-offset];
        Object[] param = new Object[param_jq.length-offset];
        for (int i=param_jq.length-1; i>=offset; --i) {
            Class pc = param_jdk[i-offset] = Reflection.getJDKType(param_jq[i]);
            if (pc.isPrimitive()) {
                if (pc == Integer.TYPE) param[i-offset] = new Integer(state.pop_I());
                else if (pc == Long.TYPE) param[i-offset] = new Long(state.pop_L());
                else if (pc == Float.TYPE) param[i-offset] = new Float(state.pop_F());
                else if (pc == Double.TYPE) param[i-offset] = new Double(state.pop_D());
                else if (pc == Byte.TYPE) param[i-offset] = new Byte((byte)state.pop_I());
                else if (pc == Short.TYPE) param[i-offset] = new Short((short)state.pop_I());
                else if (pc == Character.TYPE) param[i-offset] = new Character((char)state.pop_I());
                else if (pc == Boolean.TYPE) param[i-offset] = new Boolean(state.pop_I()!=0);
                else Assert.UNREACHABLE(pc.toString());
            } else {
                param[i-offset] = state.pop_A();
            }
        }
        try {
            if (m instanceof jq_Initializer) {
                Constructor co = c.getDeclaredConstructor(param_jdk);
                co.setAccessible(true);
                UninitializedType u = (UninitializedType)state.pop_A();
                Assert._assert(u.k == m.getDeclaringClass());
                Object inited = co.newInstance(param);
                ((ReflectiveState)state).replaceUninitializedReferences(inited, u);
                return null;
            }
            Method mr = c.getDeclaredMethod(m.getName().toString(), param_jdk);
            mr.setAccessible(true);
            Object thisptr;
            if (!m.isStatic()) thisptr = state.pop_A();
            else thisptr = null;
            return mr.invoke(thisptr, param);
        } catch (NoSuchMethodException x) {
            Assert.UNREACHABLE("host jdk does not contain method "+m);
        } catch (InstantiationException x) {
            Assert.UNREACHABLE();
        } catch (IllegalAccessException x) {
            Assert.UNREACHABLE();
        } catch (IllegalArgumentException x) {
            Assert.UNREACHABLE();
        } catch (InvocationTargetException x) {
            throw new WrappedException(x.getTargetException());
        }
        return null;
    }
    static HashSet cantInterpret = new HashSet();
    static {
        jq_Class k = (jq_Class)PrimordialClassLoader.loader.getOrCreateBSType("Ljava/io/PrintStream;");
        jq_Method m = k.getOrCreateInstanceMethod("write", "(Ljava/lang/String;)V");
        cantInterpret.add(m);
    }
    public Object invokeMethod(jq_Method m) throws Throwable {
        if (cantInterpret.contains(m)) {
            return invokeReflective(m);
        }
        if (m.isNative() || m instanceof jq_Initializer) {
            return invokeReflective(m);
        } else {
            ReflectiveState callee = new ReflectiveState(m);
            try {
                return this.invokeMethod(m, callee);
            } catch (MonitorExit x) {
                Assert._assert(m.isSynchronized());
                Assert._assert(state != callee);
                return callee.getReturnVal_A();
            }
        }
    }
    public Object invokeUnsafeMethod(jq_Method f) throws Throwable {
        if (f == Unsafe._floatToIntBits) {
            return new Integer(Float.floatToRawIntBits(state.pop_F()));
        } else if (f == Unsafe._intBitsToFloat) {
            return new Float(Float.intBitsToFloat(state.pop_I()));
        } else if (f == Unsafe._doubleToLongBits) {
            return new Long(Double.doubleToRawLongBits(state.pop_D()));
        } else if (f == Unsafe._longBitsToDouble) {
            return new Double(Double.longBitsToDouble(state.pop_L()));
        } else {
            return invokeReflective(f);
        }
    }
    
    public static class ReflectiveState extends BytecodeInterpreter.State {
        final Object[] locals;
        final Object[] stack;
        final jq_Method m;
        int sp;
        Object result;
        
        public ReflectiveState(Object[] incoming_args) {
            this.m = null;
            this.locals = new Object[0];
            this.stack = incoming_args;
            this.sp = incoming_args.length;
        }
        
        public ReflectiveState(jq_Method m) {
            //System.out.println("Initializing state: "+m.getMaxLocals()+" locals and "+m.getMaxStack()+" stack");
            this.m = m;
            this.locals = new Object[m.getMaxLocals()];
            this.stack = new Object[m.getMaxStack()];
            this.sp = 0;
        }

        public void push_I(int v) { stack[sp++] = new Integer(v); }
        public void push_L(long v) { stack[sp++] = new Long(v); stack[sp++] = null; }
        public void push_F(float v) { stack[sp++] = new Float(v); }
        public void push_D(double v) { stack[sp++] = new Double(v); stack[sp++] = null; }
        public void push_A(Object v) { stack[sp++] = v; }
        public void push(Object v) { stack[sp++] = v; }
        public int pop_I() { return ((Integer)stack[--sp]).intValue(); }
        public long pop_L() { --sp; return ((Long)stack[--sp]).longValue(); }
        public float pop_F() { return ((Float)stack[--sp]).floatValue(); }
        public double pop_D() { --sp; return ((Double)stack[--sp]).doubleValue(); }
        public Object pop_A() { return stack[--sp]; }
        public Object pop() { return stack[--sp]; }
        public void popAll() { sp = 0; }
        public Object peek_A(int depth) { return stack[sp-depth-1]; }
        public void setLocal_I(int i, int v) { locals[i] = new Integer(v); }
        public void setLocal_L(int i, long v) { locals[i] = new Long(v); }
        public void setLocal_F(int i, float v) { locals[i] = new Float(v); }
        public void setLocal_D(int i, double v) { locals[i] = new Double(v); }
        public void setLocal_A(int i, Object v) { locals[i] = v; }
        public int getLocal_I(int i) { return ((Integer)locals[i]).intValue(); }
        public long getLocal_L(int i) { return ((Long)locals[i]).longValue(); }
        public float getLocal_F(int i) { return ((Float)locals[i]).floatValue(); }
        public double getLocal_D(int i) { return ((Double)locals[i]).doubleValue(); }
        public Object getLocal_A(int i) { return locals[i]; }
        public void return_I(int v) { result = new Integer(v); }
        public void return_L(long v) { result = new Long(v); }
        public void return_F(float v) { result = new Float(v); }
        public void return_D(double v) { result = new Double(v); }
        public void return_A(Object v) { result = v; }
        public void return_V() {}
        public int getReturnVal_I() { return ((Integer)result).intValue(); }
        public long getReturnVal_L() { return ((Long)result).longValue(); }
        public float getReturnVal_F() { return ((Float)result).floatValue(); }
        public double getReturnVal_D() { return ((Double)result).doubleValue(); }
        public Object getReturnVal_A() { return result; }
        
        void replaceUninitializedReferences(Object o, UninitializedType u) {
            int p = sp;
            while (--p >= 0) {
                if (stack[p] == u) stack[p] = o;
            }
            for (p=0; p<locals.length; ++p) {
                if (locals[p] == u) locals[p] = o;
            }
        }
    }
    
    static class UninitializedType {
        jq_Class k;
        UninitializedType(jq_Class k) { this.k = k; }
    }
    
    public static class ReflectiveVMInterface extends BytecodeInterpreter.VMInterface {
        //ObjectTraverser ot;
        ReflectiveVMInterface() {
            //ot = new ObjectTraverser.Empty();
        }
        public static final ReflectiveVMInterface INSTANCE = new ReflectiveVMInterface();
        public Object new_obj(jq_Type t) {
            t.cls_initialize();
            return new UninitializedType((jq_Class) t);
        }
        public Object new_array(jq_Type t, int length) {
            t.cls_initialize();
            return Array.newInstance(Reflection.getJDKType(((jq_Array)t).getElementType()), length);
        }
        public Object checkcast(Object o, jq_Type t) {
            if (o == null) return o;
            if (!Reflection.getJDKType(t).isAssignableFrom(o.getClass()))
                throw new ClassCastException();
            return o;
        }
        public boolean instance_of(Object o, jq_Type t) {
            if (o == null) return false;
            return Reflection.getJDKType(t).isAssignableFrom(o.getClass());
        }
        public int arraylength(Object o) {
            return Array.getLength(o);
        }
        public void monitorenter(Object o, MethodInterpreter v) {
            synchronized (o) {
                try {
                    v.continueForwardTraversal();
                } catch (MonitorExit x) {
                    Assert._assert(x.o == o, "synchronization blocks are not nested!");
                    return;
                } catch (WrappedException ix) {
                    // if the method throws an exception, the object will automatically be unlocked
                    // when we exit this synchronized block.
                    throw ix;
                }
                // method exit
            }
        }
        public void monitorexit(Object o) {
            throw new MonitorExit(o);
        }
        public Object multinewarray(int[] dims, jq_Type t) {
            for (int i=0; i<dims.length; ++i) {
                t.cls_initialize();
                t = ((jq_Array)t).getElementType();
            }
            return Array.newInstance(Reflection.getJDKType(t), dims);
        }
        
    }

    static class MonitorExit extends RuntimeException {
        Object o;
        MonitorExit(Object o) { this.o = o; }
    }
    
    // Invoke reflective interpreter from command line.
    public static void main(String[] s_args) throws Throwable {
        String s = s_args[0];
        int dotloc = s.lastIndexOf('.');
        String rootMethodClassName = s.substring(0, dotloc);
        String rootMethodName = s.substring(dotloc+1);
        
	HostedVM.initialize();
        
        jq_Class c = (jq_Class)PrimordialClassLoader.loader.getOrCreateBSType("L"+rootMethodClassName.replace('.','/')+";");
        c.cls_initialize();

        jq_StaticMethod rootm = null;
        Utf8 rootm_name = Utf8.get(rootMethodName);
        for(Iterator it = Arrays.asList(c.getDeclaredStaticMethods()).iterator();
            it.hasNext(); ) {
            jq_StaticMethod m = (jq_StaticMethod)it.next();
            if (m.getName() == rootm_name) {
                rootm = m;
                break;
            }
        }
        if (rootm == null)
            Assert.UNREACHABLE("root method not found: "+rootMethodClassName+"."+rootMethodName);
        Object[] args = parseMethodArgs(rootm.getParamWords(), rootm.getParamTypes(), s_args, 0);
        ReflectiveState initialState = new ReflectiveState(args);
        Object retval = new ReflectiveInterpreter(initialState).invokeMethod(rootm);
        System.out.println("Return value: "+retval);
    }
    
    public static Object[] parseMethodArgs(int argsSize, jq_Type[] paramTypes, String[] s_args, int j) {
        Object[] args = new Object[argsSize];
        try {
            for (int i=0, m=0; i<paramTypes.length; ++i, ++m) {
                if (paramTypes[i] == PrimordialClassLoader.getJavaLangString())
                    args[m] = s_args[++j];
                else if (paramTypes[i] == jq_Primitive.BOOLEAN)
                    args[m] = Boolean.valueOf(s_args[++j]);
                else if (paramTypes[i] == jq_Primitive.BYTE)
                    args[m] = Byte.valueOf(s_args[++j]);
                else if (paramTypes[i] == jq_Primitive.SHORT)
                    args[m] = Short.valueOf(s_args[++j]);
                else if (paramTypes[i] == jq_Primitive.CHAR)
                    args[m] = new Character(s_args[++j].charAt(0));
                else if (paramTypes[i] == jq_Primitive.INT)
                    args[m] = Integer.valueOf(s_args[++j]);
                else if (paramTypes[i] == jq_Primitive.LONG) {
                    args[m] = Long.valueOf(s_args[++j]);
                    if (argsSize != paramTypes.length) ++m;
                } else if (paramTypes[i] == jq_Primitive.FLOAT)
                    args[m] = Float.valueOf(s_args[++j]);
                else if (paramTypes[i] == jq_Primitive.DOUBLE) {
                    args[m] = Double.valueOf(s_args[++j]);
                    if (argsSize != paramTypes.length) ++m;
                } else if (paramTypes[i].isArrayType()) {
                    if (!s_args[++j].equals("{")) 
                        Assert.UNREACHABLE("array parameter doesn't start with {");
                    int count=0;
                    while (!s_args[++j].equals("}")) ++count;
                    jq_Type elementType = ((jq_Array)paramTypes[i]).getElementType();
                    if (elementType == PrimordialClassLoader.getJavaLangString()) {
                        String[] array = new String[count];
                        for (int k=0; k<count; ++k)
                            array[k] = s_args[j-count+k];
                        args[m] = array;
                    } else if (elementType == jq_Primitive.BOOLEAN) {
                        boolean[] array = new boolean[count];
                        for (int k=0; k<count; ++k)
                            array[k] = Boolean.valueOf(s_args[j-count+k]).booleanValue();
                        args[m] = array;
                    } else if (elementType == jq_Primitive.BYTE) {
                        byte[] array = new byte[count];
                        for (int k=0; k<count; ++k)
                            array[k] = Byte.parseByte(s_args[j-count+k]);
                        args[m] = array;
                    } else if (elementType == jq_Primitive.SHORT) {
                        short[] array = new short[count];
                        for (int k=0; k<count; ++k)
                            array[k] = Short.parseShort(s_args[j-count+k]);
                        args[m] = array;
                    } else if (elementType == jq_Primitive.CHAR) {
                        char[] array = new char[count];
                        for (int k=0; k<count; ++k)
                            array[k] = s_args[j-count+k].charAt(0);
                        args[m] = array;
                    } else if (elementType == jq_Primitive.INT) {
                        int[] array = new int[count];
                        for (int k=0; k<count; ++k)
                            array[k] = Integer.parseInt(s_args[j-count+k]);
                        args[m] = array;
                    } else if (elementType == jq_Primitive.LONG) {
                        long[] array = new long[count];
                        for (int k=0; k<count; ++k)
                            array[k] = Long.parseLong(s_args[j-count+k]);
                        args[m] = array;
                    } else if (elementType == jq_Primitive.FLOAT) {
                        float[] array = new float[count];
                        for (int k=0; k<count; ++k)
                            array[k] = Float.parseFloat(s_args[j-count+k]);
                        args[m] = array;
                    } else if (elementType == jq_Primitive.DOUBLE) {
                        double[] array = new double[count];
                        for (int k=0; k<count; ++k)
                            array[k] = Double.parseDouble(s_args[j-count+k]);
                        args[m] = array;
                    } else
                        Assert.UNREACHABLE("Parsing an argument of type "+paramTypes[i]+" is not implemented");
                } else
                    Assert.UNREACHABLE("Parsing an argument of type "+paramTypes[i]+" is not implemented");
            }
        } catch (ArrayIndexOutOfBoundsException x) {
            x.printStackTrace();
            Assert.UNREACHABLE("not enough method arguments");
        }
        return args;
    }

}
