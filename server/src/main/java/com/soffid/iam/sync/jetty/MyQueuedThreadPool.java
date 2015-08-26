package com.soffid.iam.sync.jetty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.mortbay.component.AbstractLifeCycle;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.log.Log;
import org.mortbay.thread.ThreadPool;

/* ------------------------------------------------------------ */
/**
 * A pool of threads.
 * <p>
 * Avoids the expense of thread creation by pooling threads after their run
 * methods exit for reuse.
 * <p>
 * If the maximum pool size is reached, jobs wait for a free thread. By default
 * there is no maximum pool size. Idle threads timeout and terminate until the
 * minimum number of threads are running.
 * <p>
 * 
 * @author Greg Wilkins <gregw@mortbay.com>
 * @author Juancarlo Anez <juancarlo@modelistica.com>
 */
public class MyQueuedThreadPool extends AbstractLifeCycle implements
        Serializable, ThreadPool {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static int __id;

    private String _name;
    private Set _threads;
    private List _idle;
    private Runnable[] _jobs;
    private int _nextJob;
    private int _nextJobSlot;
    private int _queued;

    private boolean _daemon;
    private int _id;

    private final Object _threadLock = new Lock();
    private final Object _idleLock = new Lock();
    private final Object _jobsLock = new Lock();
    private final Object _joinLock = new Lock();

    private long _lastShrink;
    private int _maxIdleTimeMs = 60000;
    private int _maxThreads = 25;
    private int _minThreads = 2;
    private boolean _warned = false;
    private int _lowThreads = 0;
    private int _priority = Thread.NORM_PRIORITY;
    private int _spawnOrShrinkAt = 0;

    /* ------------------------------------------------------------------- */
    /*
     * Construct
     */
    public MyQueuedThreadPool() {
        _name = "btpool" + __id++;
    }

    /* ------------------------------------------------------------ */
    /**
     * Run job.
     * 
     * @return true
     */
    public boolean dispatch(Runnable job) {
        if (!isRunning() || job == null)
            return false;

        PoolThread thread = null;
        boolean spawn = false;

        // Look for an idle thread
        synchronized (_idleLock) {
            int idle = _idle.size();
            if (idle > 0)
                thread = (PoolThread) _idle.remove(idle - 1);
            else {
                // Are we at max size?
                if (_threads.size() < _maxThreads)
                    spawn = true;
                else {
                    if (!_warned) {
                        _warned = true;
                        Log.debug("Out of threads for {}", this);
                    }
                }
            }
        }

        if (thread != null)
            thread.dispatch(job);
        else {
            synchronized (_jobsLock) {
                _queued++;
                _jobs[_nextJobSlot++] = job;
                if (_nextJobSlot == _jobs.length)
                    _nextJobSlot = 0;
                if (_nextJobSlot == _nextJob) {
                    // Grow the job queue
                    Runnable[] jobs = new Runnable[_jobs.length + _maxThreads];
                    int split = _jobs.length - _nextJob;
                    if (split > 0)
                        System.arraycopy(_jobs, _nextJob, jobs, 0, split);
                    if (_nextJob != 0)
                        System.arraycopy(_jobs, 0, jobs, split, _nextJobSlot);

                    _jobs = jobs;
                    _nextJob = 0;
                    _nextJobSlot = _queued;
                }

                if (spawn && _queued <= _spawnOrShrinkAt)
                    spawn = false;
            }

            if (spawn)
                newThread();
        }

        return true;
    }

    /* ------------------------------------------------------------ */
    /**
     * Get the number of idle threads in the pool.
     * 
     * @see #getThreads
     * @return Number of threads
     */
    public int getIdleThreads() {
        return _idle == null ? 0 : _idle.size();
    }

    /* ------------------------------------------------------------ */
    /**
     * @return low resource threads threshhold
     */
    public int getLowThreads() {
        return _lowThreads;
    }

    /* ------------------------------------------------------------ */
    /**
     * Get the maximum thread idle time. Delegated to the named or anonymous
     * Pool.
     * 
     * @see #setMaxIdleTimeMs
     * @return Max idle time in ms.
     */
    public int getMaxIdleTimeMs() {
        return _maxIdleTimeMs;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the maximum number of threads. Delegated to the named or anonymous
     * Pool.
     * 
     * @see #setMaxThreads
     * @return maximum number of threads.
     */
    public int getMaxThreads() {
        return _maxThreads;
    }

    /* ------------------------------------------------------------ */
    /**
     * Get the minimum number of threads. Delegated to the named or anonymous
     * Pool.
     * 
     * @see #setMinThreads
     * @return minimum number of threads.
     */
    public int getMinThreads() {
        return _minThreads;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return The name of the BoundedThreadPool.
     */
    public String getName() {
        return _name;
    }

    /* ------------------------------------------------------------ */
    /**
     * Get the number of threads in the pool.
     * 
     * @see #getIdleThreads
     * @return Number of threads
     */
    public int getThreads() {
        return _threads.size();
    }

    /* ------------------------------------------------------------ */
    /**
     * Get the priority of the pool threads.
     * 
     * @return the priority of the pool threads.
     */
    public int getThreadsPriority() {
        return _priority;
    }

    /* ------------------------------------------------------------ */
    public int getQueueSize() {
        return _queued;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return the spawnOrShrinkAt The number of queued jobs (or idle threads)
     *         needed before the thread pool is grown (or shrunk)
     */
    public int getSpawnOrShrinkAt() {
        return _spawnOrShrinkAt;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param spawnOrShrinkAt
     *            The number of queued jobs (or idle threads) needed before the
     *            thread pool is grown (or shrunk)
     */
    public void setSpawnOrShrinkAt(int spawnOrShrinkAt) {
        _spawnOrShrinkAt = spawnOrShrinkAt;
    }

    /* ------------------------------------------------------------ */
    /**
     * Delegated to the named or anonymous Pool.
     */
    public boolean isDaemon() {
        return _daemon;
    }

    /* ------------------------------------------------------------ */
    public boolean isLowOnThreads() {
        return _queued > _lowThreads;
    }

    /* ------------------------------------------------------------ */
    public void join() throws InterruptedException {
        synchronized (_joinLock) {
            while (isRunning())
                _joinLock.wait();
        }

        while (isStopping())
            Thread.sleep(100);
    }

    /* ------------------------------------------------------------ */
    /**
     * Delegated to the named or anonymous Pool.
     */
    public void setDaemon(boolean daemon) {
        _daemon = daemon;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param lowThreads
     *            low resource threads threshhold
     */
    public void setLowThreads(int lowThreads) {
        _lowThreads = lowThreads;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the maximum thread idle time. Threads that are idle for longer than
     * this period may be stopped. Delegated to the named or anonymous Pool.
     * 
     * @see #getMaxIdleTimeMs
     * @param maxIdleTimeMs
     *            Max idle time in ms.
     */
    public void setMaxIdleTimeMs(int maxIdleTimeMs) {
        _maxIdleTimeMs = maxIdleTimeMs;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the maximum number of threads. Delegated to the named or anonymous
     * Pool.
     * 
     * @see #getMaxThreads
     * @param maxThreads
     *            maximum number of threads.
     */
    public void setMaxThreads(int maxThreads) {
        if (isStarted() && maxThreads < _minThreads)
            throw new IllegalArgumentException("!minThreads<maxThreads");
        _maxThreads = maxThreads;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the minimum number of threads. Delegated to the named or anonymous
     * Pool.
     * 
     * @see #getMinThreads
     * @param minThreads
     *            minimum number of threads
     */
    public void setMinThreads(int minThreads) {
        if (isStarted() && (minThreads <= 0 || minThreads > _maxThreads))
            throw new IllegalArgumentException("!0<=minThreads<maxThreads");
        _minThreads = minThreads;
        synchronized (_threadLock) {
            while (isStarted() && _threads.size() < _minThreads) {
                newThread();
            }
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * @param name
     *            Name of the BoundedThreadPool to use when naming Threads.
     */
    public void setName(String name) {
        _name = name;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set the priority of the pool threads.
     * 
     * @param priority
     *            the new thread priority.
     */
    public void setThreadsPriority(int priority) {
        _priority = priority;
    }

    /* ------------------------------------------------------------ */
    /*
     * Start the BoundedThreadPool. Construct the minimum number of threads.
     */
    protected void doStart() throws Exception {
        if (_maxThreads < _minThreads || _minThreads <= 0)
            throw new IllegalArgumentException("!0<minThreads<maxThreads");

        _threads = new HashSet();
        _idle = new ArrayList();
        _jobs = new Runnable[_maxThreads];

        for (int i = 0; i < _minThreads; i++) {
            newThread();
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * Stop the BoundedThreadPool. New jobs are no longer accepted,idle threads
     * are interrupted and stopJob is called on active threads. The method then
     * waits min(getMaxStopTimeMs(),getMaxIdleTimeMs()), for all jobs to stop,
     * at which time killJob is called.
     */
    protected void doStop() throws Exception {
        super.doStop();

        for (int i = 0; i < 100; i++) {
            synchronized (_threadLock) {
                Iterator iter = _threads.iterator();
                while (iter.hasNext())
                    ((Thread) iter.next()).interrupt();
            }

            Thread.yield();
            if (_threads.size() == 0)
                break;

            try {
                Thread.sleep(i * 100);
            } catch (InterruptedException e) {
            }
        }

        // TODO perhaps force stops
        if (_threads.size() > 0)
            Log.warn(_threads.size() + " threads could not be stopped");

        synchronized (_joinLock) {
            _joinLock.notifyAll();
        }
    }

    /* ------------------------------------------------------------ */
    protected void newThread() {
        synchronized (_threadLock) {
            PoolThread thread = new PoolThread();
            _threads.add(thread);
            thread.setName(_name + "-" + _id++);
            thread.start();
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * Stop a Job. This method is called by the Pool if a job needs to be
     * stopped. The default implementation does nothing and should be extended
     * by a derived thread pool class if special action is required.
     * 
     * @param thread
     *            The thread allocated to the job, or null if no thread
     *            allocated.
     * @param job
     *            The job object passed to run.
     */
    protected void stopJob(Thread thread, Object job) {
        thread.interrupt();
    }

    /* ------------------------------------------------------------ */
    /**
     * Pool Thread class. The PoolThread allows the threads job to be retrieved
     * and active status to be indicated.
     */
    public class PoolThread extends Thread {
        Runnable _job = null;
        boolean _isIdle = false;
        Runnable _running = null;

        /* ------------------------------------------------------------ */
        PoolThread() {
            setDaemon(_daemon);
            setPriority(_priority);
        }

        /* ------------------------------------------------------------ */
        /**
         * BoundedThreadPool run. Loop getting jobs and handling them until idle
         * or stopped.
         */
        public void run() {
            try {
                Runnable job = null;
                String threadName = Thread.currentThread().getName();

                while (isRunning()) {
                    if (job != null) {
                        _isIdle = false;
                        _running = job;
                        job = null;
                        _running.run();
                        Thread.currentThread().setName(threadName);
                        _running = null;
                    } else {
                        // Look for a job
                        synchronized (_jobsLock) {
                            // is there a queued job?
                            if (_queued > 0) {
                                _queued--;
                                job = _jobs[_nextJob++];
                                if (_nextJob == _jobs.length)
                                    _nextJob = 0;
                                continue;
                            }
                        }

                        synchronized (_idleLock) {
                            _warned = false;

                            // consider shrinking the thread pool
                            if ((_threads.size() > _maxThreads || // we have too
                                                                  // many
                                                                  // threads OR
                            _idle.size() > _spawnOrShrinkAt && // are there idle
                                                               // threads?
                                    _threads.size() > _minThreads)) // AND are
                                                                    // there
                                                                    // more than
                                                                    // min
                                                                    // threads?
                            {
                                // check this thread really is idle
                                _isIdle = _idle.contains(this);

                                long now = System.currentTimeMillis();
                                if (_isIdle
                                        && (now - _lastShrink) > getMaxIdleTimeMs()) {
                                    _lastShrink = now;
                                    return;
                                }
                            }

                            // we are going idle!
                            if (!_isIdle) {
                                _idle.add(this);
                                _isIdle = true;
                            }
                        }

                        // wait for a job
                        try {
                            synchronized (this) {
                                if (_job == null)
                                    this.wait(getMaxIdleTimeMs());
                                job = _job;
                                _job = null;
                            }
                        } catch (InterruptedException e) {
                            Log.ignore(e);
                        }
                    }
                }
            } finally {
                synchronized (_idleLock) {
                    _idle.remove(this);
                }
                synchronized (_threadLock) {
                    _threads.remove(this);
                }

                Runnable job = null;
                synchronized (this) {
                    job = _job;
                }
                // we died with a job! reschedule it
                if (job != null && isRunning())
                    MyQueuedThreadPool.this.dispatch(job);
            }
        }

        /* ------------------------------------------------------------ */
        void dispatch(Runnable job) {
            synchronized (this) {
                if (_job != null || job == null)
                    throw new IllegalStateException();
                _job = job;
                this.notify();
            }
        }
    }

    private class Lock {
    }
    
    public String getStatus ()  {
        StringBuffer b = new StringBuffer();
        b.append ("Running threads:\n");
        for ( Iterator it = _threads.iterator(); it.hasNext(); )
        {
            PoolThread th = (PoolThread) it.next();
            b.append (th.getName()+" ");
            if (th.isAlive() ) {
                Runnable task = th._running;
                if (task == null) {
                    b.append ("Waiting job");
                } else {
                    b.append ("Runinng "+task.toString());
                }
            } else {
                b.append ("Stopped");
            }
            b.append("\n");
        }
        b.append ("Waiting jobs\n");
        int i = _nextJob;
        while ( i != _nextJobSlot) {
            Runnable r = _jobs[i];
            b.append ("Job["+i+"]="+r.toString());
            b.append ("\n");
            i++;
            if (i >= _jobs.length)
                i = 0;
        }
        return b.toString();
    }
}
