package es.caib.seycon.ng.sync.engine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;


public class TaskQueueIterator implements Iterator<TaskHandler> {
	private ArrayList<LinkedList<TaskHandler>> lists;
	private int nextPriority;
	private Iterator<TaskHandler> currentIterator = null;

	public TaskQueueIterator(ArrayList<LinkedList<TaskHandler>> lists, int priority) {
		this.lists = lists;
	}

	public TaskQueueIterator(ArrayList<LinkedList<TaskHandler>> lists) {
		this.lists = lists;
	}

	public boolean hasNext() {
		while (getIterator () != null && ! getIterator().hasNext())
			nextIterator();

		return getIterator() != null;
	}

	public TaskHandler next() {
		if (getIterator () == null)
			return null;
		TaskHandler obj = getIterator ().next();
		return obj;
	}
	
	public void remove() {
		getIterator().remove();
	}

	private Iterator<TaskHandler> nextIterator ()
	{
		currentIterator = null;
		return getIterator ();
	}
	
	private Iterator<TaskHandler> getIterator ()
	{
		while (currentIterator == null && nextPriority < lists.size())
		{
			currentIterator = lists.get(nextPriority++).iterator();
			if (! currentIterator.hasNext())
				currentIterator = null;
		}
		return currentIterator;
	}

}
