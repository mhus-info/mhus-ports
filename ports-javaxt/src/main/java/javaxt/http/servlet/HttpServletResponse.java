package javaxt.http.servlet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Collection;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

import javaxt.http.Server.SocketConnection;

//******************************************************************************
//**  HttpServletResponse Class
//******************************************************************************
/**
 * Used to generate a response to an HTTP request. This class implements the
 * javax.servlet.http.HttpServletResponse interface defined in Version 2.5 of
 * the Java Servlet API.
 *
 ******************************************************************************/

public class HttpServletResponse implements javax.servlet.http.HttpServletResponse {

	private SocketConnection connection;
	private HttpServletRequest request;

	private Integer statusCode;
	private String statusMessage;
	private java.util.HashMap<String, String> headers;
	private boolean writeHeader = true;
	private Boolean chunked = null;
	private int bufferSize = 8096; // 8KB
	private static final String z = "GMT";
	private static final TimeZone tz = TimeZone.getTimeZone(z);

	private static final ByteBuffer CRLF = getCRLF();
	private String charSet = "UTF-8";
	private java.util.Locale locale = java.util.Locale.getDefault();
	private java.util.ArrayList<Cookie> cookies = new java.util.ArrayList<Cookie>();
	private Long startRange, endRange;
	private ServletOutputStream servletOutputStream;

	// **************************************************************************
	// ** Constructor
	// **************************************************************************
	/** Creates a new instance of this class. */

	public HttpServletResponse(HttpServletRequest request, SocketConnection connection) {
		this.connection = connection;
		this.headers = new java.util.HashMap<String, String>();
		this.request = request;

		// Set default response headers
		setHeader("Accept-Ranges", "bytes");
		setHeader("Connection", (request.isKeepAlive() ? "Keep-Alive" : "Close"));
		setHeader("Server", request.getServletContext().getServerInfo());
		setHeader("Date", getDate(Calendar.getInstance()));
		setStatus(200, "OK");

		// Parse range header (e.g. "Range: bytes=653316-676676")
		String range = request.getHeader("Range");
		if (range != null) {
			for (String kvp : range.split(";")) { // can there be multiple keys?
				kvp = kvp.trim();
				int eq = kvp.indexOf("=");
				if (eq > 0) {
					String key = kvp.substring(0, eq).trim().toLowerCase();
					String val = kvp.substring(eq + 1).trim();
					if (key.equals("bytes")) {
						String[] arr = val.split("-");
						try {
							startRange = Long.parseLong(arr[0].trim());
						} catch (Exception e) {
						}
						try {
							endRange = Long.parseLong(arr[1].trim());
						} catch (Exception e) {
						}
					}
				}
			}
		}
	}

	// **************************************************************************
	// ** addCookie
	// **************************************************************************
	/**
	 * Adds the specified cookie to the response.
	 */
	public void addCookie(Cookie cookie) {
		cookies.add(cookie);
	}

	// **************************************************************************
	// ** setContentLength
	// **************************************************************************
	/**
	 * Sets the "Content-Length" in the response header. Note that the
	 * "Content-Length" header is set automatically by most of the write()
	 * methods. So unless you're writing directly to the ServletOutputStream,
	 * you do not need to set this header.
	 */
	@Override
	public void setContentLength(int contentLength) {
		setContentLength(Long.parseLong(contentLength + ""));
	}

	// **************************************************************************
	// ** setContentLength
	// **************************************************************************
	/**
	 * Sets the "Content-Length" in the response header. Note that the
	 * "Content-Length" header is set automatically by most of the write()
	 * methods. So unless you're writing directly to the ServletOutputStream,
	 * you do not need to set this header.
	 */
	public void setContentLength(long contentLength) {
		setHeader("Content-Length", contentLength + "");
	}

	// **************************************************************************
	// ** getContentLength
	// **************************************************************************
	/**
	 * Returns the "Content-Length" defined in the response header. Returns null
	 * if the "Content-Length" is not defined or is less than zero.
	 */
	public Long getContentLength() {
		try {
			long l = Long.parseLong(getHeader("Content-Length"));
			if (l < 0)
				return null;
			else
				return l;
		} catch (Exception e) {
			return null;
		}
	}

	// **************************************************************************
	// ** setContentType
	// **************************************************************************
	/**
	 * Used to set/update the "Content-Type" response header (e.g.
	 * "text/html; charset=utf-8").
	 */
	@Override
	public void setContentType(String contentType) {
		setHeader("Content-Type", contentType);
	}

	// **************************************************************************
	// ** getContentType
	// **************************************************************************
	/**
	 * Returns the "Content-Type" defined in the response header (e.g.
	 * "text/html; charset=utf-8").
	 */
	@Override
	public String getContentType() {
		return getHeader("Content-Type");
	}

