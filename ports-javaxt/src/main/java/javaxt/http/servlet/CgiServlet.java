package javaxt.http.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.StringTokenizer;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

//******************************************************************************
//**  CGI Servlet
//******************************************************************************
/**
 * Http Servlet used to run CGI programs. Based on CgiServlet.java, v1.8
 * developed by Jef Poskanzer (acme.com).
 *
 ******************************************************************************/

public class CgiServlet extends HttpServlet {

	private java.io.File executable;

	// **************************************************************************
	// ** Constructor
	// **************************************************************************

	public CgiServlet(java.io.File executable) {
		this.executable = executable;
	}

	// **************************************************************************
	// ** getServletInfo
	// **************************************************************************
	/**
	 * Returns a string containing information about the author, version, and
	 * copyright of the servlet.
	 */
	@Override
	public String getServletInfo() {
		return "JavaXT CGI Servlet";
	}

	// **************************************************************************
	// ** getParameters
	// **************************************************************************
	/**
	 * Returns a list of parameters that are used to instantiate the CGI
	 * application.
	 */
	protected java.util.ArrayList<String> getParameters(HttpServletRequest request) {
		java.util.ArrayList<String> env = new java.util.ArrayList<String>();
		// env.add("PATH=" + "/usr/local/bin:/usr/ucb:/bin:/usr/bin");
		env.add("GATEWAY_INTERFACE=" + "CGI/1.1");
		env.add("SERVER_SOFTWARE=" + getServletContext().getServerInfo());
		env.add("SERVER_PROTOCOL=" + request.getProtocol());
		env.add("SERVER_NAME=" + request.getServerName());
		env.add("SERVER_PORT=" + request.getServerPort());
		env.add("REMOTE_ADDR=" + request.getRemoteAddr());
		env.add("REMOTE_HOST=" + request.getRemoteHost());
		env.add("REQUEST_METHOD=" + request.getMethod());
		env.add("SCRIPT_NAME=" + request.getServletPath());

		int contentLength = request.getContentLength();
		if (contentLength != -1)
			env.add("CONTENT_LENGTH=" + contentLength);

		String contentType = request.getContentType();
		if (contentType != null)
			env.add("CONTENT_TYPE=" + contentType);

		String pathInfo = request.getPathInfo();
		if (pathInfo != null)
			env.add("PATH_INFO=" + pathInfo);

		String pathTranslated = request.getPathTranslated();
		if (pathTranslated != null)
			env.add("PATH_TRANSLATED=" + pathTranslated);

		String queryString = request.getQueryString();
		if (queryString != null)
			env.add("QUERY_STRING=" + queryString);

		String remoteUser = request.getRemoteUser();
		if (remoteUser != null)
			env.add("REMOTE_USER=" + remoteUser);

		String authType = request.getAuthType();
		if (authType != null)
			env.add("AUTH_TYPE=" + authType);

		java.util.Enumeration<String> hnEnum = request.getHeaderNames();
		while (hnEnum.hasMoreElements()) {
			String name = hnEnum.nextElement();
			String value = request.getHeader(name);
			if (value == null)
				value = "";
			env.add("HTTP_" + name.toUpperCase().replace('-', '_') + "=" + value);
		}
		return env;
	}

	// **************************************************************************
	// ** processRequest
	// **************************************************************************
	/**
	 * Services a single request from the client.
	 * 
	 * @param request
	 *            the servlet request
	 * @param response
	 *            the servlet response
	 * @exception ServletException
	 *                when an exception has occurred
	 */
	@Override
	public void service(ServletRequest req, ServletResponse res) throws javax.servlet.ServletException, IOException {
		// public void service(HttpServletRequest request, HttpServletResponse
		// response) throws ServletException, IOException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;
		String method = request.getMethod().toUpperCase();
		if (!(method.equals("GET") || method.equals("POST"))) {
			response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
			return;
		}

		// Generate a list of parameters used to instantiate the CGI application
		java.util.ArrayList<String> env = getParameters(request);
		String[] parameters = new String[env.size() + 1];
		parameters[0] = executable.toString();
		for (int i = 0; i < parameters.length; i++) {
			if (i > 0)
				parameters[i] = env.get(i - 1);
		}

		try {

			// Run executable via Command Line
			Runtime runtime = Runtime.getRuntime();
			Process process = runtime.exec(parameters, null, executable.getParentFile());

			// If this is a POST, pass the body of the request to the process
			if (method.equals("POST")) {
				OutputStream outputStream = process.getOutputStream();
				InputStream inputStream = request.getInputStream();
				byte[] b = new byte[1024];
				int x = 0;
				while ((x = inputStream.read(b)) != -1) {
					outputStream.write(b, 0, x);
				}
				inputStream.close();
				outputStream.close();
			}

			// Parse output streams
			StreamReader s1 = new StreamReader(process.getInputStream(), response);
			ErrorStreamReader s2 = new ErrorStreamReader(process.getErrorStream());
			s1.start();
			s2.start();
			process.waitFor();
			s1.join();
			s2.join();

			// Explicitly clean up every the process by calling close on each
			// stream
			try {
				process.getInputStream().close();
			} catch (Exception ex) {
			}
			try {
				process.getErrorStream().close();
			} catch (Exception ex) {
			}
			try {
				process.getOutputStream().close();
			} catch (Exception ex) {
			}

			// Explicitly destroy the process even if the process is already
			// terminated
			try {
				process.destroy();
			} catch (Exception ex) {
			}

			process = null;

		} catch (IOException e) {
			throw e;
		} catch (InterruptedException e) {
			// throw e;
			return;
		}
	}

