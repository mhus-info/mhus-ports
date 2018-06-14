package javaxt.http.servlet;

//******************************************************************************
//**  ServletException
//******************************************************************************
/**
 *   Defines a general exception a servlet can throw when it encounters an
 *   invalid request or error.
 *
 ******************************************************************************/

public class ServletException extends javax.servlet.ServletException {

    private int statusCode = 400;

    public ServletException() {
        this(400, HttpServletResponse.getStatusMessage(400));
    }

    public ServletException(String message) {
        this(400, message);
    }

    public ServletException(String message, Exception e) {
        this(400, message);
    }

    public ServletException(int statusCode) {
        this(statusCode, HttpServletResponse.getStatusMessage(statusCode));
    }

    public ServletException(int statusCode, String message) {
        super((message==null ? HttpServletResponse.getStatusMessage(statusCode) : message));
        this.statusCode = statusCode;
    }
    
    public int getStatusCode(){
        return statusCode;
    }
}