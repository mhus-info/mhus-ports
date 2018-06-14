package javaxt.websocket;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Random;

//******************************************************************************
//**  Frame
//******************************************************************************
/**
 * Used to represent a message sent or received via a websocket connection.
 * Websocket messages are encapsulated in frames. There are 2 types of frames:
 * Control Frames (e.g. CloseFrame) and Data Frames (e.g. TextFrame). This class
 * provides abstract methods to create frames and includes subclasses that
 * implement all standard frame types defined in RFC 6455.
 *
 ******************************************************************************/

public abstract class Frame {

	/**
	 * Enum which contains the different valid opcodes
	 */
	public enum Opcode {
		CONTINUOUS, TEXT, BINARY, PING, PONG, CLOSING
	}

	/**
	 * Indicates that this is the final fragment in a message.
	 */
	private boolean fin;
	/**
	 * Defines the interpretation of the "Payload data".
	 */
	private Opcode optcode;

	/**
	 * The unmasked "Payload data" which was sent in this frame
	 */
	private ByteBuffer unmaskedpayload;

	/**
	 * Defines whether the "Payload data" is masked.
	 */
	private boolean transferemasked;

	/**
	 * Indicates that the rsv1 bit is set or not
	 */
	private boolean rsv1;

	/**
	 * Indicates that the rsv2 bit is set or not
	 */
	private boolean rsv2;

	/**
	 * Indicates that the rsv3 bit is set or not
	 */
	private boolean rsv3;

	/**
	 * Check if the frame is valid due to specification
	 *
	 * @throws FrameException
	 *             thrown if the frame is not a valid frame
	 */
	public abstract void isValid() throws FrameException;

	/**
	 * Constructor for a FramedataImpl without any attributes set apart from the
	 * opcode
	 *
	 * @param op
	 *            the opcode to use
	 */
	public Frame(Opcode op) {
		optcode = op;
		unmaskedpayload = ByteBuffer.allocate(0);
		fin = true;
		transferemasked = false;
		rsv1 = false;
		rsv2 = false;
		rsv3 = false;
	}

	public boolean isRSV1() {
		return rsv1;
	}

	public boolean isRSV2() {
		return rsv2;
	}

	public boolean isRSV3() {
		return rsv3;
	}

	public boolean isFin() {
		return fin;
	}

	public Opcode getOpcode() {
		return optcode;
	}

	public boolean getTransfereMasked() {
		return transferemasked;
	}

	public ByteBuffer getPayloadData() {
		return unmaskedpayload;
	}

	public void append(Frame nextframe) {
		ByteBuffer b = nextframe.getPayloadData();
		if (unmaskedpayload == null) {
			unmaskedpayload = ByteBuffer.allocate(b.remaining());
			b.mark();
			unmaskedpayload.put(b);
			b.reset();
		} else {
			b.mark();
			unmaskedpayload.position(unmaskedpayload.limit());
			unmaskedpayload.limit(unmaskedpayload.capacity());

			if (b.remaining() > unmaskedpayload.remaining()) {
				ByteBuffer tmp = ByteBuffer.allocate(b.remaining() + unmaskedpayload.capacity());
				unmaskedpayload.flip();
				tmp.put(unmaskedpayload);
				tmp.put(b);
				unmaskedpayload = tmp;

			} else {
				unmaskedpayload.put(b);
			}
			unmaskedpayload.rewind();
			b.reset();
		}
		fin = nextframe.isFin();

	}

	@Override
	public String toString() {
		return "Framedata{ optcode:" + getOpcode() + ", fin:" + isFin() + ", rsv1:" + isRSV1() + ", rsv2:" + isRSV2()
		        + ", rsv3:" + isRSV3() + ", payloadlength:[pos:" + unmaskedpayload.position() + ", len:"
		        + unmaskedpayload.remaining() + "], payload:"
		        + (unmaskedpayload.remaining() > 1000 ? "(too big to display)" : new String(unmaskedpayload.array()))
		        + "}";
	}

	/**
	 * Set the payload of this frame to the provided payload
	 *
	 * @param payload
	 *            the payload which is to set
	 */
	public void setPayload(ByteBuffer payload) {
		this.unmaskedpayload = payload;
	}

