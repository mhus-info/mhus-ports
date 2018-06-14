package javaxt.http.servlet;

import java.io.IOException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import de.mhus.lib.core.MLog;

//******************************************************************************
//**  HttpServlet Class
//******************************************************************************
/**
 * The HttpServer requires an implementation of an HttpServlet in order to
 * process HTTP requests.
 *
 ******************************************************************************/

public abstract class HttpServlet extends MLog implements Servlet {

	private Authenticator authenticator;
	private javax.net.ssl.KeyManager[] kms;
	private javax.net.ssl.TrustManager[] tms;
	private String sslProvider;
	protected static final ServletContext context = new ServletContext();

	// This variable are used in the HttpServletRequest class.
	protected String servletPath = "";
	private ServletConfig config;

	// **************************************************************************
	// ** init
	// **************************************************************************
	/**
	 * Called by the servlet container to indicate to a servlet that it is being
	 * placed into service.
	 */
	@Override
	public void init(ServletConfig ServletConfig) throws ServletException {
		this.config = config;
	}

	// **************************************************************************
	// ** processRequest
	// **************************************************************************
	/**
	 * This method is called each time the server receives an http request (GET,
	 * POST, HEAD, etc.). Use this method to formulate a response to the client.
	 */
	@Override
	public abstract void service(ServletRequest req, ServletResponse res)
	        throws javax.servlet.ServletException, IOException;

	// **************************************************************************
	// ** getServletContext
	// **************************************************************************
	/**
	 * Returns the ServletContext.
	 */
	public ServletContext getServletContext() {
		return context;
	}

	@Override
	public void destroy() {
	};

	// **************************************************************************
	// ** setPaths
	// **************************************************************************
	/**
	 * Used to set the context and servlet paths used in the
	 * HttpServletRequest.getContextPath() and the
	 * HttpServletRequest.getServletPath() methods.
	 */
	public void setPaths(String contextPath, String servletPath) {
		// TODO: Update logic used to assign context path
		this.getServletContext().setContextPath(contextPath);
		this.servletPath = servletPath;
	}

	// **************************************************************************
	// ** setAuthenticator
	// **************************************************************************
	/**
	 * Used to define an Authenticator used to authenticate requests.
	 */
	public void setAuthenticator(Authenticator authenticator) {
		this.authenticator = authenticator;
	}

	// **************************************************************************
	// ** getAuthenticator
	// **************************************************************************
	/**
	 * Returns a new instance of an Authenticator used to authenticate users.
	 */
	protected Authenticator getAuthenticator(HttpServletRequest request) {
		if (authenticator != null)
			return authenticator.newInstance(request);
		else
			return null;
	}

	// **************************************************************************
	// ** setKeyStore
	// **************************************************************************
	/**
	 * Used to specify a KeyStore. The KeyStore is used to store keys and
	 * certificates for SSL.
	 */
	public void setKeyStore(KeyStore keystore, String passphrase) throws Exception {
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(keystore, passphrase.toCharArray());
		kms = kmf.getKeyManagers();
	}

	// **************************************************************************
	// ** setKeyStore
	// **************************************************************************
	/**
	 * Used to specify a KeyStore. The KeyStore is used to store keys and
	 * certificates for SSL.
	 */
	public void setKeyStore(java.io.File keyStoreFile, String passphrase) throws Exception {
		char[] pw = passphrase.toCharArray();
		KeyStore keystore = KeyStore.getInstance("JKS");
		keystore.load(new java.io.FileInputStream(keyStoreFile), pw);
		setKeyStore(keystore, passphrase);
	}

	// **************************************************************************
	// ** setKeyManager
	// **************************************************************************
	/**
	 * Used to specify a KeyManager. The KeyManager is responsible for managing
	 * keys and certificates found in a KeyStore and is used to initialize the
	 * SSLContext. Typically, users are not required to specify a KeyManager.
	 * Instead, a KeyManager is selected for you whenever the setKeyStore()
	 * method is called. However, in some cases, the default KeyManager is not
	 * adequate (e.g. managing KeyStores with multiple SSL certificates) and
	 * users need to specify a different KeyManager.
	 */
	public void setKeyManager(javax.net.ssl.KeyManager keyManager) throws Exception {
		kms = new javax.net.ssl.KeyManager[] { keyManager };
	}

