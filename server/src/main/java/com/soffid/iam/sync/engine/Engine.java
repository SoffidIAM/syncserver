package com.soffid.iam.sync.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soffid.iam.config.Config;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.cron.TaskScheduler;
import com.soffid.iam.sync.service.TaskGenerator;
import com.soffid.iam.sync.service.TaskQueue;

import es.caib.seycon.ng.exception.InternalErrorException;


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
            enabled = config.isActiveServer();
            sleep(5000);
            
            taskGenerator = ServerServiceLocator.instance().getTaskGenerator();
            taskQueue = ServerServiceLocator.instance().getTaskQueue();
            taskScheduler = new TaskScheduler();
            
            taskGenerator.updateAgents();
            setStatus("Waking up threads");
            if (config.isActiveServer())
                taskGenerator.setEnabled(true);
            
   			loadMainDbTasks(config);

			boolean firstSchedulerLoop = true;
            while (!shutDownPending) {
                setStatus("Reconfiguring");
                enabled = config.isActiveServer() && config.isMainServer();
                try {
					if (firstSchedulerLoop)
                       taskScheduler.init();
					else
                       taskScheduler.reconfigure();
					firstSchedulerLoop = false;
                } catch (Throwable t) {
                    log.warn("Error scheduling tasks", t);
                }
                
                setStatus("Expiring tasks");
                try {
                    taskQueue.expireTasks();
                } catch (Throwable t) {
                    log.warn("Error expiring tasks", t);
                    
                }
                if (shutDownPending)
                    break;

                if (config.isActiveServer()) {
                	try {
                		taskGenerator.updateClusterStatus();
                	} catch (Exception e) {
                		log.warn("Error updating cluster status");
                	}
                }
                
                try {
                    taskGenerator.purgeServerInstances();
                } catch (Throwable t) {
                    log.warn("Error purging server instances", t);
                }

                setStatus("Updating task queue");
                if (!shutDownPending) {
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
		    	taskGenerator.loadTasks();
			} 
			catch (Throwable t)
			{
		        log.warn("Error loading tasks", t);
			} finally {
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
