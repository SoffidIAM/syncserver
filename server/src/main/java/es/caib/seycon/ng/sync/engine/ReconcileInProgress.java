package es.caib.seycon.ng.sync.engine;

public class ReconcileInProgress extends Exception {

	public ReconcileInProgress() {
	}

	public ReconcileInProgress(String message) {
		super(message);
	}

	public ReconcileInProgress(Throwable cause) {
		super(cause);
	}

	public ReconcileInProgress(String message, Throwable cause) {
		super(message, cause);
	}

	public ReconcileInProgress(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