	// **************************************************************************
	// ** StreamReader Class
	// **************************************************************************
	/** Thread used to process the standard output stream. */

	private class StreamReader implements Runnable {

		private InputStream is;
		private HttpServletResponse response;
		private Thread thread;
		private byte[] b = new byte[1];

		public StreamReader(InputStream is, HttpServletResponse response) {
			this.is = is;
			this.response = response;
		}

		public void start() {
			thread = new Thread(this);
			thread.start();
		}

		@Override
		public void run() {

			try {
				// Parse the list few lines returned from the executable. These
				// may contain HTTP response headers
				boolean firstLine = true;
				while (true) {
					String line = readLine();
					if (line == null)
						break;
					line = line.trim();
					if (line.equals(""))
						break;

					int colon = line.indexOf(":");
					if (colon == -1) {
						// No colon. If it's the first line, parse it for
						// status.
						if (firstLine) {
							StringTokenizer tok = new StringTokenizer(line, " ");
							try {
								switch (tok.countTokens()) {
								case 2:
									tok.nextToken();
									response.setStatus(Integer.parseInt(tok.nextToken()));
									break;
								case 3:
									tok.nextToken();
									response.setStatus(Integer.parseInt(tok.nextToken()), tok.nextToken());
									break;
								}
							} catch (NumberFormatException ignore) {
							}
						} else {
							// No colon and it's not the first line? Ignore.
						}
					} else {
						// There's a colon. Check for certain special headers.
						String name = line.substring(0, colon);
						String value = line.substring(colon + 1).trim();
						if (name.equalsIgnoreCase("Status")) {
							StringTokenizer tok = new StringTokenizer(value, " ");
							try {
								switch (tok.countTokens()) {
								case 1:
									response.setStatus(Integer.parseInt(tok.nextToken()));
									break;
								case 2:
									response.setStatus(Integer.parseInt(tok.nextToken()), tok.nextToken());
									break;
								}
							} catch (NumberFormatException ignore) {
							}
						} else if (name.equalsIgnoreCase("Content-type")) {
							response.setContentType(value);
						} else if (name.equalsIgnoreCase("Content-length")) {
							try {
								response.setContentLength(Integer.parseInt(value));
							} catch (NumberFormatException ignore) {
							}
						} else if (name.equalsIgnoreCase("Location")) {
							response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
							response.setHeader(name, value);
						} else if (name.equalsIgnoreCase("Set-Cookie")) {
							int x = value.indexOf("=");
							if (x > 0) {
								String n = value.substring(0, x);
								String v = value.substring(x + 1).trim();
								response.addCookie(new Cookie(n, v));
							}
						} else {
							// Not a special header. Just set it.
							response.setHeader(name, value);
						}
					}
				}

				// Set transfer encoding
				response.setHeader("Transfer-Encoding", "Chunked");

				// Tranfer remaining bytes from the standard output stream to
				// the servlet output stream
				OutputStream outputStream = response.getOutputStream();
				byte[] b = new byte[1024];
				int x = 0;
				while ((x = is.read(b)) != -1) {
					outputStream.write(b, 0, x);
				}

				// Close the input and output streams
				outputStream.close();
				is.close();

			} catch (IOException e) {
				// response.sendError(
				// HttpServletResponse.SC_INTERNAL_SERVER_ERROR );
				// There's some weird bug in Java, when reading from a Process
				// you get a spurious IOException. We have to ignore it.
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		}

		private String readLine() throws IOException {
			StringBuffer str = new StringBuffer();
			while (true) {
				if (is.read(b) == -1)
					break;
				byte c = b[0];
				if (c == '\n')
					break;
				str.append((char) c);
			}
			return str.toString();
		}

		public void join() throws InterruptedException {
			thread.join();
		}

	} // End StreamReader Class

	// **************************************************************************
	// ** ErrorStreamReader Class
	// **************************************************************************
	/** Thread used to read the standard output streams. */

	private class ErrorStreamReader implements Runnable {

		private InputStream is;
		private Thread thread;
		private byte[] b = new byte[1];

		public ErrorStreamReader(InputStream is) {
			this.is = is;
		}

		public void start() {
			thread = new Thread(this);
			thread.start();
		}

		@Override
		public void run() {
			try {
				while (true) {
					if (is.read(b) == -1)
						break;
				}
				is.close();
			} catch (Exception e) {
				// System.out.println ("Problem reading stream... :" + ex);
				e.printStackTrace();
				return;
			}
		}

		public void join() throws InterruptedException {
			thread.join();
		}

	} // End ErrorStreamReader Class
}