	/**
	 * Set the fin of this frame to the provided boolean
	 *
	 * @param fin
	 *            true if fin has to be set
	 */
	public void setFin(boolean fin) {
		this.fin = fin;
	}

	/**
	 * Set the rsv1 of this frame to the provided boolean
	 *
	 * @param rsv1
	 *            true if fin has to be set
	 */
	public void setRSV1(boolean rsv1) {
		this.rsv1 = rsv1;
	}

	/**
	 * Set the rsv2 of this frame to the provided boolean
	 *
	 * @param rsv2
	 *            true if fin has to be set
	 */
	public void setRSV2(boolean rsv2) {
		this.rsv2 = rsv2;
	}

	/**
	 * Set the rsv3 of this frame to the provided boolean
	 *
	 * @param rsv3
	 *            true if fin has to be set
	 */
	public void setRSV3(boolean rsv3) {
		this.rsv3 = rsv3;
	}

	/**
	 * Set the tranferemask of this frame to the provided boolean
	 *
	 * @param transferemasked
	 *            true if transferemasked has to be set
	 */
	public void setTransferemasked(boolean transferemasked) {
		this.transferemasked = transferemasked;
	}

	/**
	 * Get a frame with a specific opcode
	 *
	 * @param opcode
	 *            the opcode representing the frame
	 * @return the frame with a specific opcode
	 */
	public static Frame get(Opcode opcode) {
		if (opcode == null) {
			throw new IllegalArgumentException("Supplied opcode cannot be null");
		}
		switch (opcode) {
		case PING:
			return new PingFrame();
		case PONG:
			return new PongFrame();
		case TEXT:
			return new TextFrame();
		case BINARY:
			return new BinaryFrame();
		case CLOSING:
			return new CloseFrame();
		case CONTINUOUS:
			return new ContinuousFrame();
		default:
			throw new IllegalArgumentException("Supplied opcode is invalid");
		}
	}

	// **************************************************************************
	// ** DataFrame
	// **************************************************************************
	/**
	 * Class to represent a data frame.
	 */
	public static class DataFrame extends Frame {
		public DataFrame(Opcode opcode) {
			super(opcode);
		}

		@Override
		public void isValid() throws FrameException {
			// Nothing specific to check
		}
	}

	// **************************************************************************
	// ** ControlFrame
	// **************************************************************************
	/**
	 * Class to represent a control frame.
	 */
	public static abstract class ControlFrame extends Frame {
		public ControlFrame(Opcode opcode) {
			super(opcode);
		}

		@Override
		public void isValid() throws FrameException {
			if (!isFin()) {
				throw new FrameException("Control frame cant have fin==false set");
			}
			if (isRSV1()) {
				throw new FrameException("Control frame cant have rsv1==true set");
			}
			if (isRSV2()) {
				throw new FrameException("Control frame cant have rsv2==true set");
			}
			if (isRSV3()) {
				throw new FrameException("Control frame cant have rsv3==true set");
			}
		}
	}

	// **************************************************************************
	// ** PingFrame
	// **************************************************************************
	public static class PingFrame extends ControlFrame {
		public PingFrame() {
			super(Opcode.PING);
		}
	}

	// **************************************************************************
	// ** PongFrame
	// **************************************************************************
	public static class PongFrame extends ControlFrame {
		public PongFrame() {
			super(Opcode.PONG);
		}

		public PongFrame(PingFrame pingFrame) {
			super(Opcode.PONG);
			setPayload(pingFrame.getPayloadData());
		}
	}

	// **************************************************************************
	// ** TextFrame
	// **************************************************************************
	public static class TextFrame extends DataFrame {
		public TextFrame() {
			super(Opcode.TEXT);
		}

		@Override
		public void isValid() throws FrameException {
			super.isValid();
			if (!UTF8.isValid(getPayloadData())) {
				throw new FrameException(CloseFrame.NO_UTF8);
			}
		}
	}

	// **************************************************************************
	// ** BinaryFrame
	// **************************************************************************
	public static class BinaryFrame extends DataFrame {
		public BinaryFrame() {
			super(Opcode.BINARY);
		}
	}

