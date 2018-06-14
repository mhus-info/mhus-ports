package javaxt.websocket;

public class IncompleteFrameException extends Throwable {
	private static final long serialVersionUID = 7330519489840500997L;
	private int preferredSize;

	public IncompleteFrameException(int preferredSize) {
		this.preferredSize = preferredSize;
	}

	public int getPreferredSize() {
		return preferredSize;
	}
}