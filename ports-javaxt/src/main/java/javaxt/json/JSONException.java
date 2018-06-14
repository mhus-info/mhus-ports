package javaxt.json;

//******************************************************************************
//**  JSONException
//******************************************************************************
/**
 *   The JSONException is thrown by the javaxt.json classes when things are 
 *   amiss.
 * 
 *   @author json.org
 *   @version 2015-12-09
 *
 ******************************************************************************/

public class JSONException extends RuntimeException {

    private static final long serialVersionUID = 0;

    public JSONException(final String message) {
        super(message);
    }

    public JSONException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public JSONException(final Throwable cause) {
        super(cause.getMessage(), cause);
    }
}