	// **************************************************************************
	// ** ContinuousFrame
	// **************************************************************************
	public static class ContinuousFrame extends DataFrame {
		public ContinuousFrame() {
			super(Opcode.CONTINUOUS);
		}
	}

	// **************************************************************************
	// ** CloseFrame
	// **************************************************************************
	public static class CloseFrame extends ControlFrame {

		/**
		 * indicates a normal closure, meaning whatever purpose the connection
		 * was established for has been fulfilled.
		 */
		public static final int NORMAL = 1000;
		/**
		 * 1001 indicates that an endpoint is "going away", such as a server
		 * going down, or a browser having navigated away from a page.
		 */
		public static final int GOING_AWAY = 1001;
		/**
		 * 1002 indicates that an endpoint is terminating the connection due to
		 * a protocol error.
		 */
		public static final int PROTOCOL_ERROR = 1002;
		/**
		 * 1003 indicates that an endpoint is terminating the connection because
		 * it has received a type of data it cannot accept (e.g. an endpoint
		 * that understands only text data MAY send this if it receives a binary
		 * message).
		 */
		public static final int REFUSE = 1003;
		/*
		 * 1004: Reserved. The specific meaning might be defined in the future.
		 */
		/**
		 * 1005 is a reserved value and MUST NOT be set as a status code in a
		 * Close control frame by an endpoint. It is designated for use in
		 * applications expecting a status code to indicate that no status code
		 * was actually present.
		 */
		public static final int NOCODE = 1005;
		/**
		 * 1006 is a reserved value and MUST NOT be set as a status code in a
		 * Close control frame by an endpoint. It is designated for use in
		 * applications expecting a status code to indicate that the connection
		 * was closed abnormally, e.g. without sending or receiving a Close
		 * control frame.
		 */
		public static final int ABNORMAL_CLOSE = 1006;
		/**
		 * 1007 indicates that an endpoint is terminating the connection because
		 * it has received data within a message that was not consistent with
		 * the type of the message (e.g., non-UTF-8 [RFC3629] data within a text
		 * message).
		 */
		public static final int NO_UTF8 = 1007;
		/**
		 * 1008 indicates that an endpoint is terminating the connection because
		 * it has received a message that violates its policy. This is a generic
		 * status code that can be returned when there is no other more suitable
		 * status code (e.g. 1003 or 1009), or if there is a need to hide
		 * specific details about the policy.
		 */
		public static final int POLICY_VALIDATION = 1008;
		/**
		 * 1009 indicates that an endpoint is terminating the connection because
		 * it has received a message which is too big for it to process.
		 */
		public static final int TOOBIG = 1009;
		/**
		 * 1010 indicates that an endpoint (client) is terminating the
		 * connection because it has expected the server to negotiate one or
		 * more extension, but the server didn't return them in the response
		 * message of the WebSocket handshake. The list of extensions which are
		 * needed SHOULD appear in the /reason/ part of the Close frame. Note
		 * that this status code is not used by the server, because it can fail
		 * the WebSocket handshake instead.
		 */
		public static final int EXTENSION = 1010;
		/**
		 * 1011 indicates that a server is terminating the connection because it
		 * encountered an unexpected condition that prevented it from fulfilling
		 * the request.
		 **/
		public static final int UNEXPECTED_CONDITION = 1011;
		/**
		 * 1015 is a reserved value and MUST NOT be set as a status code in a
		 * Close control frame by an endpoint. It is designated for use in
		 * applications expecting a status code to indicate that the connection
		 * was closed due to a failure to perform a TLS handshake (e.g., the
		 * server certificate can't be verified).
		 **/
		public static final int TLS_ERROR = 1015;

		/**
		 * The connection had never been established
		 */
		public static final int NEVER_CONNECTED = -1;

		/**
		 * The connection had a buggy close (this should not happen)
		 */
		public static final int BUGGYCLOSE = -2;

		/**
		 * The connection was flushed and closed
		 */
		public static final int FLASHPOLICY = -3;

		/**
		 * The close code used in this close frame
		 */
		private int code;

		/**
		 * The close message used in this close frame
		 */
		private String reason;

