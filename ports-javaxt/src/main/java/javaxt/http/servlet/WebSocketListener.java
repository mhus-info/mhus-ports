package javaxt.http.servlet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import de.mhus.lib.core.MLog;
import javaxt.http.Server;
import javaxt.websocket.Frame;
import javaxt.websocket.Frame.CloseFrame;
import javaxt.websocket.FrameException;
import javaxt.websocket.IncompleteFrameException;
import javaxt.websocket.UTF8;

//******************************************************************************
//**  WebSocketListener
//******************************************************************************
/**
 * Used to process WebSocket requests. Instances of this class are created
 * within the HttpServlet.processRequest() method. Example:
 *
 * <pre>
 * public class WebSocketTest extends HttpServlet {
 * 
 *     public WebSocketTest() {
 *     }
 * 
 *     public void processRequest(HttpServletRequest request, HttpServletResponse response)
 *             throws ServletException, java.io.IOException {
 * 
 *         if (request.isWebSocket()) {
 *             new WebSocketListener(ws, request, response) {
 * 	            public void onConnect() {
 * 		            send("Hello There!");
 * 	            }
 * 
 * 	            public void onText(String str) {
 * 		            send("Message recieved at " + new java.util.Date());
 * 	            }
 * 
 * 	            public void onDisconnect(int statusCode, String reason) {
 * 		            send("Goodbye!");
 * 	            }
 *             };
 *             return;
 *         }
 *     }
 * }
 * </pre>
 *
 ******************************************************************************/

public class WebSocketListener extends MLog {

	private Server.SocketConnection connection;
	private HttpServletRequest request;
	private boolean debug = true;
	private List<String> events = new LinkedList<String>();

	private enum READYSTATE {
		NOT_YET_CONNECTED, CONNECTING, OPEN, CLOSING, CLOSED
	}

	private enum CloseHandshakeType {
		NONE, ONEWAY, TWOWAY
	}

	private READYSTATE readystate = READYSTATE.NOT_YET_CONNECTED;
	private Frame current_continuous_frame;
	private List<ByteBuffer> byteBufferList;
	private boolean roleIsServer;
	private ByteBuffer incompleteframe;

	// **************************************************************************
	// ** Constructor
	// **************************************************************************
	public WebSocketListener(HttpServletRequest request, HttpServletResponse response)
	        throws ServletException, IOException {

		// Validate request
		if (!request.isWebSocket())
			throw new ServletException("Invalid WebSocket request");

		// Set local variables
		this.request = request;
		this.connection = request.getConnection();
		roleIsServer = true;
		byteBufferList = new ArrayList<ByteBuffer>();

		log().d("New WebSocketListener");

		// Upgrade the request
		try {

			response.setStatus(101, "Web Socket Protocol Handshake");
			response.setHeader("Upgrade", "websocket");
			response.setHeader("Connection", request.getHeader("Connection"));
			String seckey = request.getHeader("Sec-WebSocket-Key");
			if (seckey == null)
				throw new Exception("Missing Sec-WebSocket-Key");

			String acc = seckey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
			java.security.MessageDigest sh1 = java.security.MessageDigest.getInstance("SHA1");
			String finalKey = Base64.encode(sh1.digest(acc.getBytes()));
			response.setHeader("Sec-WebSocket-Accept", finalKey);

			byte[] header = response.getHeader().getBytes("UTF-8");
			ByteBuffer output = ByteBuffer.allocateDirect(header.length);
			output.put(header);
			output.flip();
			write(output, output.capacity());
			output.clear();

		} catch (Exception e) {
			e.printStackTrace();
			response.sendError(500, e.getMessage());
			return;
		}

		log().t("Upgraded Request!");

		// Update readystate and notify listener
		readystate = READYSTATE.OPEN;
		onConnect();

		// Destroy the response object. We'll be handling the response from here
		// on out...
		response.reset();

		// Spawn thread and wait for "READ" events from the client
		try {
			Thread thread = new Thread(new EventProcessor());
			thread.start();
			thread.join();
		} catch (Throwable t) {
			// Throw servlet exception?
		}
	}

