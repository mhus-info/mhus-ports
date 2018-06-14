package javaxt.http;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import de.mhus.lib.core.MLog;
import de.mhus.lib.core.MThread;
import javaxt.http.servlet.HttpServlet;
import javaxt.http.servlet.HttpServletRequest;
import javaxt.http.servlet.HttpServletResponse;
import javaxt.http.servlet.ServletException;
import javaxt.http.servlet.WebSocketListener;

//******************************************************************************
//**  JavaXT Http Server
//******************************************************************************
/**
 *   A lightweight, multi-threaded web server used to process HTTP requests
 *   and send responses back to the client. 
 *
 *   The server requires an implementation of the HttpServlet class. As new 
 *   requests come in, they are passed to the HttpServlet.processRequest()
 *   method which is used to generate a response.
 *
 ******************************************************************************/

public class Server extends MLog {
    
    private int numThreads;
    private InetSocketAddress[] addresses;
    private HttpServlet servlet;
	private List<Thread> threads = new LinkedList<>();

    /** Maximum time that socket connections can remain idle. */
    private int maxIdleTime = 2*60000; //2 minutes

    private boolean running = true;
   
    private List<SocketConnection> requestProcessorConnections = new LinkedList<SocketConnection>();

    private void addRequestProcessor(SocketConnection connection){
        synchronized(requestProcessorConnections){
            requestProcessorConnections.add(connection);
            requestProcessorConnections.notifyAll();
        }
    }

    private List<SocketConnection> socketMonitorConnections = new LinkedList<SocketConnection>();
	private boolean allowKeepAlive = false;
	public List<SocketListener> sockets = new LinkedList<>();
	
    private void addMonitorSocket(SocketConnection connection){
        synchronized(socketMonitorConnections){
            socketMonitorConnections.add(connection);
            socketMonitorConnections.notifyAll();
        }
    }
    