		/**
		 * Constructor for a close frame
		 * <p>
		 * Using opcode closing and fin = true
		 */
		public CloseFrame() {
			super(Opcode.CLOSING);
			setReason("");
			setCode(CloseFrame.NORMAL);
		}

		public CloseFrame(int code, String reason) {
			this();
			setCode(code);
			setReason(reason);
		}

		/**
		 * Set the close code for this close frame
		 * 
		 * @param code
		 *            the close code
		 */
		public void setCode(int code) {
			this.code = code;
			// CloseFrame.TLS_ERROR is not allowed to be transfered over the
			// wire
			if (code == CloseFrame.TLS_ERROR) {
				this.code = CloseFrame.NOCODE;
				this.reason = "";
			}
			updatePayload();
		}

		/**
		 * Set the close reason for this close frame
		 * 
		 * @param reason
		 *            the reason code
		 */
		public void setReason(String reason) {
			if (reason == null) {
				reason = "";
			}
			this.reason = reason;
			updatePayload();
		}

		/**
		 * Get the used close code
		 *
		 * @return the used close code
		 */
		public int getCloseCode() {
			return code;
		}

		/**
		 * Get the message that closeframe is containing
		 *
		 * @return the message in this frame
		 */
		public String getMessage() {
			return reason;
		}

		@Override
		public String toString() {
			return super.toString() + "code: " + code;
		}

		@Override
		public void isValid() throws FrameException {
			super.isValid();
			if (code == CloseFrame.NO_UTF8 && reason == null) {
				throw new FrameException(CloseFrame.NO_UTF8);
			}
			if (code == CloseFrame.NOCODE && 0 < reason.length()) {
				throw new FrameException(PROTOCOL_ERROR, "A close frame must have a closecode if it has a reason");
			}
			// Intentional check for code != CloseFrame.TLS_ERROR just to make
			// sure even if the code earlier changes
			if ((code > CloseFrame.UNEXPECTED_CONDITION && code < 3000 && code != CloseFrame.TLS_ERROR)) {
				throw new FrameException(PROTOCOL_ERROR, "Trying to send an illegal close code!");
			}
			if (code == CloseFrame.ABNORMAL_CLOSE || code == CloseFrame.TLS_ERROR || code == CloseFrame.NOCODE
			        || code > 4999 || code < 1000 || code == 1004) {
				throw new FrameException("closecode must not be sent over the wire: " + code);
			}
		}

		@Override
		public void setPayload(ByteBuffer payload) {
			code = CloseFrame.NOCODE;
			reason = "";
			payload.mark();
			if (payload.remaining() == 0) {
				code = CloseFrame.NORMAL;
			} else if (payload.remaining() == 1) {
				code = CloseFrame.PROTOCOL_ERROR;
			} else {
				if (payload.remaining() >= 2) {
					ByteBuffer bb = ByteBuffer.allocate(4);
					bb.position(2);
					bb.putShort(payload.getShort());
					bb.position(0);
					code = bb.getInt();
				}
				payload.reset();
				try {
					int mark = payload.position();// because stringUtf8 also
					                              // creates a mark
					try {
						payload.position(payload.position() + 2);
						reason = UTF8.decode(payload);
					} catch (IllegalArgumentException e) {
						throw new FrameException(CloseFrame.NO_UTF8);
					} finally {
						payload.position(mark);
					}
				} catch (FrameException e) {
					code = CloseFrame.NO_UTF8;
					reason = null;
				}
			}
		}

		/**
		 * Update the payload to represent the close code and the reason
		 */
		private void updatePayload() {
			byte[] by = UTF8.encode(reason);
			ByteBuffer buf = ByteBuffer.allocate(4);
			buf.putInt(code);
			buf.position(2);
			ByteBuffer pay = ByteBuffer.allocate(2 + by.length);
			pay.put(buf);
			pay.put(by);
			pay.rewind();
			super.setPayload(pay);
		}

		@Override
		public ByteBuffer getPayloadData() {
			if (code == NOCODE)
				return ByteBuffer.allocate(0);
			return super.getPayloadData();
		}

	} // End CloseFrame Class

	private final Random reuseableRandom = new Random();