	// **************************************************************************
	// ** addEvent
	// **************************************************************************
	/**
	 * Used to add an event to the event queue (e.g. "READ" or "CLOSE").
	 */
	private void addEvent(String event) {
		synchronized (events) {
			events.add(event);
			events.notify();
		}
	}

	// **************************************************************************
	// ** EventProcessor
	// **************************************************************************
	/**
	 * Thread used to wait for new events (e.g. "READ" events).
	 */
	private class EventProcessor implements Runnable {

		public EventProcessor() {
			// Start monitoring the SocketConnection for "readable" events.
			// Do this after the SSL/TLS handshaking is completed by the
			// HttpServletRequest class.
			connection.addListener(new Server.SocketConnection.Listener() {
				@Override
				public void onReadable() {
					log().t("onReadable!");
					addEvent("READ");
				}
			});
		}

		@Override
		public void run() {

			while (true) {

				String event = null;
				synchronized (events) {
					while (events.isEmpty()) {
						try {
							events.wait();
						} catch (InterruptedException e) {
							return;
						}
					}
					event = events.remove(0);
					events.notifyAll();
				}

				if (event.equals("READ")) {
					try {

						// Read bytes from the socket
						int bufferSize = 1024;
						ByteBuffer buf = ByteBuffer.allocateDirect(bufferSize);
						int numBytesRead = connection.getChannel().read(buf); // request.read(buf);
						if (numBytesRead > 0) {
							buf.rewind();

							// If the buffer is filled to capacity, chances are
							// that there are more bytes to read from the socket
							if (numBytesRead == bufferSize) {
								java.io.ByteArrayOutputStream bas = new java.io.ByteArrayOutputStream();

								byte[] b = new byte[numBytesRead];
								buf.get(b, 0, numBytesRead);
								bas.write(b);

								while (true) {
									buf.clear();
									numBytesRead = connection.getChannel().read(buf); // request.read(buf);

									b = new byte[numBytesRead];
									buf.get(b, 0, numBytesRead);
									bas.write(b);

									if (numBytesRead < bufferSize) {
										break;
									}
								}

								buf = ByteBuffer.allocateDirect(bas.size());
								buf.put(bas.toByteArray());
								buf.rewind();
							}

							// Decrypt the buffer as needed
							if (request.isEncrypted()) {
								byte[] arr = request.decrypt(buf);
								buf = ByteBuffer.allocateDirect(arr.length);
								buf.put(arr);
								buf.rewind();
							}

							// Decode frames
							List<Frame> frames = decodeFrames(buf);
							for (Frame frame : frames) {
								log().t("read frame", frame);
								processFrame(frame);
							}
						}
					} catch (FrameException e) {
						onError(e);
						close(e.getCloseCode(), e.getMessage(), false);
					} catch (Exception e) {
						if (readystate == READYSTATE.OPEN) {
							onError(e);
							onDisconnect(CloseFrame.ABNORMAL_CLOSE, "", false);
						}
						try {
							log().t("Closing connection!");
							connection.close();
						} catch (IOException ex) {
						}
						return;
					}
				} else {
					try {
						log().t("Closing connection!");
						connection.close();
					} catch (Exception e) {
					}
					return;
				}
			}
		}
	}

	// **************************************************************************
	// ** onError
	// **************************************************************************
	/**
	 * Called whenever an error is encountered.
	 */
	private void onError(Exception e) {
		if (debug)
			e.printStackTrace();
	}

	// **************************************************************************
	// ** onConnect
	// **************************************************************************
	/**
	 * Called whenever a new WebSocket connection is established.
	 */
	public void onConnect() {
	}

	// **************************************************************************
	// ** onDisconnect
	// **************************************************************************
	/**
	 * Called whenever a WebSocket connection is terminated.
	 */
	public void onDisconnect(int statusCode, String reason, boolean remote) {
	}

	// **************************************************************************
	// ** onText
	// **************************************************************************
	/**
	 * Called whenever a client sends a text message to the WebSocket.
	 */
	public void onText(String str) {
	}

	// **************************************************************************
	// ** onBinary
	// **************************************************************************
	/**
	 * Called whenever a client sends binary data to the WebSocket.
	 */
	public void onBinary(ByteBuffer buf) {
	}

