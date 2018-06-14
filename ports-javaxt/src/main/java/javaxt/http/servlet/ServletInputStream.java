package javaxt.http.servlet;

import java.io.IOException;

import javax.servlet.ReadListener;

import de.mhus.lib.errors.NotSupportedException;

//******************************************************************************
//**  ServletInputStream
//******************************************************************************
/**
 * Provides an input stream for reading the body of an http request. Reads raw
 * bytes from a socket connection. Automatically decrypts the data if the data
 * is SSL/TLS encrypted.
 *
 ******************************************************************************/

public class ServletInputStream extends javax.servlet.ServletInputStream {

	private HttpServletRequest request;
	private boolean decrypt;
	private int totalBytesRead = 0;
	private int contentLength = -1;

	// **************************************************************************
	// ** Constructor
	// **************************************************************************
	protected ServletInputStream(HttpServletRequest request) {
		this.request = request;
		this.decrypt = request.isEncrypted();
		this.contentLength = request.getContentLength();
	}

	// **************************************************************************
	// ** available
	// **************************************************************************
	/**
	 * Returns an estimate of the number of bytes that can be read.
	 */
	@Override
	public int available() {
		if (contentLength < 0)
			return 0;
		else
			return contentLength - totalBytesRead;
	}

	// **************************************************************************
	// ** markSupported
	// **************************************************************************
	/**
	 * Returns false. This stream does not support the mark and reset methods.
	 */
	@Override
	public boolean markSupported() {
		return false;
	}

	// **************************************************************************
	// ** read
	// **************************************************************************
	/**
	 * Reads the next byte of data from the socket. The byte is returned as a
	 * positive integer. If the end of the stream has been reached, a value of
	 * -1 is returned.
	 */
	@Override
	public int read() throws IOException {
		if (totalBytesRead == contentLength)
			return -1;

		Byte b = request.nextByte(decrypt);
		if (b == null)
			return -1; // throw new IOException();
		totalBytesRead++;
		return (int) b & 0xFF; // convert unsigned Byte to Int
	}

	/*
	 * public int readLine(byte[] b, int off, int len) throws IOException { int
	 * totalBytesRead = 0; while (true){
	 * 
	 * totalBytesRead++; if (totalBytesRead==len) break; } return
	 * totalBytesRead; }
	 */

	// **************************************************************************
	// ** readLine
	// **************************************************************************
	/**
	 * Returns a sequence of bytes from the socket. Stops when it reaches a
	 * carriage return + line feed (CRLF) or the end of stream, whichever comes
	 * first. If a CRLF is reached, the CRLF will be added to the array.
	 * 
	 * @return x
	 * @throws IOException
	 */
	public byte[] readLine() throws IOException {

		java.io.ByteArrayOutputStream bas = new java.io.ByteArrayOutputStream();
		while (true) {

			byte a = (byte) read();
			if (a == -1)
				break;

			if (a == '\r') {

				byte b = (byte) read();
				if (b == -1)
					break;

				if (b == '\n') {

					bas.write(b);
					break;
				} else if (b > -1) {
					bas.write(a);
					bas.write(b);
				}
			} else if (a > -1) {
				bas.write(a);
			}

		}
		return bas.toByteArray();
	}

	@Override
	public boolean isFinished() {
		return false;
	}

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public void setReadListener(ReadListener readListener) {
		throw new NotSupportedException();
	}

}