    @SuppressWarnings("deprecation")
	public void stop() {
    	running = false;
    	log().i("Close JavaXT Server");
    	synchronized (socketMonitorConnections) {
    		socketMonitorConnections.notifyAll();
	    	for (SocketConnection con : socketMonitorConnections)
	    		if (con.isOpen())
					try {
						con.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    	}
    	synchronized (requestProcessorConnections) {
    		requestProcessorConnections.notifyAll();
	    	for (SocketConnection con : requestProcessorConnections)
	    		if (con.isOpen())
					try {
						con.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    	}
    	MThread.sleep(500);
    	for (SocketListener socket : sockets) {
    		String url = "http://" + socket.address.getHostName() + ":" + socket.address.getPort();
    		log().i("Send exit connect to " + url);
    		try {
    			HttpURLConnection huc = (HttpURLConnection)(new URL(url)).openConnection();
    			HttpURLConnection.setFollowRedirects(false);
    			huc.setConnectTimeout(15 * 1000);
    			huc.setRequestMethod("GET");
		        huc.connect();
		        huc.disconnect();
		   } catch (Throwable t) {}
    	}
    	sockets.clear();
    	MThread.sleep(500);
    	for (Thread thread : threads)
    		if (thread.isAlive())
    			thread.stop();
    }

  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to instantiate the Server on a given port.
   */
    public Server(int port, int numThreads, HttpServlet servlet) {
        this(new InetSocketAddress(port), numThreads, servlet);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to instantiate the Server on a given port and IP address.
   */
    public Server(InetSocketAddress address, int numThreads, HttpServlet servlet){
        this(new InetSocketAddress[]{address}, numThreads, servlet);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to instantiate the Server on multiple ports and/or IP addresses.
   */
    public Server(InetSocketAddress[] addresses, int numThreads, HttpServlet servlet){
        this.addresses = addresses;
        this.numThreads = numThreads;
        this.servlet = servlet;
    }

    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to instantiate the Server on multiple ports and/or IP addresses.
   */
    public Server(List<InetSocketAddress> addresses, int numThreads, HttpServlet servlet){
        this(addresses.toArray(new InetSocketAddress[addresses.size()]), numThreads, servlet);
    }
    
    
  //**************************************************************************
  //** Main
  //**************************************************************************
  /** Entry point for the application. Accepts command line arguments to 
   *  specify which port to use and the maximum number of concurrent threads.
   *
   *  @param args Command line arguments. Options include: 
   *  <ul>
   *  <li>-p to specify which port(s) to run on</li>
   *  <li>-debug to specify whether to output debug messages to the standard 
   *  output stream.
   *  </li>
   *  <li>-dir to specify a path to a directory where html, js, css, images are
   *  found. The server will server content from this directory to web clients.
   *  </li>
   *  </ul>
   */
//    public static void main(String[] args) throws Exception {
//
//
//      //Set local variables
//        java.io.File dir = null;
//        InetSocketAddress[] addresses = null;
//        
//        
//      //Parse inputs
//        if (args.length>0){
//            
//            if (args.length==1){
//                addresses = getAddresses(args[0]);
//            }
//            else{
//                
//                for (int i=0; i<args.length; i++){
//                    String key = args[i];
//                    if (!key.startsWith("-")) continue;
//                    String val = (i<args.length-1) ? args[i+1] : null;
//                    if (val!=null && !val.startsWith("-")){
//                        i++;
//                        
//                        if (key.startsWith("-p")){
//                            addresses = getAddresses(val);
//                        }
//                        else if (key.startsWith("-debug")){
//                            if (val.equalsIgnoreCase("true")) debug = true;
//                        }
//                        else if (key.startsWith("-dir")){
//                            java.io.File f = new java.io.File(val);
//                            if (f.exists()){
//                                if (f.isFile()) f = f.getParentFile();
//                                dir = f;
//                            }
//                        }
//                        else if (key.startsWith("-keystore")){
//                            //prompt for key pass?
//                        }
//                    }
//                }
//            }
//        }
//
//        
//      //If we're still here, and addresses are null, specify default addresses
//      //for the server to use
//        if (addresses==null) addresses = new InetSocketAddress[]{
//            new InetSocketAddress(80),
//            new InetSocketAddress(443)
//        };
//        
//
//      //Instantiate the server with the default/test servlet
//        Server webserver = new Server(addresses, 250, new ServletTest(dir));
//        webserver.start();
//    }
    
    
  //**************************************************************************
  //** getAddresses
  //**************************************************************************
  /** Used to parse command line inputs and return a list of socket addresses.
   */
    private static InetSocketAddress[] getAddresses(String str) throws IllegalArgumentException {
        java.util.ArrayList<InetSocketAddress> addresses = 
        new java.util.ArrayList<InetSocketAddress>();
        
        for (String s : str.split(",")){
            try{
                int port = Integer.parseInt(s);
                if (port < 0 || port > 65535) throw new Exception();
                addresses.add(new InetSocketAddress(port));
            }
            catch(Exception e){
                throw new IllegalArgumentException();
            }
        }
        return addresses.toArray(new InetSocketAddress[addresses.size()]);
    }
    
    
  //**************************************************************************
  //** Run
  //**************************************************************************
  /** Used to start the web server. Creates a thread pool and instantiates a
   *  socket listener for each specified port/address.
   */
    public void start() {

      //Create Thread Pool
        for (int i=0; i<numThreads; i++) {
            addThread(new Thread(new RequestProcessor())).start();
        }


      //Set up timer task to shutdown idle connections
        java.util.Timer timer = new java.util.Timer();
        timer.scheduleAtFixedRate(new SocketMonitor(), maxIdleTime, maxIdleTime);

        
      //Create a new SocketListener for each port/address
        for (InetSocketAddress address : addresses){
            addThread(new Thread(new SocketListener(address))).start();
        }

        
      //Call init
        if (servlet!=null) try{ servlet.init(null);} catch(Exception e){}

    }


  private Thread addThread(Thread thread) {
	  threads .add(thread);
	return thread;
}


//**************************************************************************
  //** SocketListener
  //**************************************************************************
  /** Thread used to open a socket and accept client connections. Inbound
   *  requests (client connections) are added to a queue and processed by the
   *  next available RequestProcessor thread. Idle connections are
   *  automatically closed after 5 minutes.
   */
    private class SocketListener implements Runnable {

        private InetSocketAddress address;
        public SocketListener(InetSocketAddress address){
            this.address = address;
        }

        public void run() {
        	sockets.add(this);
            String hostName = address.getHostName();
            if (hostName.equals("0.0.0.0") || hostName.equals("127.0.0.1")) hostName = "localhost";
            hostName += ":" + address.getPort();
            System.out.print("Accepting connections on " + hostName + "\r\n");


            Selector selector;
            ServerSocketChannel server = null;
    		try {

              //Create the selector
                selector = Selector.open();

              //Create a non-Blocking Server Socket Channel
                server = ServerSocketChannel.open();
                server.configureBlocking(false);
                server.socket().bind(address);
                server.register(selector, SelectionKey.OP_ACCEPT);
                
            }
            catch (java.io.IOException e) {
            	log().d("Failed to create listener for",hostName,e);
                //e.printStackTrace();
                return;
            }


          //Pass Inbound Request to the RequestProcessor
            while (running) {
                
                SelectionKey key = null;
                try {
                    
                    if (selector.select()==0) continue;
                    java.util.Set<SelectionKey> keys = selector.selectedKeys();
                    java.util.Iterator<SelectionKey> it = keys.iterator();


                  //Process keys
                    while (it.hasNext()) {

                      //Get the selection key
                        key = it.next();

                        
                      //Remove it from the list to indicate that it is being processed
                        it.remove();

                        
                      //Check whether the key is valid
                        if (!key.isValid()) {
                            continue;
                        }

                        
                      //Process new connections to the server
                        if (key.isAcceptable()) {

                          //Accept the connection
                            ServerSocketChannel s = (ServerSocketChannel) key.channel();
                            SocketChannel client = s.accept();
                            client.configureBlocking(false);
                            SocketConnection connection = new SocketConnection(client, selector);
                            
                            
                          //Add the new connection to the list of active connections
                            addMonitorSocket(connection);


                          //Register for read events
                            client.register(selector, SelectionKey.OP_READ, connection);
                            continue;
                        }
                        
                        
                      //Process read key 
                        if (key.isReadable()) {

                            SocketConnection connection = (SocketConnection) key.attachment();
                            synchronized(connection){
                                if (connection.isIdle.get()){
                                    connection.isIdle.set(false);
                                    addRequestProcessor(connection);
                                }
                                else{
                                    connection.onReadable();
                                }
                            }
                            continue;
                        }
                        
                        
                      //Process write key
                        if (key.isWritable()) { 
                            
                            SocketConnection connection = (SocketConnection) key.attachment();
                            connection.onWritable();
                        }
                    }
                    
                }
                catch (Throwable e) {
                	log().w(e);

                    
                  //Close the connection
                    try{
                        SocketConnection connection = (SocketConnection) key.attachment();
                        connection.close();
                    }
                    catch(Exception ex){}
                    
                    
                  //In the rare event that channel didn't close via the 
                  //SocketConnection (e.g. NPE), close the socket channel using
                  //the key.
                    try{
                        key.channel().close();
                    }
                    catch(Exception ex){}
                }
            }
            log().i("Close " + hostName + "\r\n");
            try {
            	if (server != null)
            		server.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

        }
    }


  //**************************************************************************
  //** RequestProcessor
  //**************************************************************************
  /** Thread used to process HTTP Requests and send responses back to the 
   *  client. As new HTTP requests come in they are added to a queue. Requests 
   *  in the queue are processed by instances of this class via the run method.
   */
    private class RequestProcessor implements Runnable {

        public RequestProcessor(){
        }

        public void run() {
            while (running) {

              //Wait for new requests to be added to the pool
                SocketConnection connection;
                synchronized (requestProcessorConnections) {
                    while (requestProcessorConnections.isEmpty()) {
                        try {
                            requestProcessorConnections.wait();
                        }
                        catch (InterruptedException e) {
                            return;
                        }
                    }
                    connection = requestProcessorConnections.remove(0);
                }


              //Process request and send a response back to the client
                HttpServletRequest request = null;
                HttpServletResponse response = null;
                try {
                    if (servlet!=null){
                        request = new HttpServletRequest(connection, servlet);
                        response = new HttpServletResponse(request, connection);
                        connection.onWritable();
                        servlet.service(request, response);
                    }
                }
                catch (ServletException e){
                	log().w(e);
                    if (request!=null){
                        response = new HttpServletResponse(request, connection);
                        response.setStatus(e.getStatusCode(), e.getMessage());
                    }
                    else{
                      //TODO: Need to propgate error to the client!
                        
                    }
                }
                catch (java.lang.OutOfMemoryError e){
                    log().e(e.toString());
                    return;
                }
                catch (Throwable e) {
                	log().d(e);
                }


              //Flush the response
                if (response!=null) {
                	response.flushBuffer();
                	response.closeBuffer();
                }
                
                
                
              //Check whether the channel is registered for write events. If so
              //notify the channel that we are no longer interested in write 
              //events. Otherwise the selector will loop indefinately.
                if (connection.opWrite){
                    try{
                        connection.socketChannel.register(
                        		connection.selector, SelectionKey.OP_READ, connection);
                    }
                    catch(Exception e){}
                }


              //Close the socket connection (as needed)
                boolean isKeepAlive = (request!=null ? request.isKeepAlive() : false);
                if (!isAllowKeepAlive())
                	isKeepAlive = false; // TODO currently not working without closeing the connection...
                if (!isKeepAlive){
                    try {
                        connection.close();
                    }
                    catch (java.io.IOException e) {}
                }

                
              //Mark the connection as inactive
                synchronized(connection){
                    connection.isIdle.set(true);
                }
                

              //Destroy the request and response objects
                if (request!=null){
                    request.clear();
                    request = null;
                }
                if (response!=null){
                    response.reset();
                    response = null;
                }

            }
        }
    }


  //**************************************************************************
  //** SocketMonitor
  //**************************************************************************
  /**  TimerTask used to find and close idle connections.
   */
    private class SocketMonitor extends java.util.TimerTask {

        public void run(){

            long currTime = System.currentTimeMillis();

          //Find idle connections
            synchronized(socketMonitorConnections){
                java.util.ListIterator<SocketConnection> it = socketMonitorConnections.listIterator();
                while (it.hasNext()){
                    SocketConnection connection = it.next();
                    if (currTime-connection.lastEvent>maxIdleTime){
                        if (connection.isOpen()){
                            try{ connection.close(); }
                            catch(Exception e){}
                        }
                        it.remove();
                    }
                }
            }
        }
    }


  //**************************************************************************
  //** SocketConnection
  //**************************************************************************
  /** Simple wrapper for a SocketChannel. Logs reads/writes for the
   *  SocketMonitor and is used to associate an SSLEngine with the given
   *  SocketChannel.
   */
    public static class SocketConnection {

        private final long startTime;
        private Long lastEvent;
        private SocketChannel socketChannel;
        private Selector selector;
        private javax.net.ssl.SSLEngine sslEngine;
        private final String localhost;
        private final String localaddress;
        private final int localport;
        private final java.net.InetSocketAddress remoteSocketAddress;
        private final List<Listener> listeners;
        private final List<Long> read = new LinkedList<Long>();
        private final List<Long> write = new LinkedList<Long>();
        private final AtomicBoolean isIdle = new AtomicBoolean(true);
        private boolean opWrite = false;
        
        
        private SocketConnection(SocketChannel socketChannel, Selector selector){

            this.socketChannel = socketChannel;
            this.selector = selector;
            startTime = new java.util.Date().getTime();
            lastEvent = startTime;
            
            java.net.Socket socket = socketChannel.socket();
            localhost = socket.getLocalAddress().getCanonicalHostName();
            localport = socket.getLocalPort();
            localaddress = socket.getLocalAddress().getHostAddress();
            
            remoteSocketAddress = (java.net.InetSocketAddress) socket.getRemoteSocketAddress();
            listeners = new LinkedList<Listener>();
        }
        

        
      /** Returns true if the socketChannel is not null and is open. */
        public boolean isOpen(){
            if (socketChannel!=null) return socketChannel.isOpen();
            return false;
        }

        
      /** Called by the SocketListener to notify users that the socket is ready 
       *  for reading.
       */
        private void onReadable(){
            synchronized(read){
                long t = System.currentTimeMillis();
                if (read.isEmpty()) read.add(t);
                else read.set(0, t);
                read.notify();
            }
            synchronized(listeners){
                for (Listener listener : listeners){
                    listener.onReadable();
                }
            }
        }
        
        
      /** Called by the SocketListener to notify users that the socket is ready 
       *  for writing.
       */
        private void onWritable(){
            synchronized(write){
                long t = System.currentTimeMillis();
                if (write.isEmpty()) write.add(t);
                else write.set(0, t);
                write.notify();
            }
            synchronized(listeners){
                for (Listener listener : listeners){
                    listener.onWritable();
                }
            }
        }
        
      /** Called by the SocketListener to notify users that the socket is 
       *  being closed.
       */
        private void onClose(){
            synchronized(listeners){
                for (Listener listener : listeners){
                    listener.onClose();
                }
            }
        }
        
      /** Used to add a Listener to this socket connection. Example: 
          <pre>
            connection.addListener(new Server.SocketConnection.Listener(){
                public void onReadable(){
                    log("Read!");
                }
            });
          </pre>
       */
        public void addListener(Listener listener){
            synchronized(listeners){
                listeners.add(listener);
                listeners.notify();
            }
        }

      /** SocketConnection Listener class */
        public static class Listener {
            public void onReadable(){}
            public void onWritable(){}
            public void onClose(){}
        }
        

      /** Returns the client IP address. */
        public java.net.InetSocketAddress getRemoteSocketAddress(){
            return remoteSocketAddress;
        }


      /** Returns the host name of the Internet Protocol (IP) interface on
       *  which the request was received.
       */
        public String getLocalHost(){
            return localhost;
        }

      /** Returns the Internet Protocol (IP) address of the interface on which
       *  the request was received.
       */
        public String getLocalAddress(){
            return localaddress;
        }


      /** Returns the Internet Protocol (IP) port number of the interface on
       *  which the request was received.
       */
        public int getLocalPort(){
            return localport;
        }


      /** Used to read data from the SocketChannel. The method will return
       *  immediately after receiving bytes from the socket. Note that a 
       *  SocketChannel in non-blocking mode cannot read any more bytes than 
       *  are immediately available from the socket's input buffer. In this 
       *  case, this method will wait until the connection is readable. 
       *  Unfortunately, this is no way for this method to know whether the
       *  client is done sending data so no checksum is performed to ensure
       *  that all the bytes came across cleanly.
       */
        public int read(java.nio.ByteBuffer buffer) throws java.io.IOException {
            if (!isOpen()) throw new java.io.IOException("SocketConnection is closed!");
            

            int numBytesRead;
            synchronized (read) {
                numBytesRead = socketChannel.read(buffer);
                read.clear();
                if (numBytesRead==0){
                    while (read.isEmpty()) {
                        try {
                            read.wait();
                        }
                        catch (InterruptedException e) {
                            break;
                        }
                    }
                    numBytesRead = socketChannel.read(buffer);
                    read.clear();
                }
            }
            
            
            if (numBytesRead==-1){
                close();
                throw new java.io.IOException("Received -1 bytes. Socket is closed.");
            }


            lastEvent = new java.util.Date().getTime();
            return numBytesRead;
        }


      /** Used to write data to the SocketChannel. Note that a SocketChannel
       *  in non-blocking mode cannot write any more bytes than are free in the
       *  socket's output buffer. In this case, the server will wait until the
       *  connection becomes writable. Returns only after all of the bytes in 
       *  the buffer have been sent.
       *
       *  @param length Number of bytes in the buffer. This is used to ensure
       *  that all the bytes were sent to the client. Note that buffer length
       *  does not always equal buffer capacity.
       */
        public int write(java.nio.ByteBuffer buffer, int length) throws java.io.IOException {
            if (!isOpen()) throw new java.io.IOException("SocketConnection is closed!");


            int numBytesWrite = socketChannel.write(buffer);
            if (numBytesWrite==-1) throw new java.io.IOException("Socket is closed.");
            if (numBytesWrite<length) {
                
                synchronized (write) {
                    write.clear();
                    write.notify();
                }
                
                
              //Register for write events. Note that this will slow things down 
              //quite a bit. In my dev environment, performance drops from 11k
              //requests per second to about 8k per second. Also, note that we  
              //need to unregister from OP_WRITE events once we are done writing.
              //Otherwise, the SocketListener gets stuck in an infinite loop.
                if (!opWrite){
                    try{
                        socketChannel.register(
                        selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);
                        opWrite = true;
                    }
                    catch(Exception e){}
                }
                
                
              //Wait for write operations
                while (numBytesWrite<length) {
                    int x;
                    synchronized (write) {
                        while (write.isEmpty()) {
                            try {
                                write.wait();
                            }
                            catch (InterruptedException e) {
                                break;
                            }
                        }

                        x = socketChannel.write(buffer);
                        write.clear();
                        write.notify();
                    }

                    if (x==-1) throw new java.io.IOException("Socket is closed.");
                    numBytesWrite += x;
                    lastEvent = new java.util.Date().getTime();
                }

            }
            else{
                synchronized (write) {
                    write.clear();
                    write.notify();
                }
            }

            return numBytesWrite;
        }

        
      /** Used to close the socketChannel and update any listeners.*/
        public void close() throws java.io.IOException{
            if (socketChannel!=null){
                socketChannel.close();
            }
            socketChannel = null;
            sslEngine = null;
            onClose();
        }

        public javax.net.ssl.SSLEngine getSSLEngine(){
            return sslEngine;
        }

        public void setSSLEngine(javax.net.ssl.SSLEngine sslEngine){
            this.sslEngine = sslEngine;
        }

        public SocketChannel getChannel(){
            return socketChannel;
        }
        
    }


  //**************************************************************************
  //** ServletTest
  //**************************************************************************
  /** Simple implementation of an JavaXT HttpServlet. Simply returns the
   *  request headers and body back to the client in plain text.
   */
    private class ServletTest extends javaxt.http.servlet.HttpServlet {

        private final java.io.File dir;
        private final String s = System.getProperty("file.separator");
        
        public ServletTest(java.io.File dir) throws Exception {
            this.dir = dir;
            
            java.io.File keystore = new java.io.File("/temp/keystore.jks");
            if (keystore.exists()){
                setKeyStore(keystore, "password");
                setTrustStore(keystore, "password");
            }
        }
        
    	@Override
		public void service(ServletRequest req, ServletResponse res) throws javax.servlet.ServletException, IOException {
//        public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, java.io.IOException {
    		HttpServletRequest request = (HttpServletRequest) req;
    		HttpServletResponse response = (HttpServletResponse) res;
            
          //Print the requested URL
    		log().d("New Request From", request.getRemoteAddr(), request.getMethod(), ": ", request.getURL());


            
            if (request.isWebSocket()){

                new WebSocketListener(request, response){
                    public void onConnect(){
                        send("Hello There!");
                    }
                    public void onText(String str){
                        //System.out.println(str);
                        send("Message recieved at " + new java.util.Date());
                        
                    }
                    public void onDisconnect(int statusCode, String reason, boolean remote){
                        //System.out.println("Goodbye...");
                    }
                };
                return;
            }
            
            
            
            if (dir!=null){
            
              //Get requested path
                String path = request.getURL().getPath();
                if (path.length()>1 && path.startsWith("/")) path = path.substring(1);

                
              //Construct a physical file path using the url
                java.io.File file = new java.io.File(dir + s + path);        
                if (file.exists()){
                    if (file.isDirectory()){
                        file = new java.io.File(file, "index.html");
                    }
                }


              //If the file doesn't exist, return an error
                if (!file.exists()){
                    response.setStatus(404);
                }
                else{ //Dump the file content to the servlet output stream
                    String ext = null;
                    int x = file.getName().lastIndexOf(".");
                    if (x!=-1) ext = file.getName().substring(x+1).toLowerCase();
                    response.write(file, getContentType(ext), true);
                }
            }
            else{
            
              //Send sample http response to the client
                try{

                    byte[] header = request.toString().getBytes("UTF-8");
                    byte[] body = request.getBody();
                    byte[] msg = new byte[header.length + body.length];

                    System.arraycopy(header,0,msg,0,header.length);
                    System.arraycopy(body,0,msg,header.length,body.length);

                    header = null;
                    body = null;

                    response.setContentType("text/plain");
                    response.write(msg);

                    msg = null;

                }
                catch(Exception e){
                }
            }

            
            log().t(request);
            log().t(response);
        }
        
        
        private String getContentType(String ext){
            if (ext!=null) {
                if (ext.equals("css")) return "text/css";
                if (ext.equals("htm") || ext.equals("html")) return "text/html";
                if (ext.equals("js")) return "text/javascript";
                if (ext.equals("txt")) return "text/plain";
                if (ext.equals("gif")) return "image/gif";
                if (ext.equals("jpg")) return "image/jpeg";
                if (ext.equals("png")) return "image/png";
                if (ext.equals("ico")) return "image/vnd.microsoft.icon";
            }
            return "application/octet-stream";
        }
        
    }

    
  //**************************************************************************
  //** log
  //**************************************************************************
//  /** Used to log messages to the standard output stream when the server is
//   *  in debug mode.
//   */
//    public static void log(Object obj) {
//        if (!debug) return;
//        
//        String md = "[" + getTime() + "] ";
//        String padding = "";
//        for (int i=0; i<md.length(); i++){
//            padding+= " ";
//        }
//        String str;
//        if (obj instanceof String){
//            str = (String) obj;
//            if (str.length()>0){
//                String[] arr = str.split("\n");
//                for (int i=0; i<arr.length; i++){
//                    if (i==0) str = md + arr[i].trim() + "\r\n";
//                    else str += padding + arr[i].trim() + "\r\n";
//                }
//                str = str.trim();
//            }
//        }
//        else {
//            str = md + obj;
//        }
//        synchronized(System.out) { System.out.println(str); }
//    }
//    
    
//    private static String getTime(){
//        java.util.Date d = new java.util.Date();
//        return pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
//    }
    
//    private static String pad(int i){
//        if (i<10) return "0"+i;
//        else return i+"";
//    }

	public boolean isAllowKeepAlive() {
		return allowKeepAlive;
	}

	public void setAllowKeepAlive(boolean allowKeepAlive) {
		this.allowKeepAlive = allowKeepAlive;
	}

}// End server class