	// **************************************************************************
	// ** send
	// **************************************************************************
	/**
	 * Used to send a text message to the client.
	 */
	public void send(String str) {
		try {
			send(Frame.createFrame(str, !roleIsServer));
		} catch (IOException e) {
		}
	}

	// **************************************************************************
	// ** send
	// **************************************************************************
	/**
	 * Used to send a frame to the client.
	 */
	private void send(Frame frame) throws IOException {
		ByteBuffer buf = frame.getByteBuffer(!roleIsServer);
		int length = buf.capacity();
		buf.flip();

		log().t("send frame", frame);
		write(buf, length);
	}

	// **************************************************************************
	// ** close
	// **************************************************************************
	/**
	 * Sends a websocket Close frame, with a normal status code and no reason
	 * phrase. This will enqueue a graceful close to the remote endpoint.
	 */
	public void close() {
		close(CloseFrame.NORMAL, "", false);
	}

	// **************************************************************************
	// ** disconnect
	// **************************************************************************
	/**
	 * Issues a harsh disconnect of the underlying connection. This will
	 * terminate the connection, without sending a websocket close frame.
	 */
	public void disconnect() throws IOException {
		close(CloseFrame.ABNORMAL_CLOSE, "", false);
	}

	// **************************************************************************
	// ** close
	// **************************************************************************
	/**
	 * Used to close the websocket connection and the underlying http socket.
	 *
	 * @param remote
	 *            Used to indicate who is initiating the close event. If true
	 *            then the client initiated the close event. Otherwise, it is
	 *            the server.
	 */
	private void close(int code, String message, boolean remote) {
		if (readystate == READYSTATE.CLOSED)
			return;

		log().t("Close!");
		if (readystate == READYSTATE.OPEN) {
			readystate = READYSTATE.CLOSING;

			if (code == CloseFrame.ABNORMAL_CLOSE) {
				remote = false;
			} else {
				try {
					CloseFrame closeFrame = new CloseFrame(code, message);
					closeFrame.isValid();
					send(closeFrame);
				} catch (Exception e) {
					onError(e);
					code = CloseFrame.ABNORMAL_CLOSE;
					message = "Error sending/generation CloseFrame";
					remote = false;
				}

			}
		} else {

			if (code == CloseFrame.FLASHPOLICY) {
				remote = true;
			} else if (code == CloseFrame.ABNORMAL_CLOSE) {
				remote = false;
			} else {
				code = CloseFrame.NEVER_CONNECTED;
				remote = false;
			}
		}

		addEvent("CLOSE");
		onDisconnect(code, message, remote);

		incompleteframe = null;
		readystate = READYSTATE.CLOSED;
	}

	// **************************************************************************
	// ** write
	// **************************************************************************
	/**
	 * Used to send bytes to the client. This implementation relies on the
	 * HttpServletRequest class.
	 */
	private void write(ByteBuffer buf, int length) throws IOException {
		if (request.isEncrypted()) {
			buf = request.wrap(buf);
			length = buf.capacity();
		}
		connection.write(buf, length);
	}

	// **************************************************************************
	// ** decodeFrames
	// **************************************************************************
	/**
	 * Decodes raw bytes into a list of Frames.
	 */
	private List<Frame> decodeFrames(ByteBuffer buffer) throws FrameException {
		while (true) {
			List<Frame> frames = new LinkedList<Frame>();
			Frame cur;

			if (incompleteframe != null) {
				// complete an incomplete frame
				try {
					buffer.mark();
					int available_next_byte_count = buffer.remaining();// The
					                                                   // number
					                                                   // of
					                                                   // bytes
					                                                   // received
					int expected_next_byte_count = incompleteframe.remaining();// The
					                                                           // number
					                                                           // of
					                                                           // bytes
					                                                           // to
					                                                           // complete
					                                                           // the
					                                                           // incomplete
					                                                           // frame

					if (expected_next_byte_count > available_next_byte_count) {
						// did not receive enough bytes to complete the frame
						incompleteframe.put(buffer.array(), buffer.position(), available_next_byte_count);
						buffer.position(buffer.position() + available_next_byte_count);
						return Collections.emptyList();
					}
					incompleteframe.put(buffer.array(), buffer.position(), expected_next_byte_count);
					buffer.position(buffer.position() + expected_next_byte_count);
					cur = Frame.translateSingleFrame((ByteBuffer) incompleteframe.duplicate().position(0));
					frames.add(cur);
					incompleteframe = null;
				} catch (IncompleteFrameException e) {
					// extending as much as suggested
					ByteBuffer extendedframe = ByteBuffer.allocate(checkAlloc(e.getPreferredSize()));
					assert (extendedframe.limit() > incompleteframe.limit());
					incompleteframe.rewind();
					extendedframe.put(incompleteframe);
					incompleteframe = extendedframe;
					continue;
				}
			}

			while (buffer.hasRemaining()) {// Read as much as possible full
			                               // frames
				buffer.mark();
				try {
					cur = Frame.translateSingleFrame(buffer);
					if (cur == null)
						return frames;
					frames.add(cur);
				} catch (IncompleteFrameException e) {
					// remember the incomplete data
					buffer.reset();
					int pref = e.getPreferredSize();
					incompleteframe = ByteBuffer.allocate(checkAlloc(pref));
					incompleteframe.put(buffer);
					break;
				}
			}
			return frames;
		}
	}