	public static Frame createFrame(String text, boolean mask) {
		TextFrame curframe = new TextFrame();
		curframe.setPayload(ByteBuffer.wrap(UTF8.encode(text)));
		curframe.setTransferemasked(mask);
		try {
			curframe.isValid();
		} catch (FrameException e) {
			// throw new NotSendableException( e );
		}
		return curframe;
	}

	// **************************************************************************
	// ** translateSingleFrame
	// **************************************************************************
	public static Frame translateSingleFrame(ByteBuffer buffer) throws IncompleteFrameException, FrameException {
		int maxpacketsize = buffer.remaining();
		int realpacketsize = 2;
		if (maxpacketsize < realpacketsize) {
			throw new IncompleteFrameException(realpacketsize);
		}
		byte b1 = buffer.get( /* 0 */ );
		boolean FIN = b1 >> 8 != 0;
		boolean rsv1 = false;
		boolean rsv2 = false;
		boolean rsv3 = false;
		if ((b1 & 0x40) != 0) {
			rsv1 = true;
		}
		if ((b1 & 0x20) != 0) {
			rsv2 = true;
		}
		if ((b1 & 0x10) != 0) {
			rsv3 = true;
		}
		byte b2 = buffer.get( /* 1 */ );
		boolean MASK = (b2 & -128) != 0;
		int payloadlength = (byte) (b2 & ~(byte) 128);
		Opcode optcode = getOpCode((byte) (b1 & 15));

		if (!(payloadlength >= 0 && payloadlength <= 125)) {
			if (optcode == Opcode.PING || optcode == Opcode.PONG || optcode == Opcode.CLOSING) {
				throw new FrameException("more than 125 octets");
			}
			if (payloadlength == 126) {
				realpacketsize += 2; // additional length bytes
				if (maxpacketsize < realpacketsize)
					throw new IncompleteFrameException(realpacketsize);
				byte[] sizebytes = new byte[3];
				sizebytes[1] = buffer.get( /* 1 + 1 */ );
				sizebytes[2] = buffer.get( /* 1 + 2 */ );
				payloadlength = new BigInteger(sizebytes).intValue();
			} else {
				realpacketsize += 8; // additional length bytes
				if (maxpacketsize < realpacketsize)
					throw new IncompleteFrameException(realpacketsize);
				byte[] bytes = new byte[8];
				for (int i = 0; i < 8; i++) {
					bytes[i] = buffer.get( /* 1 + i */ );
				}
				long length = new BigInteger(bytes).longValue();
				if (length > Integer.MAX_VALUE) {
					throw new FrameException("Payloadsize is to big...");
				} else {
					payloadlength = (int) length;
				}
			}
		}

		// int maskskeystart = foff + realpacketsize;
		realpacketsize += (MASK ? 4 : 0);
		// int payloadstart = foff + realpacketsize;
		realpacketsize += payloadlength;

		if (maxpacketsize < realpacketsize)
			throw new IncompleteFrameException(realpacketsize);

		ByteBuffer payload = ByteBuffer.allocate(checkAlloc(payloadlength));

		if (MASK) {
			byte[] maskskey = new byte[4];
			buffer.get(maskskey);
			for (int i = 0; i < payloadlength; i++) {
				payload.put((byte) (buffer.get( /* payloadstart + i */ ) ^ maskskey[i % 4]));
			}
		} else {
			try {
				payload.put(buffer.array(), buffer.position(), payload.limit());
				buffer.position(buffer.position() + payload.limit());
			} catch (UnsupportedOperationException e) {
				return null;
			}
		}

		Frame frame = Frame.get(optcode);
		frame.setFin(FIN);
		frame.setRSV1(rsv1);
		frame.setRSV2(rsv2);
		frame.setRSV3(rsv3);
		payload.flip();
		frame.setPayload(payload);
		isFrameValid(frame);
		// getExtension().decodeFrame(frame);

		frame.isValid();
		return frame;
	}

	// **************************************************************************
	// ** checkAlloc
	// **************************************************************************
	private static int checkAlloc(int bytecount) throws FrameException {
		if (bytecount < 0)
			throw new FrameException(CloseFrame.PROTOCOL_ERROR, "Negative count");
		return bytecount;
	}

