// Handshake.java, created Tue Dec 10 14:02:30 2002 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package Memory.Manager;

import Run_Time.Debug;
import Run_Time.HighResolutionTimer;
import Run_Time.SystemInterface;
import Scheduler.jq_NativeThread;

/**
 * @author John Whaley <jwhaley@alum.mit.edu>
 * @version $Id: Handshake.java,v 1.4 2003/05/12 10:05:19 joewhaley Exp $
 */
public class Handshake {

    static final int LOCKOUT_GC_WORD = 0x0CCCCCCC;

    protected boolean requestFlag;
    protected boolean completionFlag;

    /**
     * Initiates a garbage collection.  Called from requestAndAwaitCompletion
     * by the first mutator thread to request a collection using the
     * current VM_Handshake object.
     *
     * The sequence of events that start a collection is initiated by the
     * calling mutator, and it then yields, waiting for the collection
     * to complete.
     *
     * While mutators are executing, all the GC threads (VM_CollectorThreads)
     * reside on a single system queue, VM_Scheduler.collectorQueue.  This
     * method determines which processors will participate in the collection,
     * dequeues the GC threads associated with those processors, and
     * schedules them for executing on their respective processors.
     * (Most of the time, all processors and thus all GC threads participate,
     * but this is changing as the RVM thread startegy changes.)
     *
     * The collection actually starts when all participating GC threads
     * arrive at the first rendezvous in VM_CollectorThreads run method,
     * and suspend thread switching on their processors.
     *
     * While collection is in progress, mutators are not explicitly waiting
     * for the collection. They reside in the thread dispatch queues of their
     * processors, until the collector threads re-enable thread switching.
     */
    private void initiateCollection() {
        int maxCollectorThreads;

        // check that scheduler initialization is complete
        //  
        if (!jq_NativeThread.allNativeThreadsInitialized()) {
            Debug.write(
                " Garbage collection required before system fully initialized\n");
            Debug.write(
                " Specify larger than default heapsize on command line\n");
            jq_NativeThread.dumpAllThreads();
            SystemInterface.die(-1);
        }

        // wait for all gc threads to finish preceeding collection cycle
        // TODO.

        maxCollectorThreads = jq_NativeThread.native_threads.length;
        
        // Acquire global lockout field (at fixed address in the boot record).
        // This field will be released when gc completes
        // TODO.

        // reset counter for collector threads arriving to participate in the collection
        CollectorThread.participantCount.reset(0);

        // reset rendezvous counters to 0, the decision about which collector threads
        // will participate has moved to the run method of CollectorThread
        //
        CollectorThread.gcBarrier.resetRendezvous();

        // scan all the native Virtual Processors and attempt to block those executing
        // native C, to prevent returning to java during collection.  the first collector
        // thread will wait for those not blocked to reach the SIGWAIT state.
        // (see VM_CollectorThread.run)
        //

        // Dequeue and schedule collector threads on ALL RVM Processors,
        // including those running system daemon threads (ex. NativeDaemonProcessor)
        //

    } // initiateCollection

    /**
     * Called by mutators to request a garbage collection and wait
     * for it to complete.
     * If the completionFlag is already set, return immediately.
     * Else, if the requestFlag is not yet set (ie this is the
     * first mutator to request this collection) then initiate
     * the collection sequence & then wait for it to complete.
     * Else (it has already started) just wait for it to complete.
     *
     * Waiting is actually just yielding the processor to schedule
     * the collector thread, which will disable further thread
     * switching on the processor until it has completed the
     * collection.
     */
    public void requestAndAwaitCompletion() {
        synchronized (this) {
            if (completionFlag) {
                return;
            }
            if (requestFlag) {
            } else {
                // first mutator initiates collection by making all gc threads runnable at high priority
                SynchronizationBarrier.rendezvousStartTime =
                    HighResolutionTimer.now();
                requestFlag = true;
                initiateCollection();
            }
        } // end of synchronized block

        // allow a gc thread to run
        Thread.yield();

    }

    /**
     * Set the completion flag that indicates the collection has completed.
     * Called by a collector thread after the collection has completed.
     * It currently does not do a "notify" on waiting mutator threads,
     * since they are in VM_Processor thread queues, waiting
     * for the collector thread to re-enable thread switching.
     *
     * @see VM_CollectorThread
     */
    synchronized void notifyCompletion() {
        completionFlag = true;
    }

    /**
     * Acquire LockoutLock.  Accepts a value to store into the lockoutlock
     * word when it becomes available (value == 0). If not available when
     * called, a passed flag indicates either spinning or yielding until
     * the word becomes available.
     *
     * @param value    Value to store into lockoutlock word
     * @param spinwait flag to cause spinning (if true) or yielding
     */
    public static void acquireLockoutLock(int value, boolean spinwait) {
        if (spinwait) {
            // TODO
        }

        // else - no spinwait:
        // yield until lockout word is available (0), then attempt to set

        // TODO
    }

    /**
     * Release the LockoutLock by setting the lockoutlock word to 0.
     * The expected current value that should be in the word can be
     * passed in, and is verified if not 0.
     *
     * @param value    Value that should currently be in the lockoutlock word
     */
    public static void releaseLockoutLock(int value) {
        // TODO
    }

    /**
     * Return the current contents of the lockoutLock word
     *
     * @return  current lockoutLock word
     */
    public static int queryLockoutLock() {
        // TODO
        return 0;
    }

}