	// **************************************************************************
	// ** processFrame
	// **************************************************************************
	/**
	 * Used to process a given frame from the client and call the corresponding
	 * event listeners (e.g. onText).
	 */
	private void processFrame(Frame frame) throws FrameException, IOException {
		Frame.Opcode curop = frame.getOpcode();
		if (curop == Frame.Opcode.CLOSING) {

			int code = CloseFrame.NOCODE;
			String reason = "";
			if (frame instanceof CloseFrame) {
				CloseFrame cf = (CloseFrame) frame;
				code = cf.getCloseCode();
				reason = cf.getMessage();
			}

			close(code, reason, true);
		} else if (curop == Frame.Opcode.PING) {
			send(new Frame.PongFrame((Frame.PingFrame) frame));
		} else if (curop == Frame.Opcode.PONG) {
			// this.lastPong = System.currentTimeMillis();
		} else if (!frame.isFin() || curop == Frame.Opcode.CONTINUOUS) {

			if (curop != Frame.Opcode.CONTINUOUS) {
				if (current_continuous_frame != null)
					throw new FrameException(CloseFrame.PROTOCOL_ERROR,
					        "Previous continuous frame sequence not completed.");
				current_continuous_frame = frame;
				byteBufferList.add(frame.getPayloadData());
			} else if (frame.isFin()) {
				if (current_continuous_frame == null)
					throw new FrameException(CloseFrame.PROTOCOL_ERROR, "Continuous frame sequence was not started.");
				byteBufferList.add(frame.getPayloadData());
				if (current_continuous_frame.getOpcode() == Frame.Opcode.TEXT) {
					current_continuous_frame.setPayload(getPayloadFromByteBufferList());
					current_continuous_frame.isValid();
					try {
						onText(UTF8.decode(current_continuous_frame.getPayloadData()));
					} catch (RuntimeException e) {
						onError(e);
					}
				} else if (current_continuous_frame.getOpcode() == Frame.Opcode.BINARY) {
					current_continuous_frame.setPayload(getPayloadFromByteBufferList());
					current_continuous_frame.isValid();
					try {
						onBinary(current_continuous_frame.getPayloadData());
					} catch (RuntimeException e) {
						onError(e);
					}
				}
				current_continuous_frame = null;
				byteBufferList.clear();
			} else if (current_continuous_frame == null) {
				throw new FrameException(CloseFrame.PROTOCOL_ERROR, "Continuous frame sequence was not started.");
			}

			// Check if the whole payload is valid utf8, when the opcode
			// indicates a text
			if (curop == Frame.Opcode.TEXT) {
				if (!UTF8.isValid(frame.getPayloadData())) {
					throw new FrameException(CloseFrame.NO_UTF8);
				}
			}

			// Checking if the current continous frame contains a correct
			// payload with the other frames combined
			if (curop == Frame.Opcode.CONTINUOUS && current_continuous_frame != null) {
				byteBufferList.add(frame.getPayloadData());
			}
		} else if (current_continuous_frame != null) {
			throw new FrameException(CloseFrame.PROTOCOL_ERROR, "Continuous frame sequence not completed.");
		} else if (curop == Frame.Opcode.TEXT) {
			try {
				onText(UTF8.decode(frame.getPayloadData()));
			} catch (RuntimeException e) {
				onError(e);
			}
		} else if (curop == Frame.Opcode.BINARY) {
			try {
				onBinary(frame.getPayloadData());
			} catch (RuntimeException e) {
				onError(e);
			}
		} else {
			throw new FrameException(CloseFrame.PROTOCOL_ERROR, "non control or continious frame expected");
		}
	}