	// **************************************************************************
	// ** setTrustStore
	// **************************************************************************
	/**
	 * Used to set the TrustStore and initialize the TrustManagerFactory. The
	 * TrustStore is used to store public keys and certificates for SSL.
	 */
	public void setTrustStore(KeyStore truststore) throws Exception {
		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		tmf.init(truststore);
		tms = tmf.getTrustManagers();
	}

	// **************************************************************************
	// ** setTrustStore
	// **************************************************************************
	/**
	 * Used to set the TrustStore and initialize the TrustManagerFactory. The
	 * TrustStore is used to store public keys and certificates for SSL.
	 */
	public void setTrustStore(java.io.File trustStoreFile, String passphrase) throws Exception {
		char[] pw = passphrase.toCharArray();
		KeyStore truststore = KeyStore.getInstance("JKS");
		truststore.load(new java.io.FileInputStream(trustStoreFile), pw);
		setTrustStore(truststore);
	}

	// **************************************************************************
	// ** setSSLProvider
	// **************************************************************************
	/**
	 * Used to specify an Security Provider used to decrypt SSL/TLS messages.
	 */
	public void setSSLProvider(java.security.Provider provider) {
		if (provider != null) {
			sslProvider = provider.getName();
			// java.security.Security.addProvider(provider);
		} else
			sslProvider = null;
	}

	// **************************************************************************
	// ** setSSLProvider
	// **************************************************************************
	/**
	 * Used to specify an Security Provider used to decrypt SSL/TLS messages.
	 */
	public void setSSLProvider(String provider) {
		setSSLProvider(java.security.Security.getProvider(provider));
	}

	// **************************************************************************
	// ** getSSLContext
	// **************************************************************************
	/**
	 * Used to initialize an SSLContext which, in turn is used by an SSLEngine
	 * decrypt SSL/TLS messages.
	 */
	public SSLContext getSSLContext() throws ServletException {

		/*
		 * //Debug use only! java.security.Provider provider = new
		 * SSLProvider(); java.security.Security.addProvider(provider);
		 * setSSLProvider(provider);
		 */

		SSLContext sslContext = null;
		try {
			if (sslProvider == null)
				sslContext = SSLContext.getInstance("TLS");
			else
				sslContext = SSLContext.getInstance("TLS", sslProvider);
			sslContext.init(kms, tms, null);
		} catch (Exception e) {
			ServletException se = new ServletException("Failed to initialize SSLContext.");
			se.initCause(e);
			throw se;
		}

		return sslContext;
	}

	// **************************************************************************
	// ** getSSLEngine
	// **************************************************************************
	/**
	 * Used to instantiate an SSLEngine used to decrypt SSL/TLS messages.
	 */
	protected SSLEngine getSSLEngine(String host, int port) throws ServletException {
		SSLContext sslContext = getSSLContext();
		SSLEngine sslEngine = sslContext.createSSLEngine(host, port);
		sslEngine.setUseClientMode(false);
		sslEngine.setEnableSessionCreation(true);
		sslEngine.setNeedClientAuth(false);
		return sslEngine;
	}

	// **************************************************************************
	// ** supportsHttps
	// **************************************************************************
	/**
	 * Returns true if the servlet has been configured to support HTTP/SSL. This
	 * is determined by checking if a KeyStore or a KeyManager has been
	 * assigned.
	 */
	public boolean supportsHttps() {
		if (kms != null && kms.length > 0) {
			if (kms[0] != null)
				return true;
		}
		return false;
	}

	@Override
	public ServletConfig getServletConfig() {
		return config;
	}

	@Override
	public String getServletInfo() {
		// TODO Auto-generated method stub
		return null;
	}
}