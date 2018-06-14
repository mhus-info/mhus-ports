package javaxt.websocket;
import javaxt.websocket.Frame.CloseFrame;

public class FrameException extends Exception {

    private static final long serialVersionUID = 3731842424390998726L;
    private int closecode;


    public FrameException() {
        this( CloseFrame.PROTOCOL_ERROR);
    }

    public FrameException(String s) {
        this( CloseFrame.PROTOCOL_ERROR, s);
    }

    public FrameException(Throwable t) {
        this( CloseFrame.PROTOCOL_ERROR, t);
    }

    public FrameException(String s, Throwable t) {
        this( CloseFrame.PROTOCOL_ERROR, s, t);
    }

    public FrameException(int closecode) {
        this.closecode = closecode;
    }


    public FrameException(int closecode, String s) {
    super(s);
        this.closecode = closecode;
    }


    public FrameException(int closecode, Throwable t) {
    super(t);
        this.closecode = closecode;
    }


    public FrameException(int closecode, String s, Throwable t) {
    super(s, t);
        this.closecode = closecode;
    }


    public int getCloseCode() {
        return closecode;
    }
}