	// **************************************************************************
	// ** setCharacterEncoding
	// **************************************************************************
	/**
	 * Sets the name of the character encoding used in the response. Default is
	 * "UTF-8".
	 * 
	 * @param charset
	 *            String specifying the character set defined by
	 *            <a href="http://www.iana.org/assignments/character-sets">IANA
	 *            </a>.
	 */
	@Override
	public void setCharacterEncoding(String charset) {
		if (charset != null && !charset.equalsIgnoreCase(charSet)) {

			// Test the charset
			try {
				charset.getBytes(charset);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// If we're still here, update the class variable
			charSet = charset;
		}
	}

	// **************************************************************************
	// ** getCharacterEncoding
	// **************************************************************************
	/**
	 * Returns the name of the character encoding used in the response.
	 */
	@Override
	public String getCharacterEncoding() {
		return charSet;
	}

	// **************************************************************************
	// ** setLocale
	// **************************************************************************
	/**
	 * Sets the locale of the response. The locale is communicated via the
	 * "Content-Language" header and the character encoding in the
	 * "Content-Type" header.
	 */
	@Override
	public void setLocale(java.util.Locale locale) {
		this.locale = locale;
	}

	// **************************************************************************
	// ** getLocale
	// **************************************************************************
	/**
	 * Returns the locale specified for this response using the setLocale()
	 * method.
	 */
	@Override
	public java.util.Locale getLocale() {
		return locale;
	}

	// **************************************************************************
	// ** addHeader
	// **************************************************************************
	/**
	 * Adds a response header with the given name and value. According to spec,
	 * http response headers should be allowed to have multiple values. However,
	 * this implementation does not currently allow response headers to have
	 * multiple values.
	 */
	@Override
	public void addHeader(String name, String value) {
		setHeader(name, value);
	}

	// **************************************************************************
	// ** setHeader
	// **************************************************************************
	/**
	 * Sets a response header with the given name and value.
	 */
	@Override
	public void setHeader(String name, String value) {
		if (!writeHeader)
			return;
		if (name == null)
			return;
		if (name.equalsIgnoreCase("Set-Cookie")) {
			for (String cookie : value.split(";")) {
				cookie = cookie.trim();
				if (cookie.contains("=")) {
					name = cookie.substring(0, cookie.indexOf("=")).trim();
					value = cookie.substring(cookie.indexOf("=") + 1).trim();
					cookies.add(new Cookie(name, value));
				} else {
					cookies.add(new Cookie(cookie));
				}
			}
		} else {

			// Don't set "Transfer-Encoding" to "chunked" if the client doesn't
			// support it. Servers are explicitly forbidden from sending that
			// particular encoding type to clients announcing themselves as
			// HTTP/1.0 (e.g. Squid 2.5).
			if (name.equalsIgnoreCase("Transfer-Encoding") && value != null) {
				if (value.equalsIgnoreCase("chunked")) {
					String httpClient = request.getHttpVersion();
					if (httpClient == null)
						return;
					if (httpClient.equals("0.9") || httpClient.equals("1.0")) {
						return;
					}
				}
			}

			headers.put(name, value);
		}
	}

	// **************************************************************************
	// ** getHeader
	// **************************************************************************

	@Override
	public String getHeader(String name) {

		java.util.Iterator<String> it = headers.keySet().iterator();
		while (it.hasNext()) {
			String key = it.next();
			if (key.equalsIgnoreCase(name)) {
				return headers.get(key);
			}
		}
		return null;
	}

	// **************************************************************************
	// ** containsHeader
	// **************************************************************************
	/**
	 * Returns a boolean indicating whether the named response header has
	 * already been set.
	 */
	@Override
	public boolean containsHeader(String name) {
		return (getHeader(name) != null);
	}

	// **************************************************************************
	// ** setDateHeader
	// **************************************************************************
	/**
	 * Sets a response header with the given name and date-value.
	 */
	@Override
	public void setDateHeader(String name, long date) {
		setHeader(name, getDate(date));
	}

	// **************************************************************************
	// ** addDateHeader
	// **************************************************************************
	/**
	 * Adds a response header with the given name and date-value. According to
	 * spec, http response headers should be allowed to have multiple values.
	 * However, this implementation does not currently allow response headers to
	 * have multiple values.
	 */
	@Override
	public void addDateHeader(String name, long date) {
		setDateHeader(name, date);
	}

	// **************************************************************************
	// ** setIntHeader
	// **************************************************************************
	/**
	 * Sets a response header with the given name and integer value.
	 */
	@Override
	public void setIntHeader(String name, int value) {
		setHeader(name, value + "");
	}

	// **************************************************************************
	// ** addIntHeader
	// **************************************************************************
	/**
	 * Adds a response header with the given name and integer value. According
	 * to spec, http response headers should be allowed to have multiple values.
	 * However, this implementation does not currently allow response headers to
	 * have multiple values.
	 */
	@Override
	public void addIntHeader(String name, int value) {
		setIntHeader(name, value);
	}

	// **************************************************************************
	// ** setBufferSize
	// **************************************************************************
	/**
	 * Sets the preferred buffer size for the body of the response. A larger
	 * buffer allows more content to be sent to the client at a time. A smaller
	 * buffer decreases server memory load and allows the client to start
	 * receiving data more quickly.
	 * <p/>
	 *
	 * This method must be called before any response body content is written.
	 */
	@Override
	public void setBufferSize(int size) {
		if (size > 0)
			bufferSize = size;
	}

	// **************************************************************************
	// ** setBufferSize
	// **************************************************************************
	/**
	 * Returns the buffer size used for the response.
	 */
	@Override
	public int getBufferSize() {
		return bufferSize;
	}

	// **************************************************************************
	// ** setStatus
	// **************************************************************************

	@Override
	public void setStatus(int sc) {
		this.setStatus(sc, getStatusMessage(sc));
	}

	@Override
	public void setStatus(int statusCode, String statusMessage) {
		this.statusCode = statusCode;
		this.statusMessage = statusMessage;
	}

	@Override
	public int getStatus() {
		if (statusCode == null)
			return 200;
		return statusCode;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	// **************************************************************************
	// ** getStatusMessage
	// **************************************************************************
	/**
	 * Returns the status message for a given status code. Source:
	 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
	 */
	protected static String getStatusMessage(int statusCode) {
		switch (statusCode) {
		case 100:
			return "Continue";
		case 200:
			return "OK";
		case 201:
			return "Created";
		case 202:
			return "Accepted";
		case 203:
			return "Partial Information"; // Non-Authoritative Information
		case 204:
			return "No Content";
		case 206:
			return "Partial Content";
		case 301:
			return "Moved Permanently";
		case 302:
			return "Found";
		case 304:
			return "Not Modified";
		case 307:
			return "Temporary Redirect";
		case 400:
			return "Bad Request";
		case 401:
			return "Unauthorized";
		case 403:
			return "Forbidden";
		case 404:
			return "Not Found";
		case 500:
			return "Internal Error";
		case 501:
			return "Not Implemented";
		case 502:
			return "Bad Gateway";
		case 503:
			return "Service Unavailable";
		case 504:
			return "Gateway Timeout";
		case 505:
			return "HTTP Version Not Supported";
		default:
			return null;
		}
	}

	// **************************************************************************
	// ** sendError
	// **************************************************************************
	/**
	 * Sends an error response to the client using the specified status. The
	 * server defaults to creating the response to look like an HTML-formatted
	 * server error page containing the specified message, setting the content
	 * type to "text/html", leaving cookies and other headers unmodified.
	 *
	 * <p>
	 * If the response has already been committed, this method throws an
	 * IllegalStateException. After using this method, the response should be
	 * considered to be committed and should not be written to.
	 *
	 * @param sc
	 *            the error status code
	 * @param msg
	 *            the descriptive message
	 * @exception IOException
	 *                If an input or output exception occurs
	 * @exception IllegalStateException
	 *                If the response was committed
	 */
	@Override
	public void sendError(int sc, String msg) throws IOException {

		setStatus(sc, msg);
		write("<head>" + "<title>" + sc + " - " + msg + "</title>" + "</head>" + "<body>" + "<h1>" + sc + "</h1>" + msg
		        + "</body>");
	}

	// **************************************************************************
	// ** sendError
	// **************************************************************************
	/**
	 * Sends an error response to the client using the specified status code and
	 * clearing the buffer.
	 * <p>
	 * If the response has already been committed, this method throws an
	 * IllegalStateException. After using this method, the response should be
	 * considered to be committed and should not be written to.
	 *
	 * @param sc
	 *            the error status code
	 * @exception IOException
	 *                If an input or output exception occurs
	 * @exception IllegalStateException
	 *                If the response was committed before this method call
	 */
	@Override
	public void sendError(int sc) throws IOException {
		sendError(sc, getStatusMessage(sc));
	}

	// **************************************************************************
	// ** sendRedirect
	// **************************************************************************
	/**
	 * Sends a temporary redirect response to the client using the specified
	 * redirect location URL.
	 */
	@Override
	public void sendRedirect(String location) throws IOException {
		sendRedirect(location, false);
	}

	// **************************************************************************
	// ** sendRedirect
	// **************************************************************************
	/**
	 * Sends a temporary or permanent redirect response to the client using the
	 * specified redirect location URL.
	 */
	public void sendRedirect(String location, boolean movedPermanently) throws IOException {

		if (movedPermanently)
			setStatus(301);
		else
			setStatus(307);
		setHeader("Location", location);
		write("<head>" + "<title>Document Moved</title>" + "</head>" + "<body>" + "<h1>Object Moved</h1>"
		        + "This document may be found <a href=\"" + location + "\">here</a>" + "</body>");
	}

	// **************************************************************************
	// ** write
	// **************************************************************************
	/**
	 * Used to write a block of text in the response body. You should only call
	 * this method once.
	 * 
	 * @param compressOutput
	 *            Specify whether to gzip compress the text. Note that this
	 *            option will be applied only if "Accept-Encoding" supports gzip
	 *            compression.
	 */
	public void write(String text, boolean compressOutput) throws IOException {
		try {
			write(text.getBytes(charSet), compressOutput);
		} catch (java.io.UnsupportedEncodingException e) {
			// this error should have been thrown earlier (setCharacterEncoding)
		}
	}

	// **************************************************************************
	// ** write
	// **************************************************************************
	/**
	 * Used to write a block of text in the response body. Will automatically
	 * try to gzip compress the text if "Accept-Encoding" supports gzip
	 * compression. You should only call this method once.
	 */
	public void write(String text) throws IOException {
		this.write(text, true);
	}

	// **************************************************************************
	// ** write
	// **************************************************************************
	/**
	 * Used to write bytes to the response body. You should only call this
	 * method once.
	 * 
	 * @param bytes
	 *            Input byte array
	 * @param compressOutput
	 *            Specify whether to gzip compress the byte array. Note that
	 *            this option will be applied only if "Accept-Encoding" supports
	 *            gzip compression. Do not use this option if your bytes are
	 *            already gzip compressed.
	 */
	public void write(byte[] bytes, boolean compressOutput) throws IOException {
		if (bytes == null)
			return;

		// Check whether we can/should compress the output
		boolean gzip = false;
		if (compressOutput && bytes.length > 50) {
			String acceptEncoding = request.getHeader("Accept-Encoding");
			if (acceptEncoding != null) {
				if (acceptEncoding.toLowerCase().contains("gzip")) {
					gzip = true;
				}
			}
		}

		if (gzip) {

			// If the input byte array is smaller than the bufferSize we can
			// compress the entire array in a single step. Otherwise, we will
			// compress incrementally and chuck out the output.
			if (bytes.length <= bufferSize) {

				// Compress the byte array
				ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length);
				GZIPOutputStream out = new GZIPOutputStream(bos, bytes.length);
				out.write(bytes);
				out.finish();
				out.close();

				bytes = bos.toByteArray();

				bos.reset();
				bos = null;
				out = null;

				// Set content length. This is extremely important for
				// persistant
				// connections (i.e. "Connection: Keep-Alive").
				setContentLength(bytes.length);

				// Ensure that the Content Encoding is correct and write the
				// header
				setHeader("Content-Encoding", "gzip");
				writeHeader();

				// Write the body
				ByteBuffer output = ByteBuffer.allocateDirect(bytes.length);
				output.put(bytes);
				output.flip();
				write(output, output.capacity());

				output.clear();
				output = null;

			} else { // Chunk the output

				// Write header before sending the file contents. Ensure that
				// the output is chunked and the content encoding is correct.
				setHeader("Transfer-Encoding", "chunked");
				setHeader("Content-Encoding", "gzip");
				writeHeader();

				// Incrementally compress the byte array and chunk the output
				GZIPOutputStream out = new GZIPOutputStream(new ConnectionOutputStream(), bufferSize);
				java.io.InputStream inputStream = new ByteArrayInputStream(bytes);
				byte[] b = new byte[bufferSize];
				int x = 0;
				while ((x = inputStream.read(b)) != -1) {
					out.write(b, 0, x);
				}
				inputStream.close();

				out.finish();
				out.close();

				out = null;
				b = null;

			}
		} else { // no compression

			// Write header before sending the file contents.
			setContentLength(bytes.length);
			writeHeader();

			// Send the contents of the byte array to the client
			java.io.InputStream inputStream = new ByteArrayInputStream(bytes);
			ConnectionOutputStream out = new ConnectionOutputStream();
			byte[] b = new byte[bufferSize];

			int x = 0;
			while ((x = inputStream.read(b)) != -1) {
				out.write(b, 0, x);
			}

			inputStream.close();
			inputStream = null;

			out.close();
			out = null;

			b = null;
		}
	}

	// **************************************************************************
	// ** write
	// **************************************************************************
	/**
	 * Used to write bytes to the response body. Will automatically try to
	 * compress the byte array if "Accept-Encoding" supports gzip compression.
	 * You should only call this method once.
	 */
	public void write(byte[] bytes) throws IOException {
		this.write(bytes, true);
	}

	// **************************************************************************
	// ** write
	// **************************************************************************
	/**
	 * Used to write contents of a file into the response body. Automatically
	 * compresses the file content if the client supports gzip compression. You
	 * should only call this method once.
	 */
	public void write(java.io.File file, String contentType, boolean useCache) throws IOException {

		String fileName = null;
		if (!contentType.startsWith("image") && !contentType.startsWith("text")) {
			fileName = file.getName();
		}

		write(file, fileName, contentType, useCache);
	}

	// **************************************************************************
	// ** write
	// **************************************************************************
	/**
	 * Used to write contents of a file into the response body. Automatically
	 * compresses the file content if the client supports gzip compression. You
	 * should only call this method once.
	 * 
	 * @param fileName
	 *            Optional file name used in the "Content-Disposition" header.
	 *            If the fileName is null, the "Content-Disposition" header will
	 *            not be set by this method.
	 */
	public void write(java.io.File file, String fileName, String contentType, boolean useCache) throws IOException {

		if (!file.exists() || file.isDirectory()) {
			this.setStatus(404);
			return;
		}

		long fileSize = file.length();
		long fileDate = file.lastModified();

		// Process Cache Directives
		if (useCache) {

			String eTag = "W/\"" + fileSize + "-" + fileDate + "\"";
			setHeader("ETag", eTag);
			setHeader("Last-Modified", getDate(fileDate)); // Sat, 23 Oct 2010
			                                               // 13:04:28 GMT
			// this.setHeader("Cache-Control", "max-age=315360000");
			// this.setHeader("Expires", "Sun, 30 Sep 2018 16:23:15 GMT ");

			// Return 304/Not Modified response if we can...
			String matchTag = request.getHeader("if-none-match");
			String cacheControl = request.getHeader("cache-control");
			if (matchTag == null)
				matchTag = "";
			if (cacheControl == null)
				cacheControl = "";
			if (cacheControl.equalsIgnoreCase("no-cache") == false) {
				if (eTag.equalsIgnoreCase(matchTag)) {
					// System.out.println("Sending 304 Response!");
					this.setStatus(304);
					return;
				} else {
					// Internet Explorer 6 uses "if-modified-since" instead of
					// "if-none-match"
					matchTag = request.getHeader("if-modified-since");
					if (matchTag != null) {
						for (String tag : matchTag.split(";")) {
							if (tag.trim().equalsIgnoreCase(getDate(fileDate))) {
								// System.out.println("Sending 304 Response!");
								this.setStatus(304);
								return;
							}
						}
					}

				}
			}

		} else {
			setHeader("Cache-Control", "no-cache");
		}

		// Set Content Type and Disposition
		setContentType(contentType);
		if (fileName != null) {
			setHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\"");
		}

		// If the file is small enough, convert it to a byte array and call the
		// other write method.
		if (file.length() <= bufferSize) {
			byte[] b = new byte[(int) file.length()];
			java.io.FileInputStream is = new java.io.FileInputStream(file);
			java.nio.channels.FileChannel inputStream = is.getChannel();

			ByteBuffer buf = ByteBuffer.allocateDirect(b.length);
			buf.rewind();
			inputStream.read(buf);
			buf.rewind();
			buf.get(b);

			inputStream.close();
			is.close();

			write(b);
			return;
		}

		// Check whether to compress the response
		boolean gzip = false;
		String acceptEncoding = request.getHeader("Accept-Encoding");
		if (acceptEncoding != null) {
			if (acceptEncoding.toLowerCase().contains("gzip")) {
				gzip = true;
			}
		}

		// Dump file contents to servlet output stream
		if (gzip) {

			// Write header before sending the file contents. Ensure that the
			// output is chunked and the content encoding is correct.
			setHeader("Transfer-Encoding", "chunked");
			setHeader("Content-Encoding", "gzip");
			writeHeader();

			GZIPOutputStream out = new GZIPOutputStream(new ConnectionOutputStream(), bufferSize);
			java.io.InputStream inputStream = new java.io.FileInputStream(file);
			byte[] b = new byte[bufferSize];
			int x = 0;
			while ((x = inputStream.read(b)) != -1) {
				out.write(b, 0, x);
			}
			inputStream.close();

			out.finish();
			out.close();

			out = null;
			b = null;

		} else {

			// Write header before sending the file contents.
			setContentLength(fileSize);
			writeHeader();

			// Send the contents of a file to the client
			java.io.FileInputStream is = new java.io.FileInputStream(file);
			java.nio.channels.FileChannel inputStream = is.getChannel();

			ByteBuffer buf = ByteBuffer.allocateDirect(bufferSize);

			while (inputStream.read(buf) > -1) {
				writeChunk(buf);
				buf.clear();
				buf.rewind();
			}

			inputStream.close();
			is.close();

			inputStream = null;
			is = null;

		}
	}

