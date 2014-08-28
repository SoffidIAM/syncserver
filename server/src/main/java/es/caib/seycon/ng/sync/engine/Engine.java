package es.caib.seycon.ng.sync.engine;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soffid.iam.sync.engine.cron.TaskScheduler;

import es.caib.seycon.ng.config.Config;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.engine.db.ConnectionPool;
import es.caib.seycon.ng.sync.engine.tasks.DispatchersReloaderTask;
import es.caib.seycon.ng.sync.engine.tasks.ExpirePasswordsTask;
import es.caib.seycon.ng.sync.engine.tasks.TimedTaskDaemon;
import es.caib.seycon.ng.sync.servei.TaskGenerator;
import es.caib.seycon.ng.sync.servei.TaskQueue;


public class Engine extends Thread {
    private static Engine theEngine;
    /** cadena describiendo el estao actual del generador de tareas */
    String status;
    /** true si es el servidor primario */
    private boolean enabled;
    private boolean shutDownPending = false;
    private Logger log;
	private TaskGenerator taskGenerator;
	private TaskQueue taskQueue;
	TaskScheduler taskScheduler;


    static public Engine getEngine () {
        if (theEngine == null)
            theEngine = new Engine ();
        return theEngine;
    }
    
    private Engine () {
    }
    
    /**
     * consultar el estado
     * 
     * @return breve texto descriptivo del estado actual
     */
    public String getStatus() {
        return status;
    }

    /**
     * Asignar estado actual del generador de tareas
     * 
     * @param s
     *                breve texto descriptivo
     */
    private void setStatus(String s) {
        status = s;
    }

    @Override
    public void run() {
        setName("Engine");
        log = LoggerFactory.getLogger("Engine");
        
        new UpdateTaskStatusThread().start();
        try {
            int i;
            setStatus("Initializing");
            Config config = Config.getConfig();
            // Un standby server esta disabled
            boolean logCollectorEnabled = config.isActiveServer()
                    && !config.isMainServer();
            enabled = config.isActiveServer() && config.isMainServer();
            sleep(5000);
            
            taskGenerator = ServerServiceLocator.instance().getTaskGenerator();
            taskQueue = ServerServiceLocator.instance().getTaskQueue();
            taskScheduler = new TaskScheduler();
            
            taskGenerator.updateAgents();
            setStatus("Waking up threads");
            if (config.isActiveServer())
                taskGenerator.setEnabled(true);
            
    		if (ConnectionPool.getPool().isOfflineMode())
    			loadBackupDbTasks(config);
    		else
    			loadMainDbTasks(config);

            while (!shutDownPending) {
                setStatus("Expiring tasks");
                try {
                    taskScheduler.reconfigure();
                } catch (Throwable t) {
                    log.warn("Error scheduling tasks", t);
                    
                }
                try {
                    taskQueue.expireTasks();
                } catch (Throwable t) {
                    log.warn("Error expiring tasks", t);
                    
                }
                if (shutDownPending)
                    break;

                setStatus("Updating task queue");
                if (enabled && !shutDownPending) {
            		if (! ConnectionPool.getPool().isOfflineMode())
             			loadMainDbTasks(config);
                    if (shutDownPending)
                        break;
                }
                if (shutDownPending)
                    break;
                try {
                    sleep(10000);
                } catch (InterruptedException e2) {
                } catch (IllegalThreadStateException e2) {
                }

            }
            sleep(3000);
        } catch (Exception e) {
            log.warn("Exception ", e);
        }
        boolean abort = false;
        log.info("Stopped", null, null);
        System.exit(2);
    }

	private void loadMainDbTasks (Config config)
	{
		if (config.getDB() != null)
		{
			try {
				ConnectionPool.getPool().setThreadStatus(ConnectionPool.ThreadBound.MASTER);
		    	taskGenerator.loadTasks();
			} 
			catch (Throwable t)
			{
		        log.warn("Error loading tasks", t);
			} finally {
				ConnectionPool.getPool().setThreadStatus(ConnectionPool.ThreadBound.ANY);
			}
		}
	}

	private void loadBackupDbTasks (Config config)
	{
		if (config.getBackupDB() != null)
		{
			try {
				ConnectionPool.getPool().setThreadStatus(ConnectionPool.ThreadBound.BACKUP);
		    	taskGenerator.loadTasks();
			} 
			catch (Throwable t)
			{
		        log.warn("Error loading tasks", t);
			} finally {
				ConnectionPool.getPool().setThreadStatus(ConnectionPool.ThreadBound.ANY);
			}
		}
	}

    public void shutDown() {
        shutDownPending = true;
        if (taskGenerator != null)
			try
			{
				taskGenerator.setEnabled(false);
			}
			catch (InternalErrorException e)
			{
			}
        this.interrupt();
        
    }

}