	// **************************************************************************
	// ** getPayloadFromByteBufferList
	// **************************************************************************
	/**
	 * Method to generate a full bytebuffer out of all the fragmented frame
	 * payload.
	 */
	private ByteBuffer getPayloadFromByteBufferList() throws FrameException {
		long totalSize = 0;
		for (ByteBuffer buffer : byteBufferList) {
			totalSize += buffer.limit();
		}
		if (totalSize > Integer.MAX_VALUE) {
			throw new FrameException("Payloadsize is to big...");
		}
		ByteBuffer resultingByteBuffer = ByteBuffer.allocate((int) totalSize);
		for (ByteBuffer buffer : byteBufferList) {
			resultingByteBuffer.put(buffer);
		}
		resultingByteBuffer.flip();
		return resultingByteBuffer;
	}

	// **************************************************************************
	// ** checkAlloc
	// **************************************************************************
	private int checkAlloc(int bytecount) throws FrameException {
		if (bytecount < 0)
			throw new FrameException(CloseFrame.PROTOCOL_ERROR, "Negative count");
		return bytecount;
	}

	// **************************************************************************
	// ** Base64
	// **************************************************************************
	/**
	 * Attempts to encode/decode using the Base64 libraries/utilities that ship
	 * with most distributions of Java. Supports Java 1.6 - 1.9+
	 */
	private static class Base64 {

		private static final Class[] ByteArray = new Class[] { byte[].class };
		private static Class<?> cls;

		private static class JDK {
			private static int majorVersion;
			private static int minorVersion;
			static {
				String[] arr = System.getProperty("java.version").split("\\.");
				majorVersion = Integer.valueOf(arr[0]);
				minorVersion = Integer.valueOf(arr[1]);
				try {
					if (JDK.majorVersion == 1 && JDK.minorVersion < 8) {
						cls = Class.forName("javax.xml.bind.DatatypeConverter");
					} else {
						cls = Class.forName("java.util.Base64");
					}
				} catch (Throwable e) {
				}
			}
		}

		public static String encode(byte[] b) throws java.io.IOException {
			try {
				if (JDK.majorVersion == 1 && JDK.minorVersion < 8) {
					try {
						Class<?> cls = Class.forName("sun.misc.BASE64Encoder");
						Object obj = cls.newInstance();
						return (String) cls.getMethod("encode", ByteArray).invoke(obj, b);
					} catch (Throwable e) {
						return (String) cls.getMethod("printBase64Binary", ByteArray).invoke(null, b);
					}
				} else {
					Object encoder = cls.getMethod("getEncoder").invoke(null, null);
					return (String) encoder.getClass().getMethod("encodeToString", ByteArray).invoke(encoder, b);
				}
			} catch (Exception e) {
				throw new java.io.IOException(e);
			}
		}

		public static byte[] decode(String str) throws java.io.IOException {
			try {
				if (JDK.majorVersion == 1 && JDK.minorVersion < 8) {
					try {
						Class<?> cls = Class.forName("sun.misc.BASE64Decoder");
						Object obj = cls.newInstance();
						return (byte[]) cls.getMethod("decodeBuffer", String.class).invoke(obj, str);
					} catch (Throwable e) {
						return (byte[]) cls.getMethod("parseBase64Binary", String.class).invoke(null, str);
					}
				} else {
					Object decoder = cls.getMethod("getDecoder").invoke(null, null);
					return (byte[]) decoder.getClass().getMethod("decode", String.class).invoke(decoder, str);
				}
			} catch (Exception e) {
				throw new java.io.IOException(e);
			}
		}
	}

}