	// **************************************************************************
	// ** write
	// **************************************************************************
	/**
	 * Used to transfer bytes from an input stream to the response body.
	 * 
	 * @param inputStream
	 *            java.io.InputStream
	 * @param compressOutput
	 *            Specify whether to gzip compress the byte array. Note that
	 *            this option will be applied only if "Accept-Encoding" supports
	 *            gzip compression. Do not use this option if your bytes are
	 *            already gzip compressed.
	 */
	public void write(java.io.InputStream inputStream, boolean compressOutput) throws IOException {
		if (inputStream == null)
			return;

		// Check whether we can/should compress the output
		boolean gzip = false;
		if (compressOutput) {
			String acceptEncoding = request.getHeader("Accept-Encoding");
			if (acceptEncoding != null) {
				if (acceptEncoding.toLowerCase().contains("gzip")) {
					gzip = true;
				}
			}
		}

		// Write header before sending the file contents.
		setHeader("Transfer-Encoding", "chunked");
		if (gzip)
			setHeader("Content-Encoding", "gzip");
		writeHeader();

		// Write body. Compress as needed.
		java.io.OutputStream out;
		if (gzip)
			out = new GZIPOutputStream(new ConnectionOutputStream(), bufferSize);
		else
			out = new ConnectionOutputStream();
		byte[] b = new byte[bufferSize];
		int x = 0;
		while ((x = inputStream.read(b)) != -1) {
			out.write(b, 0, x);
		}
		inputStream.close();

		// out.finish();
		out.close();

		out = null;
		b = null;
	}