	// **************************************************************************
	// ** getByteBuffer
	// **************************************************************************
	public ByteBuffer getByteBuffer(boolean mask) throws java.io.IOException {

		Frame framedata = this;

		ByteBuffer mes = framedata.getPayloadData();

		int sizebytes = mes.remaining() <= 125 ? 1 : mes.remaining() <= 65535 ? 2 : 8;
		ByteBuffer buf = ByteBuffer
		        .allocate(1 + (sizebytes > 1 ? sizebytes + 1 : sizebytes) + (mask ? 4 : 0) + mes.remaining());
		byte optcode = getByte(framedata.getOpcode());
		byte one = (byte) (framedata.isFin() ? -128 : 0);
		one |= optcode;
		buf.put(one);
		byte[] payloadlengthbytes = toByteArray(mes.remaining(), sizebytes);
		assert (payloadlengthbytes.length == sizebytes);

		if (sizebytes == 1) {
			buf.put((byte) (payloadlengthbytes[0] | (mask ? (byte) -128 : 0)));
		} else if (sizebytes == 2) {
			buf.put((byte) ((byte) 126 | (mask ? (byte) -128 : 0)));
			buf.put(payloadlengthbytes);
		} else if (sizebytes == 8) {
			buf.put((byte) ((byte) 127 | (mask ? (byte) -128 : 0)));
			buf.put(payloadlengthbytes);
		} else
			throw new RuntimeException("Size representation not supported/specified");

		if (mask) {
			ByteBuffer maskkey = ByteBuffer.allocate(4);
			maskkey.putInt(reuseableRandom.nextInt());
			buf.put(maskkey.array());
			for (int i = 0; mes.hasRemaining(); i++) {
				buf.put((byte) (mes.get() ^ maskkey.get(i % 4)));
			}
		} else {
			buf.put(mes);
			// Reset the position of the bytebuffer e.g. for additional use
			mes.flip();
		}
		assert (buf.remaining() == 0) : buf.remaining();

		return buf;
	}

	private byte[] toByteArray(long val, int bytecount) {
		byte[] buffer = new byte[bytecount];
		int highest = 8 * bytecount - 8;
		for (int i = 0; i < bytecount; i++) {
			buffer[i] = (byte) (val >>> (highest - 8 * i));
		}
		return buffer;
	}

	// **************************************************************************
	// ** getByte
	// **************************************************************************
	/**
	 * Returns a single byte representing a Frame's opcode.
	 */
	private byte getByte(Frame.Opcode opcode) {
		if (opcode == Frame.Opcode.CONTINUOUS)
			return 0;
		else if (opcode == Frame.Opcode.TEXT)
			return 1;
		else if (opcode == Frame.Opcode.BINARY)
			return 2;
		else if (opcode == Frame.Opcode.CLOSING)
			return 8;
		else if (opcode == Frame.Opcode.PING)
			return 9;
		else if (opcode == Frame.Opcode.PONG)
			return 10;
		throw new IllegalArgumentException("Unknown Opcode: " + opcode.toString());
	}

	private static void isFrameValid(Frame inputFrame) throws FrameException {
		if (inputFrame.isRSV1() || inputFrame.isRSV2() || inputFrame.isRSV3()) {
			throw new FrameException("bad rsv" + " RSV1: " + inputFrame.isRSV1() + " RSV2: " + inputFrame.isRSV2()
			        + " RSV3: " + inputFrame.isRSV3());
		}
	}

	// **************************************************************************
	// ** getOpCode
	// **************************************************************************
	/**
	 * Returns a Frame opcode associated with a given byte
	 */
	private static Opcode getOpCode(byte opcode) throws FrameException {
		switch (opcode) {
		case 0:
			return Opcode.CONTINUOUS;
		case 1:
			return Opcode.TEXT;
		case 2:
			return Opcode.BINARY;
		// 3-7 are not yet defined
		case 8:
			return Opcode.CLOSING;
		case 9:
			return Opcode.PING;
		case 10:
			return Opcode.PONG;
		// 11-15 are not yet defined
		default:
			throw new FrameException("Unknown opcode " + (short) opcode);
		}
	}
}