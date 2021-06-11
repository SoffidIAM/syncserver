package com.soffid.iam.sync.hub.server;

import java.io.Serializable;

public class Request implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6140660152712844125L;
	
	// Request control
	long start;
	transient long accept;
	String source;
	boolean done;
	boolean accepted;
	Long id; 
	
	// Request heador
	String target;
	String url;
	
	// Request content
	String method;
	String classes[];
	Object args[];
	// Response
	boolean success;
	Object response;
	
	
	public long getStart() {
		return start;
	}
	public void setStart(long start) {
		this.start = start;
	}
	public String getTarget() {
		return target;
	}
	public void setTarget(String target) {
		this.target = target;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String getSource() {
		return source;
	}
	public void setSource(String source) {
		this.source = source;
	}
	public boolean isDone() {
		return done;
	}
	public void setDone(boolean done) {
		this.done = done;
	}
	public String getMethod() {
		return method;
	}
	public void setMethod(String method) {
		this.method = method;
	}
	public String[] getClasses() {
		return classes;
	}
	public void setClasses(String[] classes) {
		this.classes = classes;
	}
	public Object[] getArgs() {
		return args;
	}
	public void setArgs(Object[] args) {
		this.args = args;
	}
	public boolean isSuccess() {
		return success;
	}
	public void setSuccess(boolean success) {
		this.success = success;
	}
	public Object getResponse() {
		return response;
	}
	public void setResponse(Object response) {
		this.response = response;
	}
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public boolean isAccepted() {
		return accepted;
	}
	public void setAccepted(boolean accepted) {
		this.accepted = accepted;
	}
	public long getAccept() {
		return accept;
	}
	public void setAccept(long accept) {
		this.accept = accept;
	}
}