	// **************************************************************************
	// ** getOutputStream
	// **************************************************************************
	/**
	 * Returns an output stream for writing the body of an http response.
	 * Automatically encrypts the data if the connection is SSL/TLS encrypted.
	 * Note that by default, if the request header includes a keep-alive
	 * directive, the response header will include a keep-alive response. As
	 * such, you must explicitely set the content length, set the "Connection"
	 * header to "Close", or chunk the output using chunked encoding. Example:
	 * 
	 * <pre>
	 * // IMPORTANT: Set response headers before getting the output stream!
	 * response.setContentLength(54674);
	 * 
	 * // Get output stream
	 * java.io.OutputStream outputStream = response.getOutputStream();
	 * 
	 * // Transfer bytes from an input stream to the output stream
	 * byte[] b = new byte[1024];
	 * int x = 0;
	 * while ((x = inputStream.read(b)) != -1) {
	 *     outputStream.write(b, 0, x);
	 * }
	 * 
	 * // Close the input and output streams
	 * outputStream.close();
	 * inputStream.close();
	 * </pre>
	 */
	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		writeHeader();
		if (servletOutputStream == null)
			servletOutputStream = new ServletOutputStream(new ConnectionOutputStream());
		return servletOutputStream;
	}

	// **************************************************************************
	// ** getWriter
	// **************************************************************************
	/**
	 * Returns a PrintWriter object that can send character text to the client.
	 */
	@Override
	public java.io.PrintWriter getWriter() throws IOException {
		return new java.io.PrintWriter(this.getOutputStream());
	}

	// **************************************************************************
	// ** ConnectionOutputStream
	// **************************************************************************
	/**
	 * Simple implementation of an OutputStream used to write bytes directly to
	 * a non-blocking SocketChannel. This class was originally written to
	 * support incremental GZIP compression but is used in other places as well.
	 */
	protected class ConnectionOutputStream extends java.io.OutputStream {
		private ByteBuffer buf = ByteBuffer.allocateDirect(bufferSize);

		public ConnectionOutputStream() {
		}

		@Override
		public void write(int b) throws IOException {

			buf.put((byte) b);
			buf.position(buf.position());

			if (buf.position() == buf.capacity()) {
				writeChunk(buf);
				buf.clear();
				buf.rewind();
			}
		}

		@Override
		public void flush() throws IOException {
			close();
		}

		@Override
		public void close() throws IOException {
			if (buf.position() > 0) {
				writeChunk(buf);
				buf.clear();
				buf.rewind();
			}
		}
	}

	// **************************************************************************
	// ** writeChunk
	// **************************************************************************
	/**
	 * Writes a "chunk" of data to the client. Transparently wraps the bytes
	 * into "chunked" transfer format as needed.
	 */
	private void writeChunk(ByteBuffer buf) throws IOException {

		// Late check to see if we should chunk the output
		if (chunked == null) {
			String TransferEncoding = getHeader("Transfer-Encoding");
			chunked = (TransferEncoding != null ? TransferEncoding.equalsIgnoreCase("chunked") : false);
		}

		if (buf.position() == buf.capacity()) { // Send all the bytes to the
		                                        // client

			if (chunked) {

				// Create new byte buffer and insert chunk header
				ByteBuffer sz = getBuffer(buf.position());
				int len = sz.capacity() + 2 + buf.capacity() + 2;
				ByteBuffer b = ByteBuffer.allocateDirect(len);
				b.put(sz);
				b.put(CRLF);
				CRLF.rewind();

				// Insert bytes
				buf.rewind();
				b.put(buf);

				// Insert chunk delimitor
				b.put(CRLF);
				CRLF.rewind();

				// Send buffer to client
				b.rewind();
				write(b, b.capacity());
				b.clear();

			} else {
				buf.rewind();
				write(buf, buf.capacity());
			}
		} else { // Buffer is only partially filled. This is our queue that
		         // we're
		         // done sending bytes to the client.

			// Trim the byte buffer
			byte[] arr = new byte[buf.position()];
			buf.rewind();
			buf.get(arr);
			buf = ByteBuffer.allocateDirect(arr.length);
			buf.put(arr);
			arr = null;

			if (chunked) {

				// Create new byte buffer and insert chunk header
				ByteBuffer sz = getBuffer(buf.position());
				ByteBuffer z = getBuffer(0);
				int len = sz.capacity() + 2 + buf.capacity() + 2 + z.capacity() + 4;
				ByteBuffer b = ByteBuffer.allocateDirect(len);
				b.put(sz);
				sz.clear();
				b.put(CRLF);
				CRLF.rewind();

				// Insert bytes
				buf.rewind();
				b.put(buf);

				// Insert chunk delimitor and a zero-length last chunk: "0\r\n"
				// and the final "\r\n".
				b.put(CRLF);
				CRLF.rewind();
				b.put(z);
				z.clear();
				b.put(CRLF);
				CRLF.rewind();
				b.put(CRLF);
				CRLF.rewind();

				// Send buffer to client
				b.rewind();
				write(b, b.capacity());
				b.clear();
			} else {
				buf.rewind();
				write(buf, buf.capacity());
			}

			chunked = null;

		}
	}

	/** Total bytes processed before writing the response body. */
	private long ttl = 0;

	// **************************************************************************
	// ** write
	// **************************************************************************
	/**
	 * Of all the write methods, this is the only on that actually writes
	 * something to the SocketChannel.
	 */
	private void write(ByteBuffer buf, int length) throws IOException {

		// Check whether we have satisfied the range request. Update the byte
		// buffer as needed.
		if (startRange != null && !writeHeader) {

			if (getStatus() == 416)
				return;

			if ((ttl + length) < startRange) {
				// Haven't gotten to the start range yet and the current buffer
				// doesn't get us there either.
				ttl += length;
				return;
			} else if (ttl < startRange) {
				// Haven't gotten to the start range but the current buffer does
				// contain bytes we need to satisfy the request. Resize the
				// buffer
				// accordingly.
				int pos = (int) (startRange - ttl);
				length = length - pos;
				ttl += pos;
				buf.position(pos);

				byte[] arr = new byte[length];
				buf.get(arr);
				buf = ByteBuffer.allocateDirect(arr.length);
				buf.put(arr);
				buf.flip();
			}

			if (endRange != null) {
				if (ttl > endRange) {
					return; // we're past end range
				} else {
					long remainingBytes = (endRange + 1) - (ttl);
					if (remainingBytes < length) {
						length = (int) remainingBytes;
						byte[] arr = new byte[length];
						buf.rewind();
						buf.get(arr);
						buf = ByteBuffer.allocateDirect(length);
						buf.put(arr);
						buf.flip();
					} else {
						// System.err.println("Send bytes? Range (" + startRange
						// + "-" + endRange + "). "
						// + "Written " + (ttl) + "bytes. Need to send " +
						// remainingBytes + " more");
					}
				}
			} else { // no end range specified and content length is unknown

				// System.err.println("No end range specified and content length
				// is unknown? "
				// + "Range (" + startRange + "-" + endRange + "). Written " +
				// (ttl) + " bytes. Buffer is " + length);
			}
		}

		if (request.isEncrypted()) {
			buf = request.wrap(buf);
			length = buf.capacity();
		}
		connection.write(buf, length);
	}

	// **************************************************************************
	// ** getBuffer
	// **************************************************************************
	/**
	 * Converts an integer into a ByteBuffer.
	 */
	private ByteBuffer getBuffer(int i) throws IOException {
		char[] chars = Integer.toHexString(i).toCharArray();
		ByteBuffer buf = ByteBuffer.allocateDirect(chars.length);
		for (char c : chars) {
			buf.put((byte) c);
			buf.position(buf.position());
		}
		buf.rewind();
		chars = null;
		return buf;
	}

	// **************************************************************************
	// ** getCRLF
	// **************************************************************************
	/**
	 * Used to create and fill a ByteBuffer with a Carriage Return (CR) and Line
	 * Feed (LF).
	 */
	private static ByteBuffer getCRLF() {
		ByteBuffer buf = ByteBuffer.allocateDirect(2);
		buf.put("\r\n".getBytes());
		buf.rewind();
		return buf;
	}

	// **************************************************************************
	// ** getChannel
	// **************************************************************************
	/**
	 * Returns the raw NIO SocketChannel associated with this response. Use with
	 * care.
	 */
	public java.nio.channels.SocketChannel getChannel() {
		return connection.getChannel();
	}

	// **************************************************************************
	// ** writeHeader
	// **************************************************************************
	/**
	 * Used to write the http header to the response.
	 */
	private void writeHeader() throws IOException {

		if (writeHeader == false)
			return;
		else {

			byte[] header = getHeader().getBytes(charSet);
			ByteBuffer output = ByteBuffer.allocateDirect(header.length);
			output.put(header);
			output.flip();
			write(output, output.capacity());
			output.clear();
			output = null;

			writeHeader = false;
		}
	}

	// **************************************************************************
	// ** getHeader
	// **************************************************************************
	/**
	 * Returns the raw HTTP response header.
	 */
	public String getHeader() {

		// Update the status code and message as needed
		if (statusCode == null)
			statusCode = 200;
		if (statusMessage == null)
			statusMessage = getStatusMessage(statusCode);

		String TransferEncoding = getHeader("Transfer-Encoding");
		boolean chunked = (TransferEncoding != null ? TransferEncoding.equalsIgnoreCase("chunked") : false);

		// Update header for range requests
		if (statusCode < 300 && startRange != null) {
			setStatus(206);
			if (chunked)
				setHeader("Transfer-Encoding", null);
			String contentRange = "bytes " + startRange + "-";
			Long contentLength = getContentLength();
			if (contentLength != null) {
				if (endRange == null) {
					endRange = contentLength - 1;
					contentRange += endRange + "/" + contentLength;
					setContentLength((endRange + 1) - startRange);
				} else {
					if (endRange >= contentLength) {
						setStatus(416);
						contentRange = "bytes */" + contentLength;
						setContentLength(0);
					} else {
						contentRange += endRange + "/" + contentLength;
						setContentLength((endRange + 1) - startRange);
					}
				}
			} else {// Content Length is Unknown

				if (endRange == null) {
					contentRange += "/*";
				} else {
					contentRange += endRange + "/*";
					setContentLength((endRange + 1) - startRange);
				}
			}

			setHeader("Content-Range", contentRange);
		} else {
			startRange = endRange = null;
		}

		// The http response headers must include a value for "Content-Length"
		// if using a persistant connection (i.e. "Connection: Keep-Alive") and
		// chunkedTransfer is turned off.
		String connType = getHeader("Connection");
		boolean isKeepAlive = (connType != null ? connType.equalsIgnoreCase("Keep-Alive") : false);

		if (isKeepAlive && getContentLength() == null && chunked == false) {
			setHeader("Connection", "Close");
		}

		// If a new session has been created, add the session id to the response
		HttpSession session = request.getSession(false);
		if (session != null) {
			if (session.isNew())
				addCookie(new Cookie("JSESSIONID", session.getID()));
		}

		// Add status line
		StringBuffer header = new StringBuffer();
		header.append("HTTP/1.1 " + statusCode + (statusMessage == null ? "" : " " + statusMessage) + "\r\n");

		// Add headers
		java.util.Iterator<String> it = headers.keySet().iterator();
		while (it.hasNext()) {
			String key = it.next();
			String val = headers.get(key);
			if (val != null)
				header.append(key + ": " + val + "\r\n");
		}

		// Add cookies
		if (!cookies.isEmpty()) {
			header.append("Set-Cookie: ");
			java.util.Iterator<Cookie> cookie = cookies.iterator();
			while (cookie.hasNext()) {
				header.append(cookie.next().toString());
				if (cookie.hasNext())
					header.append(" ");
			}
			header.append("\r\n");
		}

		header.append("\r\n");
		return header.toString();
	}

	// **************************************************************************
	// ** toString
	// **************************************************************************
	/**
	 * Returns the raw HTTP response header.
	 */
	@Override
	public String toString() {
		return getHeader();
	}

	// **************************************************************************
	// ** getDate
	// **************************************************************************
	/**
	 * Used to convert a date to a string (e.g. "Mon, 20 Feb 2012 07:22:20 EST"
	 * ). This method does not rely on the java.text.SimpleDateFormat for
	 * performance reasons.
	 */
	private static String getDate(Calendar cal) {

		if (!cal.getTimeZone().equals(tz)) {
			cal = (java.util.Calendar) cal.clone();
			cal.setTimeZone(tz);
		}

		StringBuffer str = new StringBuffer(29);
		switch (cal.get(Calendar.DAY_OF_WEEK)) {
		case Calendar.MONDAY:
			str.append("Mon, ");
			break;
		case Calendar.TUESDAY:
			str.append("Tue, ");
			break;
		case Calendar.WEDNESDAY:
			str.append("Wed, ");
			break;
		case Calendar.THURSDAY:
			str.append("Thu, ");
			break;
		case Calendar.FRIDAY:
			str.append("Fri, ");
			break;
		case Calendar.SATURDAY:
			str.append("Sat, ");
			break;
		case Calendar.SUNDAY:
			str.append("Sun, ");
			break;
		}

		int i = cal.get(Calendar.DAY_OF_MONTH);
		str.append(i < 10 ? "0" + i : i);

		switch (cal.get(Calendar.MONTH)) {
		case Calendar.JANUARY:
			str.append(" Jan ");
			break;
		case Calendar.FEBRUARY:
			str.append(" Feb ");
			break;
		case Calendar.MARCH:
			str.append(" Mar ");
			break;
		case Calendar.APRIL:
			str.append(" Apr ");
			break;
		case Calendar.MAY:
			str.append(" May ");
			break;
		case Calendar.JUNE:
			str.append(" Jun ");
			break;
		case Calendar.JULY:
			str.append(" Jul ");
			break;
		case Calendar.AUGUST:
			str.append(" Aug ");
			break;
		case Calendar.SEPTEMBER:
			str.append(" Sep ");
			break;
		case Calendar.OCTOBER:
			str.append(" Oct ");
			break;
		case Calendar.NOVEMBER:
			str.append(" Nov ");
			break;
		case Calendar.DECEMBER:
			str.append(" Dec ");
			break;
		}

		str.append(cal.get(Calendar.YEAR));
		str.append(" ");

		i = cal.get(Calendar.HOUR_OF_DAY);
		str.append(i < 10 ? "0" + i + ":" : i + ":");

		i = cal.get(Calendar.MINUTE);
		str.append(i < 10 ? "0" + i + ":" : i + ":");

		i = cal.get(Calendar.SECOND);
		str.append(i < 10 ? "0" + i + " " : i + " ");

		str.append(z);
		return str.toString();

		// new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
		// return f.format(date);
	}

	private String getDate(long milliseconds) {
		java.util.Calendar cal = java.util.Calendar.getInstance();
		cal.setTimeInMillis(milliseconds);
		return getDate(cal);
	}

	// **************************************************************************
	// ** reset
	// **************************************************************************
	/**
	 * Clears the status code and headers. This method is called automatically
	 * after each new http request to free up server resources. You do not need
	 * to call this method explicitly from your application.
	 */
	@Override
	public void reset() {

		if (headers != null) {
			headers.clear();
			headers = null;
		}

		if (request != null) {
			request.clear();
			request = null;
		}
	}

	// **************************************************************************
	// ** flushBuffer
	// **************************************************************************
	/**
	 * Forces any content in the buffer to be written to the client. A call to
	 * this method automatically commits the response, meaning the status code
	 * and headers will be written. This method is called automatically after
	 * each http request to free up server resources. You do not need to call
	 * this method explicitly from your application.
	 */
	@Override
	public void flushBuffer() {
		if (writeHeader && headers != null) {
			try {
				setContentLength(0);
				writeHeader();
			} catch (IOException e) {
			}
		}
	}

	public void closeBuffer() {
		if (servletOutputStream != null)
			try {
				servletOutputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		servletOutputStream = null;
	}

	// **************************************************************************
	// ** resetBuffer
	// **************************************************************************
	/**
	 * According to spec, this method is supposed to clear the content buffer.
	 * However, this implementation doesn't use a content buffer. All content is
	 * written immediately.
	 */
	@Override
	public void resetBuffer() {
		if (isCommitted())
			throw new IllegalStateException();
	}

	// **************************************************************************
	// ** isCommitted
	// **************************************************************************
	/**
	 * Returns a boolean indicating if the response has been committed. A
	 * committed response has already had its status code and headers written.
	 */
	@Override
	public boolean isCommitted() {
		return !writeHeader;
	}

	// **************************************************************************
	// ** encodeURL
	// **************************************************************************
	/**
	 * According to spec, this method is supposed to encode the specified URL by
	 * including the session ID in it, or, if encoding is not needed, returns
	 * the URL unchanged. This is important for browsers that don't support
	 * cookies. In our case, sessions are maintained using cookies so we return
	 * the URL unchanged.
	 */
	@Override
	public String encodeURL(String url) {
		return url;
	}

	// **************************************************************************
	// ** encodeRedirectURL
	// **************************************************************************
	/**
	 * According to spec, this method is supposed to encode the specified URL
	 * for use in the sendRedirect method or, if encoding is not needed, returns
	 * the URL unchanged. The implementation of this method includes logic to
	 * determine whether the session ID needs to be encoded in the URL. This is
	 * important for browsers that don't support cookies. In our case, sessions
	 * are maintained using cookies so we return the URL unchanged.
	 */
	@Override
	public String encodeRedirectURL(String url) {
		return url;
	}

	// **************************************************************************
	// ** encodeUrl
	// **************************************************************************
	/**
	 * @deprecated Use encodeURL(String url) instead
	 */
	@Deprecated
	@Override
	public String encodeUrl(String url) {
		return encodeURL(url);
	}

	// **************************************************************************
	// ** encodeRedirectUrl
	// **************************************************************************
	/**
	 * @deprecated Use encodeRedirectURL(String url) instead
	 */
	@Deprecated
	@Override
	public String encodeRedirectUrl(String url) {
		return url;
	}

	// **************************************************************************
	// ** Server status codes; see RFC 2068.
	// **************************************************************************

	/** Status code (100) indicating the client can continue. */
	public static final int SC_CONTINUE = 100;

	/**
	 * Status code (101) indicating the server is switching protocols according
	 * to Upgrade header.
	 */
	public static final int SC_SWITCHING_PROTOCOLS = 101;

	/** Status code (200) indicating the request succeeded normally. */
	public static final int SC_OK = 200;

	/**
	 * Status code (201) indicating the request succeeded and created a new
	 * resource on the server.
	 */
	public static final int SC_CREATED = 201;

	/**
	 * Status code (202) indicating that a request was accepted for processing,
	 * but was not completed.
	 */
	public static final int SC_ACCEPTED = 202;

	/**
	 * Status code (203) indicating that the meta information presented by the
	 * client did not originate from the server.
	 */
	public static final int SC_NON_AUTHORITATIVE_INFORMATION = 203;

	/**
	 * Status code (204) indicating that the request succeeded but that there
	 * was no new information to return.
	 */
	public static final int SC_NO_CONTENT = 204;

	/**
	 * Status code (205) indicating that the agent <em>SHOULD</em> reset the
	 * document view which caused the request to be sent.
	 */
	public static final int SC_RESET_CONTENT = 205;

	/**
	 * Status code (206) indicating that the server has fulfilled the partial
	 * GET request for the resource.
	 */
	public static final int SC_PARTIAL_CONTENT = 206;

	/**
	 * Status code (300) indicating that the requested resource corresponds to
	 * any one of a set of representations, each with its own specific location.
	 */
	public static final int SC_MULTIPLE_CHOICES = 300;

	/**
	 * Status code (301) indicating that the resource has permanently moved to a
	 * new location, and that future references should use a new URI with their
	 * requests.
	 */
	public static final int SC_MOVED_PERMANENTLY = 301;

	/**
	 * Status code (302) indicating that the resource has temporarily moved to
	 * another location, but that future references should still use the
	 * original URI to access the resource. This definition is being retained
	 * for backwards compatibility. SC_FOUND is now the preferred definition.
	 */
	public static final int SC_MOVED_TEMPORARILY = 302;

	/**
	 * Status code (302) indicating that the resource reside temporarily under a
	 * different URI. Since the redirection might be altered on occasion, the
	 * client should continue to use the Request-URI for future requests.
	 * (HTTP/1.1) To represent the status code (302), it is recommended to use
	 * this variable.
	 */
	public static final int SC_FOUND = 302;

	/**
	 * Status code (303) indicating that the response to the request can be
	 * found under a different URI.
	 */
	public static final int SC_SEE_OTHER = 303;

	/**
	 * Status code (304) indicating that a conditional GET operation found that
	 * the resource was available and not modified.
	 */
	public static final int SC_NOT_MODIFIED = 304;

	/**
	 * Status code (305) indicating that the requested resource <em>MUST</em> be
	 * accessed through the proxy given by the Location field.
	 */
	public static final int SC_USE_PROXY = 305;

	/**
	 * Status code (307) indicating that the requested resource resides
	 * temporarily under a different URI. The temporary URI <em>SHOULD</em> be
	 * given by the <code><em>Location</em></code> field in the response.
	 */
	public static final int SC_TEMPORARY_REDIRECT = 307;

	/**
	 * Status code (400) indicating the request sent by the client was
	 * syntactically incorrect.
	 */
	public static final int SC_BAD_REQUEST = 400;

	/**
	 * Status code (401) indicating that the request requires HTTP
	 * authentication.
	 */
	public static final int SC_UNAUTHORIZED = 401;

	/** Status code (402) reserved for future use. */
	public static final int SC_PAYMENT_REQUIRED = 402;

	/**
	 * Status code (403) indicating the server understood the request but
	 * refused to fulfill it.
	 */
	public static final int SC_FORBIDDEN = 403;

	/**
	 * Status code (404) indicating that the requested resource is not
	 * available.
	 */
	public static final int SC_NOT_FOUND = 404;

	/**
	 * Status code (405) indicating that the method specified in the
	 * <code><em>Request-Line</em></code> is not allowed for the resource
	 * identified by the <code><em>Request-URI</em></code>.
	 */
	public static final int SC_METHOD_NOT_ALLOWED = 405;

	/**
	 * Status code (406) indicating that the resource identified by the request
	 * is only capable of generating response entities which have content
	 * characteristics not acceptable according to the accept headers sent in
	 * the request.
	 */
	public static final int SC_NOT_ACCEPTABLE = 406;

	/**
	 * Status code (407) indicating that the client <em>MUST</em> first
	 * authenticate itself with the proxy.
	 */
	public static final int SC_PROXY_AUTHENTICATION_REQUIRED = 407;

	/**
	 * Status code (408) indicating that the client did not produce a request
	 * within the time that the server was prepared to wait.
	 */
	public static final int SC_REQUEST_TIMEOUT = 408;

	/**
	 * Status code (409) indicating that the request could not be completed due
	 * to a conflict with the current state of the resource.
	 */
	public static final int SC_CONFLICT = 409;

	/**
	 * Status code (410) indicating that the resource is no longer available at
	 * the server and no forwarding address is known. This condition
	 * <em>SHOULD</em> be considered permanent.
	 */
	public static final int SC_GONE = 410;

	/**
	 * Status code (411) indicating that the request cannot be handled without a
	 * defined <code><em>Content-Length</em></code>.
	 */
	public static final int SC_LENGTH_REQUIRED = 411;

	/**
	 * Status code (412) indicating that the precondition given in one or more
	 * of the request-header fields evaluated to false when it was tested on the
	 * server.
	 */
	public static final int SC_PRECONDITION_FAILED = 412;

	/**
	 * Status code (413) indicating that the server is refusing to process the
	 * request because the request entity is larger than the server is willing
	 * or able to process.
	 */
	public static final int SC_REQUEST_ENTITY_TOO_LARGE = 413;

	/**
	 * Status code (414) indicating that the server is refusing to service the
	 * request because the <code><em>Request-URI</em></code> is longer than the
	 * server is willing to interpret.
	 */
	public static final int SC_REQUEST_URI_TOO_LONG = 414;

	/**
	 * Status code (415) indicating that the server is refusing to service the
	 * request because the entity of the request is in a format not supported by
	 * the requested resource for the requested method.
	 */
	public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;

	/**
	 * Status code (416) indicating that the server cannot serve the requested
	 * byte range.
	 */
	public static final int SC_REQUESTED_RANGE_NOT_SATISFIABLE = 416;

	/**
	 * Status code (417) indicating that the server could not meet the
	 * expectation given in the Expect request header.
	 */
	public static final int SC_EXPECTATION_FAILED = 417;

	/**
	 * Status code (500) indicating an error inside the HTTP server which
	 * prevented it from fulfilling the request.
	 */
	public static final int SC_INTERNAL_SERVER_ERROR = 500;

	/**
	 * Status code (501) indicating the HTTP server does not support the
	 * functionality needed to fulfill the request.
	 */
	public static final int SC_NOT_IMPLEMENTED = 501;

	/**
	 * Status code (502) indicating that the HTTP server received an invalid
	 * response from a server it consulted when acting as a proxy or gateway.
	 */
	public static final int SC_BAD_GATEWAY = 502;

	/**
	 * Status code (503) indicating that the HTTP server is temporarily
	 * overloaded, and unable to handle the request.
	 */
	public static final int SC_SERVICE_UNAVAILABLE = 503;

	/**
	 * Status code (504) indicating that the server did not receive a timely
	 * response from the upstream server while acting as a gateway or proxy.
	 */
	public static final int SC_GATEWAY_TIMEOUT = 504;

	/**
	 * Status code (505) indicating that the server does not support or refuses
	 * to support the HTTP protocol version that was used in the request
	 * message.
	 */
	public static final int SC_HTTP_VERSION_NOT_SUPPORTED = 505;

	@Override
	public void setContentLengthLong(long len) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addCookie(javax.servlet.http.Cookie cookie) {
		// TODO Auto-generated method stub

	}

	@Override
	public Collection<String> getHeaders(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<String> getHeaderNames() {
		// TODO Auto-generated method stub
		return null;
	}
}