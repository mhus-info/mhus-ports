package javaxt.http.servlet;

//******************************************************************************
//**  Cookie Class
//******************************************************************************
/**
 * Creates a cookie, a small amount of information sent by a servlet to a Web
 * browser, saved by the browser, and later sent back to the server. A cookie's
 * value can uniquely identify a client, so cookies are commonly used for
 * session management.
 *
 ******************************************************************************/

public class Cookie extends javax.servlet.http.Cookie {

	private static final long serialVersionUID = 1L;

	public Cookie(String name) {
		super(name, null);
	}

	public Cookie(String name, String value) {
		super(name, value);
	}

	public void setMaxAge(long deltaSeconds) {
		// maxAge = deltaSeconds;
	}

}