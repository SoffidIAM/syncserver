package es.caib.seycon.ng.sync.engine;

import java.util.ArrayList;
import java.util.LinkedList;

import es.caib.seycon.ng.sync.servei.TaskQueueImpl;

/**
 * Contiene las tares "nuevas" que todavía no han sido procesadas por el
 * dispatcher Una vez procesadas desaparecen de la lista y pasan a la lista
 * general
 * 
 * @author u07286
 * 
 */
public class PriorityTaskQueue {

    private ArrayList<LinkedList<TaskHandler>> tasks;

    public LinkedList<TaskHandler> getTaskByPriority(int priority) {
        if (priority < 0 || priority > TaskQueueImpl.MAX_PRIORITY) {
            return null;
        }
        return tasks.get(priority);
    }

    public PriorityTaskQueue() {
        tasks = new ArrayList<LinkedList<TaskHandler>>(TaskQueueImpl.MAX_PRIORITY+1);
        for (int i = 0; i <= TaskQueueImpl.MAX_PRIORITY; i++) {
            tasks.add(new LinkedList<TaskHandler>());
        }
    }

    /**
     * Agregar una nueva tarea
     * 
     * @param newTask
     *            tarea a agregar
     */
    public synchronized void addTask(TaskHandler newTask) {
        int i = newTask.getPriority();
        tasks.get(i).addLast(newTask);
    }

    /**
     * Devolver la siguiente tarea a realizar por un task dispatcher
     * 
     * @param taskDispatcher
     *            agente que desea ejecutar la tarea
     * @param task
     *            última tarea ejecutada
     * @return null si no hay más tareas pendientes o las que siguientes no se
     *         pueden ejecutar todavía
     */
    public synchronized TaskHandler getNextPendingTask(TaskHandler previousTask) {
        int taskPriority;
        if (previousTask == null)
            taskPriority = TaskQueueImpl.MAX_PRIORITY;
        else
            taskPriority = previousTask.getPriority();
        for (int priority = 0; priority < TaskQueueImpl.MAX_PRIORITY
                && priority < taskPriority; priority++)

        {
            if (!tasks.get(priority).isEmpty()) {
                return tasks.get(priority).removeFirst();
            }
        }
        return null;
    }

    public synchronized void clear() {
        for (int i = 0; i < TaskQueueImpl.MAX_PRIORITY; i++) {
            tasks.get(i).clear();
        }
    }

}
