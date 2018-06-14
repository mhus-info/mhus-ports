package javaxt.http.servlet;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.nio.ByteBuffer;
import javaxt.http.Server.SocketConnection;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

import de.mhus.lib.core.MLog;
import javaxt.http.Server;

//******************************************************************************
//**  HttpServletRequest
//******************************************************************************
/**
 *   Used to read raw bytes sent from the client to the server. Assumes the
 *   data is a valid HTTP/1.1 request. Supports both http and https (ssl/tls).
 *   This class implements the javax.servlet.http.HttpServletRequest interface
 *   defined in Version 2.5 of the Java Servlet API.
 *
 ******************************************************************************/

public class HttpServletRequest extends MLog implements javax.servlet.http.HttpServletRequest {

    private String[] header; 
    private java.net.URL url;
    private String version;
    private String method;
    private HashMap<String, List<String>> parameters;
    private SocketConnection connection;
    private java.net.InetSocketAddress remoteSocketAddress;
    private static final int maxHeaderSize = 8192; //8KB
    private static final String[] methods = new String[]{
        "GET", "POST", "HEAD", "PUT", "OPTIONS", "TRACE", "DELETE"};
    private static final int mx = "OPTIONS".length()+1;
    private HttpSession session = null;
    private java.util.ArrayList<Cookie> cookies = null;
    private Boolean isKeepAlive;
    private Boolean isWebSocket;
    private static final int maxRecordSize = 33049;
    private SSLEngine sslEngine;
    private ByteBuffer appData;
    private ByteBuffer recordHeader;
    private ServletInputStream inputStream;
    private Integer contentLength = null;
    private java.nio.ByteBuffer oneByte = java.nio.ByteBuffer.allocateDirect(1);
    
    
  //The following variables are used for authentication
    private Authenticator authenticator;
    private boolean authenticate = true;
    private ServletException authenticationException = null;
    private java.security.Principal principal;
    private boolean getUserPrincipal = true;
    private boolean getCredentials = true;
    private String[] credentials = null;
    /** String identifier for "BASIC" authentication. */
    public static final String BASIC_AUTH = "BASIC";
    /** String identifier for "FORM" authentication. */
    public static final String FORM_AUTH = "FORM";
    /** String identifier for Client Certificate authentication.*/
    public static final String CLIENT_CERT_AUTH = "CLIENT_CERT";
    /** String identifier for "DIGEST" authentication. */
    public static final String DIGEST_AUTH = "DIGEST";
    
    
  //The following variables are used to implement methods defined in the  
  //Servlet spec. I frankly don't use these variables or methods but perhaps 
  //someone will find them useful...
    private String charset;
    private java.util.Enumeration<Locale> locales;
    private HashMap<String, Object> attributes;
    private String servletPath;
    private ServletContext servletContext;

    
  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Parses the first few lines of the http request. Stops when it reaches an
   *  empty line.
   */
    public HttpServletRequest(SocketConnection connection, HttpServlet servlet)
        throws ServletException, IOException {

        this.remoteSocketAddress = connection.getRemoteSocketAddress();
        this.connection = connection;        
        this.attributes = new HashMap<String, Object>();
        this.servletPath = servlet.servletPath;
        this.servletContext = servlet.getServletContext();
        

        int totalBytesRead = 0;
        String[] header = new String[30]; //<--max header length is set to 30 lines
        char[] row = new char[2048]; //<--max row length is set to 2048 characters
        int i = 0;
        int j = 0;
        boolean decrypt = false;


      //Extract the http header from the input stream, one byte at a time
        while (true) {

            byte a = nextByte(decrypt);
            totalBytesRead++;

          //If this is the first byte, check whether the request is SSL
            if (totalBytesRead==1 && ((a>19 && a<25) || a==-128)){


              //Read the next 4 bytes from the socket channel. This, plus the
              //first byte should contain TLS record information.
                recordHeader = ByteBuffer.allocateDirect(5);
                recordHeader.put(a);
                for (int x=0; x<4; x++){
                    recordHeader.put(nextByte(false));
                }
                recordHeader.rewind();


              //Check whether the TLS record is valid
                int tlsVersion = (int) recordHeader.get(1);
                if (a==-128) tlsVersion = (int) recordHeader.get(3);
                if (tlsVersion!=3) throw new ServletException("Unsupported TLS Version.");


              //If we're still here, get the SSLEngine associated with this
              //connection. Create a new SSLEngine as needed.
                sslEngine = connection.getSSLEngine();
                if (sslEngine==null){
                    sslEngine = servlet.getSSLEngine(connection.getLocalAddress(), connection.getLocalPort());
                    connection.setSSLEngine(sslEngine);
                }


              //Process the SSL/TLS record
                switch((int) a){
                    case 22: case -128: initHandshake(); break;
                    case 23: break;
                    default:
                        throw new ServletException("Unsupported TLS Record: " + a);
                }


              //Update flag to decrypt all subsequent bytes from the socket
                decrypt = true;

              //Read the next byte
                a = nextByte(decrypt);
            }



            if (a=='\r') {

                byte b = nextByte(decrypt);
                totalBytesRead++;
                
                if (b=='\n') {

                    if (j==0) break;
                    header[i] = new String(row, 0, j);
                    j = 0;
                    i++;
                }
                else if (b > -1) {
                    row[j] = (char) a; j++;
                    row[j] = (char) b; j++;
                }
            }
            else if (a > -1) {
                row[j] = (char) a; j++;
            }
            



          //Check row length, row count, and overall header length.
            if (j>row.length) throw new ServletException("Line " + i + " is too long.");
            if (i>header.length) throw new ServletException("Too many rows.");
            if (totalBytesRead>maxHeaderSize) throw new ServletException("Header is too big.");



          
          //Check the first few characters to see if this is a valid HTTP request.
            if (totalBytesRead==mx){
                boolean isValid = false;
                String str = new String(row, 0, j);
                for (String method : methods){
                    if (str.startsWith(method + " ")){
                        isValid = true;
                        break;
                    }
                }
                if (!isValid) throw new ServletException("Bad Request: " + str);
            }
        }
        
        row = null;



        if (totalBytesRead<mx){
            throw new ServletException("Bad Request");
        }
        


      //Prune the header array
        this.header = new String[i];
        System.arraycopy(header,0,this.header,0,i);
        header = null;


        parseHeader();
        
      //Instantiate the authenticator
        try{
            authenticator = servlet.getAuthenticator(this);
        }
        catch(Exception e){
            //TODO: Figure out how to propogate this error to the caller!
            e.printStackTrace();
        }
    }


  //**************************************************************************
  //** parseHeader
  //**************************************************************************
  /** Used to parse the HTTP request header.
   */
    private void parseHeader() throws IOException{

      //Parse the first line of the header to get the method, path, and version
        String host = getHeader("Host");
        String path = null;
        String protocol = null;
        
        if (host==null){
            host = getLocalName();
            int port = getLocalPort();
            host += (port!=80? ":"+port : "");
        }
        
        java.util.StringTokenizer st = new java.util.StringTokenizer(header[0]);
        if (st.hasMoreTokens()) method = st.nextToken().trim().toUpperCase();
        if (st.hasMoreTokens()) path = st.nextToken().trim();
        if (st.hasMoreTokens()) protocol = st.nextToken().trim().toUpperCase();

        if (protocol.contains("/")) {
            version = protocol.substring(protocol.indexOf("/")+1);
        }


      //Assemble requested url
        if (path.toLowerCase().startsWith("http://"+host.toLowerCase()) ||
            path.toLowerCase().startsWith("https://"+host.toLowerCase())){
            url = new java.net.URL(path);
        }
        else{
            url = new java.net.URL((isEncrypted() ? "https" : "http") + "://" + host + path);
        }
        
      //Parse query string
        parameters = parseQueryString(url.getQuery());
    }


  //**************************************************************************
  //** getRemoteAddr
  //**************************************************************************
  /** Returns the IP address of the client that sent the request.
   */
    public String getRemoteAddr(){
        return remoteSocketAddress.getAddress().getHostAddress();
    }


  //**************************************************************************
  //** getRemoteHost
  //**************************************************************************
  /** Returns the hostname of the client that sent the request. 
   */
    public String getRemoteHost(){
        return remoteSocketAddress.getHostName();     
    }


  //**************************************************************************
  //** getRemotePort
  //**************************************************************************
  /** Returns the port of the client that sent the request.
   */
    public int getRemotePort(){
        return remoteSocketAddress.getPort();
    }

    
  //**************************************************************************
  //** getHttpVersion
  //**************************************************************************
  /** Returns the HTTP version number passed in as part of the request (e.g. 
   *  "1.0", "1.1", etc).
   */
    public String getHttpVersion(){
        return version;
    }


  //**************************************************************************
  //** getHeader
  //**************************************************************************
  /** Returns the value of the specified request header as a String. If the
   *  request did not include a header of the specified name, this method
   *  returns null. If there are multiple headers with the same name, this
   *  method returns the first head in the request. The header name is case
   *  insensitive.
   * 
   *  @param name A String specifying the header name (e.g. "Content-Encoding")
   *  The header name is case insensitive. 
   */
    public String getHeader(String name){
        for (String entry : header){
            if (entry!=null){
                if (entry.contains(":")){
                    String key = entry.substring(0, entry.indexOf(":"));
                    if (key.equalsIgnoreCase(name)){
                        return entry.substring(entry.indexOf(":")+1).trim();
                    }
                }
            }
        }
        return null;
    }


  //**************************************************************************
  //** setHeader
  //**************************************************************************
    public void setHeader(String name, String value){
        for (int i=0; i<header.length; i++){
            String entry = header[i];
            if (entry!=null){
                if (entry.contains(":")){
                    String key = entry.substring(0, entry.indexOf(":"));
                    if (key.equalsIgnoreCase(name)){
                        header[i] = name + ": " + value;
                        return;
                    }
                }
            }
        }

      //If we're still here, simply add an entry to the end of the array
        String[] header = new String[this.header.length+1];
        System.arraycopy(this.header,0,header,0,this.header.length);
        header[header.length-1] = name + ": " + value;
        this.header = header;
    }


  //**************************************************************************
  //** getHeaders
  //**************************************************************************
  /** Returns all the values of the specified request header as an Enumeration.
   *  If the request did not include any headers of the specified name, this 
   *  method returns an empty Enumeration.
   *
   *  @param name A String specifying the header name (e.g. "Accept-Language").
   *  The header name is case insensitive. 
   */
    public java.util.Enumeration<String> getHeaders(String name){
        java.util.ArrayList<String> headers = new java.util.ArrayList<String>();
        for (String entry : header){
            if (entry!=null){
                if (entry.contains(":")){
                    String key = entry.substring(0, entry.indexOf(":"));
                    if (key.equalsIgnoreCase(name)){
                        headers.add(entry.substring(entry.indexOf(":")+1).trim());
                    }
                }
            }
        }
        return java.util.Collections.enumeration(headers);
    }


  //**************************************************************************
  //** getHeaderNames
  //**************************************************************************
  /** Returns an enumeration of all the header names this request contains. If 
   *  the request has no headers, this method returns an empty enumeration.
   */
    public java.util.Enumeration<String> getHeaderNames(){
        java.util.ArrayList<String> headers = new java.util.ArrayList<String>();
        for (String entry : header){
            if (entry!=null){
                if (entry.contains(":")){
                    String key = entry.substring(0, entry.indexOf(":"));
                    headers.add(key);
                }
            }
        }
        return java.util.Collections.enumeration(headers);
    }


  //**************************************************************************
  //** getIntHeader
  //**************************************************************************
  /** Returns the value of the specified request header as an int. If the
   *  request does not have a header of the specified name, this method 
   *  returns -1. If the header cannot be converted to an integer, this method
   *  throws a NumberFormatException.
   * 
   *  @param name A String specifying the header name (e.g. "Content-Length").
   *  The header name is case insensitive.
   */
    public int getIntHeader(String name) throws NumberFormatException {
        String val = getHeader(name);
        if (val==null) return -1;
        else return Integer.parseInt(val);
    }

    
  //**************************************************************************
  //** getDateHeader
  //**************************************************************************
  /** Returns the value of the specified request header as a long representing
   *  the number of milliseconds since January 1, 1970 GMT. If the request did 
   *  not have a header of the specified name, this method returns -1. If the 
   *  header can't be converted to a date, the method throws an
   *  IllegalArgumentException.
   *
   *  @param name A String specifying the header name (e.g. "If-Modified-Since").
   *  The header name is case insensitive.
   */
    public long getDateHeader(String name) throws IllegalArgumentException{
        String val = getHeader(name);
        if (val==null) return -1;

      //Set date format (e.g. "Sat, 29 Oct 1994 19:43:31 GMT")
        final String format = "EEE, dd MMM yyyy HH:mm:ss z"; 
        
        java.text.SimpleDateFormat formatter =
                new java.text.SimpleDateFormat(format, Locale.US); //locale?

        try{
            return formatter.parse(val).getTime();
        }
        catch(java.text.ParseException e){
            throw new IllegalArgumentException(e.getLocalizedMessage());
        }
    }


  //**************************************************************************
  //** getCharacterEncoding
  //**************************************************************************
  /** Returns the name of the character encoding used in the body of this
   *  request as specified in the "Content-Type" in the request header (e.g.
   *  "UTF-8"). Returns a null if the request does not specify a character 
   *  encoding.
   */
    public String getCharacterEncoding(){
        if (charset==null){
            String contentType = getContentType();
            if (contentType!=null){
                for (String str : contentType.split(";")){
                    str = str.trim();
                    if (str.startsWith("charset=")){
                        charset = str.substring(8);
                        break;
                    }
                }
            }
        }
        return charset;
    }


  //**************************************************************************
  //** setCharacterEncoding
  //**************************************************************************
  /** Overrides the name of the character encoding used in the body of this
   *  request. This method must be called prior to reading request parameters
   *  or reading input using getReader().
   */
    public void setCharacterEncoding(String env) throws java.io.UnsupportedEncodingException{
        if (charset==null || !charset.equalsIgnoreCase(env)){

          //Test the charset
            env.getBytes(env);

          //If we're still here, update the class variable
            charset = env;
        }
    }


  //**************************************************************************
  //** getContentType
  //**************************************************************************
  /** Returns the "Content-Type" defined in the request header. Returns null
   *  if the "Content-Type" is not defined.
   */
    public String getContentType(){
        return getHeader("Content-Type");
    }


  //**************************************************************************
  //** getLocale
  //**************************************************************************
  /** Returns the preferred Locale that the client will accept content in,
   *  based on the "Accept-Language" header. If the client request doesn't 
   *  provide an Accept-Language header, this method returns the default 
   *  locale for the server.
   */
    public Locale getLocale(){
        return getLocales().nextElement();
    }


  //**************************************************************************
  //** getLocales
  //**************************************************************************
  /** Returns an Enumeration of Locale objects indicating the locales that are 
   *  acceptable to the client based on the Accept-Language header. The list
   *  of Locales is ordered, starting with the preferred locale. If the client 
   *  request doesn't provide an Accept-Language header, this method returns 
   *  an Enumeration containing one Locale, the default locale for the server.
   */
    public java.util.Enumeration<Locale> getLocales(){
        if (locales!=null) return locales;
        
        java.util.HashMap<Locale, Double> locales = new java.util.HashMap<Locale, Double>();
        java.util.Enumeration<String> headers = getHeaders("Accept-Language"); 
        while (headers.hasMoreElements()){
            for (String str : headers.nextElement().split(",")){
                String[] arr = str.trim().replace("-", "_").split(";");
                
              //Find the locale
                Locale locale = null;
                String[] l = arr[0].split("_");
                switch(l.length){
                    case 2: locale = new Locale(l[0], l[1]); break;
                    case 3: locale = new Locale(l[0], l[1], l[2]); break;
                    default: locale = new Locale(l[0]); break;
                }
                
              //Find the q-value
                Double q = 1.0D;
                for (String s : arr){
                    s = s.trim();
                    if (s.startsWith("q=")){
                        q = Double.parseDouble(s.substring(2).trim());
                        break;
                    }
                }

              //Update the list of locales
                if (locales.containsKey(locale)){
                    Double currVal = locales.get(locale);
                    if (q>currVal) locales.put(locale, q);
                }
                else{
                    locales.put(locale, q);
                }
            }
        }
        
        if (locales.isEmpty()) locales.put(Locale.getDefault(), 0D);
        
      //Sort the locales
        java.util.ArrayList<Locale> sortedLocales = new java.util.ArrayList<Locale>(locales.keySet());
        java.util.Collections.sort(sortedLocales, new LocaleComparer(locales));

        this.locales = java.util.Collections.enumeration(sortedLocales);
        return this.locales;
    }

    
    private static class LocaleComparer implements java.util.Comparator<Locale> {
        
        private java.util.HashMap<Locale, Double> locales;
        public LocaleComparer(java.util.HashMap<Locale, Double> locales){
            this.locales = locales;
        }
        
        public int compare(Locale a, Locale b) {
            double x = locales.get(a);
            double y = locales.get(b);
            if (x>y) return -1;
            else if (x<y) return 1;
            else{
                int i = a.toString().split("_").length;
                int j = b.toString().split("_").length;                
                if (i<j) return 1;
                else if (i>j) return -1;
                else return 0;
            }
        }
    }


  //**************************************************************************
  //** getPath
  //**************************************************************************
  /** Returns the requested path and querystring. This usually corresponds to
   *  the first line of the request header. Example:
   *  <pre>GET /index.html?abc=123 HTTP/1.1</pre>
   *  If the server is acting as a proxy, the first line may include a full
   *  url. In this case, use the getURL() method to retrieve the original path.
   */
    public String getPath(){
        return url.getPath() + (url.getQuery()==null ? "" : "?"+url.getQuery());
    }


  //**************************************************************************
  //** getMethod
  //**************************************************************************
  /** Returns the method specified in the first line of the request (e.g. GET,
   *  POST, PUT, HEAD, etc). Note that the method is always returned in
   *  uppercase.
   */
    public String getMethod(){
        return method;
    }


  //**************************************************************************
  //** getHost
  //**************************************************************************
    public String getHost(){
        return url.getHost();
    }


  //**************************************************************************
  //** getPort
  //**************************************************************************
    public int getPort(){
        int port = url.getPort();
        if (port < 0 || port > 65535) port = 80;
        return port;
    }


  //**************************************************************************
  //** getServerName
  //**************************************************************************
  /** Returns the host name of the server to which the request was sent.
   *  It is the value of the part before ":" in the "Host" header, 
   *  header value, if any, or the resolved server name, or the server IP 
   *  address.
   */
    public String getServerName(){
        return getHost();
    }


  //**************************************************************************
  //** getServerPort
  //**************************************************************************
  /** Returns the port number to which the request was sent. It is the value
   *  of the part after ":" in the Host header value, if any, or the server 
   *  port where the client connection was accepted on. 
   */
    public int getServerPort(){
        return getPort();
    }
    
    
  /** Returns the host name of the Internet Protocol (IP) interface on which
   *  the request was received.
   */
    public String getLocalName(){
        return connection.getLocalHost();
    }

  /** Returns the Internet Protocol (IP) address of the interface on which the
   *  request was received.
   */
    public String getLocalAddr(){
        return connection.getLocalAddress();
    }


  /** Returns the Internet Protocol (IP) port number of the interface on which 
   *  the request was received.
   */
    public int getLocalPort(){
        return connection.getLocalPort();
    }
    
    
  //**************************************************************************
  //** setHost
  //**************************************************************************
  /** Used to update the Host attribute defined in the header and in the url.
   */
    public void setHost(String host, int port) {
        try{
            url = new java.net.URL(updateURL(host,port,url));
            host = getHost();
            port = getPort();
            setHeader("Host", (port==80 ? host : host+":"+port));
        }
        catch(Exception e){}
    }


  //**************************************************************************
  //** setRefererHost
  //**************************************************************************
  /** Used to set/update the RefererHost attribute defined in the header. */

    public void setRefererHost(String host, int port) {

        String referer = getHeader("Referer");
        if (referer!=null)
        try{
            java.net.URL url = new java.net.URL(referer);
            setHeader("Referer", updateURL(host, port, url));
        }
        catch(Exception e){
        }
    }


  //**************************************************************************
  //** updateURL
  //**************************************************************************
  /**  Used to update the host and port in a URL.
   */
    private String updateURL(String host, Integer port, java.net.URL url) {

        port = ((port==null || port < 0 || port > 65535) ? 80 : port);
        host = (port==80 ? host : host+":"+port);
        String str = url.toString();
        String protocol = str.substring(0, str.indexOf(url.getHost()));
        String path = (url.getPath()==null ? "" : url.getPath());
        if (path.length()>0){
            str = str.substring(protocol.length());
            path = str.substring(str.indexOf(url.getPath()));
        }
        else{
            if (url.getQuery()!=null)
                path = str.substring(str.indexOf("?"+url.getQuery()));
        }
        return protocol + host + path;
    }


  //**************************************************************************
  //** isKeepAlive
  //**************************************************************************
  /** Used to determine whether the Connection attribute is set to Keep-Alive.
   */
    public boolean isKeepAlive(){
        if (isKeepAlive==null){
            String connType = getHeader("Connection");
            isKeepAlive = (connType==null ? false : connType.toUpperCase().contains("KEEP-ALIVE"));
        }
        return isKeepAlive;
    }

    
  //**************************************************************************
  //** isWebSocket
  //**************************************************************************
  /** Used to determine whether the client is requesting a WebSocket 
   *  connection. Returns true if the Upgrade header contains a "websocket"
   *  keyword and if the Connection header contains a "upgrade" keyword.
   */
    public boolean isWebSocket(){
        if (isWebSocket==null) isWebSocket = isUpgradeRequest();
        return isWebSocket;
    }
    
    private boolean isUpgradeRequest(){
        //Check for "Upgrade: websocket" header present.
        String upgrade = getHeader("Upgrade");
        if (upgrade == null) return false;
        if (!upgrade.equalsIgnoreCase("websocket")) return false;
        

        
        //Check if the "Connection: upgrade" header present.
        String connection = getHeader("Connection");
        if (connection == null) return false;
        boolean foundUpgradeToken = false;
        for (String str : connection.split(",")){
            if (str.trim().equalsIgnoreCase("upgrade")){
                foundUpgradeToken = true;
                break;
            }
        }
        if (!foundUpgradeToken) return false;
        
        
        //Only GET request are supported
        if (!getMethod().equals("GET")) return false;
        
        
        //Only HTTP/1.1 requests are supported
        if (!getProtocol().equals("HTTP/1.1")) return false;
        
        
        return true;
    }

    
  //**************************************************************************
  //** isEncrypted
  //**************************************************************************
  /** Used to determine whether the Connection is encrypted (e.g. SSL/TLS)
   */
    public boolean isEncrypted(){
        return sslEngine!=null;
    }


  //**************************************************************************
  //** isSecure
  //**************************************************************************    
  /** Returns a boolean indicating whether this request was made using a
   *  secure channel, such as HTTPS.
   */
    public boolean isSecure(){
        return isEncrypted();
    }


  //**************************************************************************
  //** getProtocol
  //**************************************************************************
  /** Returns the name and version of the protocol the request uses in the 
   *  form <i>protocol/majorVersion.minorVersion</i> (e.g. "HTTP/1.1").
   */
    public String getProtocol(){
        return "HTTP/" + version;
    }


  //**************************************************************************
  //** getScheme
  //**************************************************************************
  /** Returns the name of the scheme used to make this request (e.g. "http").
   */
    public String getScheme(){
        String protocol = getURL().getProtocol().toLowerCase();
        if (isWebSocket()) protocol = protocol.equals("https") ? "wss" : "ws";
        return protocol;
    }


  //**************************************************************************
  //** getURL
  //**************************************************************************
  /** Used to retrieve the requested url defined in the header. */

    public java.net.URL getURL(){
        return url;
    }


  //**************************************************************************
  //** getRequestURI 
  //**************************************************************************
  /** Returns the part of this request's URL from the protocol name up to the
   *  query string in the first line of the HTTP request. The web container 
   *  does not decode this String
   *  For example:

     * <table summary="Examples of Returned Values">
     * <tr align=left><th>First line of HTTP request      </th>
     * <th>     Returned Value</th>
     * <tr><td>POST /some/path.html HTTP/1.1<td><td>/some/path.html
     * <tr><td>GET http://foo.bar/a.html HTTP/1.0
     * <td><td>/a.html
     * <tr><td>HEAD /xyz?a=b HTTP/1.1<td><td>/xyz
     * </table>
     *
     * <p>To reconstruct an URL with a scheme and host, use
     * {@link HttpUtils#getRequestURL}.
     *
     * @return		a <code>String</code> containing
     *			the part of the URL from the
     *			protocol name up to the query string
     */
    public String getRequestURI(){
        String path = url.getPath();
        if (!path.startsWith("/")) path = "/" + path;
        return path;
    }


  //**************************************************************************
  //** getRequestURL
  //**************************************************************************
  /** Reconstructs the URL the client used to make the request. The returned
   *  URL contains a protocol, server name, port number, and server path, but 
   *  it does not include query string parameters. 
   */
    public StringBuffer getRequestURL(){
        String url = this.getURL().toString();
        if (this.getQueryString()!=null) url = url.substring(0, url.indexOf("?"));
        StringBuffer str = new StringBuffer();
        str.append(url);
        return str;
    }


  //**************************************************************************
  //** getURL
  //**************************************************************************
  /** Returns the url query string. Returns null if one does not exist. */
    public String getQueryString(){
        return url.getQuery();
    }


  //**************************************************************************
  //** getParameter
  //**************************************************************************
  /** Used to retrieve the value of a specific variable supplied in the query
   *  string. Does NOT retrieve or parse posted data from form data. Use the
   *  getForm() method instead.
   *
   *  @param key Parameter name. Performs a case insensitive search for the
   *  keyword.
   *
   *  @return Returns a comma delimited list of values associated with the
   *  given key or a null value if the key is not found or if the value is
   *  an empty string.
   */
    public String getParameter(String key){
        StringBuffer str = new StringBuffer();
        List<String> values = parameters.get(key.toLowerCase());
        if (values!=null){
            for (int i=0; i<values.size(); i++){
                str.append(values.get(i));
                if (i<values.size()-1) str.append(",");
            }
            return str.toString();
        }
        else{
            return null;
        }
    }


  //**************************************************************************
  //** getParameterNames
  //**************************************************************************    
  /** Returns an Enumeration of String objects containing the names of the 
   *  parameters contained in the query string. If the request has no 
   *  parameters, the method returns an empty Enumeration. <p/>
   *  Note that this method does NOT retrieve or parse posted data from form 
   *  data. Use the getForm() method instead.
   */
    public java.util.Enumeration<String> getParameterNames(){
        return java.util.Collections.enumeration(parameters.keySet());
    }


  //**************************************************************************
  //** getParameterValues
  //**************************************************************************
  /** Returns an array containing all of the values for a given query string 
   *  parameter or null if the parameter does not exist.<p/>
   *  Note that this method does NOT retrieve or parse posted data from form 
   *  data. Use the getForm() method instead.
   */
    public String[] getParameterValues(String name){     
        List<String> values = parameters.get(name.toLowerCase());
        if (values!=null){
            return values.toArray(new String[values.size()]);
        }
        return null;
    }


  //**************************************************************************
  //** getParameterMap
  //**************************************************************************
  /** Returns an immutable java.util.Map containing parameters found in the 
   *  query string. The keys in the parameter map are of type String. The 
   *  values in the parameter map are of type String array.<p/>
   *  Note that this method does NOT retrieve or parse posted data from form 
   *  data. Use the getForm() method instead.
   */
    public java.util.Map<String, String[]> getParameterMap(){
        HashMap<String, String[]> map = new HashMap<String, String[]>();
        java.util.Iterator<String> it = parameters.keySet().iterator();
        while (it.hasNext()){
            String key = it.next();
            map.put(key, getParameterValues(key));
        }
        return map;
    }


  //**************************************************************************
  //** parseQueryString
  //**************************************************************************
  /** Used to parse a url query string and create a list of name/value pairs.
   *  This method is called in the constructor to parse the querystring found
   *  in the request URL.
   */
    private HashMap<String, List<String>> parseQueryString(String query){

        
      //IMPLEMENTATION NOTE:
      //Code copied from the javaxt.utils.URL class. Only one change made to 
      //the original source. If the query contains a "&amp;", simply update
      //the query string. Please make sure to synchronize any other changes.


      //Create an empty hashmap
        HashMap<String, List<String>> parameters = new HashMap<String, List<String>>();
        if (query==null) return parameters;


        try{
          //Decode the querystring. Note that the URLDecoder doesn't decode
          //everything (e.g. "&amp;").
            query = java.net.URLDecoder.decode(query, "UTF-8");
        }
        catch(Exception e){
          //Decode the string manually. This should never happen!
            String find[] = new String[]{"%2C","%2F","%3A"};
            String replace[] = new String[]{",","/",":"};
            for (int i=0; i<find.length; i++){
                 query = query.replace(find[i],replace[i]);
            }
        }

        if (query.contains("&amp;"))query = query.replace("&amp;", "&");
        

      //Parse the querystring, one character at a time. Note that the tokenizer
      //implemented here is very inefficient. Need something better/faster.
        if (query.startsWith("&")) query = query.substring(1);
        query += "&";


        StringBuffer word = new StringBuffer();
        String c = "";

        for (int i=0; i<query.length(); i++){

             c = query.substring(i,i+1);

             if (!c.equals("&")){
                 word.append(c); //word = word + c;
             }
             else{
                 int x = word.indexOf("=");
                 if (x>=0){
                     String key = word.substring(0,x).toLowerCase();
                     String value = word.substring(x+1);

                     List<String> values = parameters.get(key);
                     if (values==null) values = new java.util.LinkedList<String>();
                     values.add(value);
                     parameters.put(key, values);
                 }
                 else{
                     parameters.put(word.toString(), null);
                 }

                 word = new StringBuffer(); 
             }
        }
        word = null;
        return parameters;
    }


  //**************************************************************************
  //** getRequestURL
  //**************************************************************************
  /** Returns a StringBuffer similar to one returned by an implementation of a
   *  HttpServletRequest class. The returned URL contains a protocol, server
   *  name, port number, and server path, but it does not include query string
   *  parameters.
   *
    public StringBuffer getRequestURL(){
        String url = this.url.toString();
        if (url.contains("?")) url = url.substring(0, url.indexOf("?"));
        return new StringBuffer().append(url);
    }
    */


  //**************************************************************************
  //** getContentLength
  //**************************************************************************
  /** Returns the "Content-Length" specified in the http request header.
   */
    public int getContentLength(){
        if (contentLength==null){
            try{
                contentLength = Integer.parseInt(getHeader("Content-Length"));
            }
            catch(Exception e){
                contentLength = -1;
            }
        }
        return contentLength;
    }


  //**************************************************************************
  //** getBody
  //**************************************************************************
  /** Returns the body of the http request as a byte array. Reads all remaining
   *  bytes from the socket. Therefore, you should only call this method once.
   *  Subsequent calls will return an empty array.
   */
    public byte[] getBody() throws IOException {

      //Only POST should have a body. Otherwise, return an empty array.
        if (!this.getMethod().equals("POST")) return new byte[0];


      //If the client specified a Content-Length of 0, simply return an empty
      //array. Otherwise, the server will wait up to 2 minutes trying to read
      //data from the socket.
        int contentLength = getContentLength();
        if (contentLength<1) return new byte[0];
        

      //Set initial buffer size and buffer
        int bufferSize = 24576; //24kb
        if (contentLength>0 && contentLength<bufferSize) bufferSize = contentLength;
        ByteBuffer buf = ByteBuffer.allocateDirect(bufferSize);
        java.io.ByteArrayOutputStream bas = new java.io.ByteArrayOutputStream();

        

        boolean decrypt = isEncrypted();
        while (true) {

          //Update the buffer size as needed. This will help avoid a 15 second
          //wait period when reading bytes from the socket.
            if (contentLength>0){
                int remaining = contentLength-bas.size();
                if (remaining<bufferSize) buf = ByteBuffer.allocateDirect(remaining);
            }

          //Try to fill the buffer with bytes from the socket
            int numBytesRead = read(buf, decrypt);
            if (numBytesRead>0){
                byte[] b = new byte[numBytesRead];
                buf.get(b, 0, numBytesRead);
                bas.write(b);
                if (contentLength<0 && numBytesRead!=bufferSize) break; //<-- Is this a valid use case? Can Content-Length be undefined?
                if (bas.size()==contentLength) break;
            }
            else{
                break;
            }
        }

        buf.clear();
        buf = null;

        return bas.toByteArray();
    }


  //**************************************************************************
  //** getInputStream
  //**************************************************************************
  /** Returns the body of the http request as an input stream. Automatically 
   *  decrypts the body if the data is SSL/TLS encrypted. Example:
   <pre>
        java.io.InputStream inputStream = request.getInputStream();
        byte[] b = new byte[1024];
        int x=0;
        while ( (x = inputStream.read(b)) != -1) {
            //Do something! Example: outputStream.write(b,0,x);
        }
        inputStream.close();
   </pre>
   */
    public ServletInputStream getInputStream() {
        if (inputStream==null) inputStream = new ServletInputStream(this);
        return inputStream;
    }


  //**************************************************************************
  //** getReader
  //**************************************************************************
  /** Returns a BufferedReader used to process the body of the http request. 
   *  Automatically decrypts the body if the data is SSL/TLS encrypted.
   *  Either this method or getInputStream() may be called to read the body,
   *  but not both.
   */
    public java.io.BufferedReader getReader() throws IOException{
        String charset = getCharacterEncoding();
        if (charset==null) charset = "UTF-8"; //vs "ISO-8859-1"
        return new java.io.BufferedReader(
            new java.io.InputStreamReader(getInputStream(), charset));
    }


  //**************************************************************************
  //** getFormInputs
  //**************************************************************************
  /** Returns form elements in the body of the http request as an iterator.
   *  Reads data from the client on-demand, meaning form data will only be
   *  retrieved from the client when calling Iterator.next(). This is
   *  potentially more memory efficient than calling getBody() and parsing
   *  the entire byte array. This is especially true when processing
   *  "multipart/form-data" with large amounts of binary data (e.g. uploaded
   *  files). Please see the FormInput.getInputStream() or FormInput.toFile()
   *  methods for more information on handling large binary streams.
   *  <p/>
   *
   *  Here's a simple example of how to iterate through form data using the
   *  getFormInputs() method. Note how easy it is to identify an uploaded 
   *  file and save it to disk.
   <pre>
        java.util.Iterator&lt;FormInput&gt; it = request.getFormInputs();
        while (it.hasNext()){
            FormInput input = it.next();
            String name = input.getName();
            FormValue value = input.getValue();

            if (input.isFile()){
                value.toFile(new java.io.File("/temp/" + input.getFileName()));
                System.out.println(name + ": &lt;FILE&gt;");
            }
            else{
                System.out.println(name + ": " + value);
            }
        }
   </pre>
   *  Note that the form iterator reads data directly from the socket 
   *  connection. Therefore, you should only call this method once.
   *  <p/>
   *
   *  More information on HTML form data can be found here:<br/>
   *  http://www.w3.org/TR/html401/interact/forms.html#h-17.13.4
   */
    public FormIterator getFormInputs() throws IOException {

        if (!this.getMethod().equals("POST"))
            throw new IOException("Unsupported method: " + this.getMethod());

        String contentType = getHeader("Content-Type");
        if (contentType==null) throw new IOException("Content-Type is undefined.");


        String boundary = null;
        if (contentType.contains("application/x-www-form-urlencoded")){
            boundary = "&";
        }
        else if (contentType.contains("multipart/form-data")){
            for (String s : contentType.split(";")){
                s = s.toLowerCase().trim();
                if (s.toLowerCase().trim().startsWith("boundary=")){
                    boundary = s.trim().substring("boundary=".length());
                    break;
                }
            }
        }
        else{
            throw new IOException("Unsupported Content-Type: " + contentType);
        }

        return new FormIterator(getInputStream(), boundary);
    }


  //**************************************************************************
  //** FormIterator Class
  //**************************************************************************
  /** Simple implementation of a java.util.Iterator used to parse form data.
   */
    private class FormIterator implements java.util.Iterator {

        private FormInput currInput = null;
        private FormInput prevInput = null;
        private ServletInputStream is;
        private String boundary;


        private FormIterator(ServletInputStream is, String boundary){
            this.is = is;
            this.boundary = boundary;
        }

        public boolean hasNext(){
            if (currInput==null) getNextInput();
            return currInput!=null;
        }

        public FormInput next(){
            if (currInput==null) getNextInput();
            FormInput input = currInput;
            prevInput = currInput;
            currInput = null;
            return input;

        }

        private void getNextInput(){
            try{
                FormInput input = new FormInput(is, prevInput, boundary);
                if (currInput!=null) prevInput = currInput;
                currInput = input;
            }
            catch(Exception e){
                currInput = null;
            }
        }

        public void remove(){
        }
    }


  //**************************************************************************
  //** getSession
  //**************************************************************************
  /** Returns the current session associated with this request, or if the
   *  request does not have a session, creates one.
   */
    public HttpSession getSession(){
        return getSession(true);
    }


  //**************************************************************************
  //** getSession
  //**************************************************************************
  /** Returns the current HttpSession associated with this request or, if
   *  there is no current session and create is true, returns a new session.
   */
    public HttpSession getSession(boolean create){
        if (session!=null) return session;

        String sessionID = getRequestedSessionId();
        if (sessionID!=null) session = HttpSession.get(sessionID);
        if (session!=null) return session;

      //If we're still here, create a new session as requested
        if (create) session = new HttpSession();

        return session;
    }


  //**************************************************************************
  //** getRequestedSessionId
  //**************************************************************************
  /** Returns the session ID specified by the client ("JSESSIONID" cookie).
   *  If the client did not specify a session ID, this method returns null.
   *  Use the isRequestedSessionIdValid() method to verify whether the 
   *  session ID is valid.
   */
    public String getRequestedSessionId(){
        Cookie[] cookies = getCookies();
        if (cookies!=null){
            for (Cookie cookie : cookies){
                if (cookie.getName().equalsIgnoreCase("JSESSIONID")){
                    return cookie.getValue();
                }
            }
        }
        return null;
    }


  //**************************************************************************
  //** isRequestedSessionIdValid
  //**************************************************************************
  /** Checks whether the requested session ID is still valid. Returns true if 
   *  this request has an id for a valid session in the current session 
   *  context. 
   */
    public boolean isRequestedSessionIdValid(){
        HttpSession session = getSession(false);
        return session!=null;    
    }


  //**************************************************************************
  //** isRequestedSessionIdFromCookie
  //**************************************************************************
  /** Checks whether the requested session ID came in as a cookie. */

    public boolean isRequestedSessionIdFromCookie(){
        return true; //This server manages sessions via cookies...
    }


  //**************************************************************************
  //** isRequestedSessionIdFromURL
  //**************************************************************************
  /** Checks whether the requested session ID came in as part of the request 
   *  URL.
   */
    public boolean isRequestedSessionIdFromURL(){
        return false; //This server manages sessions via cookies...
    }


  //**************************************************************************
  //** isRequestedSessionIdFromUrl
  //**************************************************************************
  /** @deprecated As of Version 2.1 of the Java Servlet API. 
   *  Use isRequestedSessionIdFromURL() instead.
   */
    public boolean isRequestedSessionIdFromUrl(){
        return isRequestedSessionIdFromURL();
    }


  //**************************************************************************
  //** getCookies
  //**************************************************************************
  /** Returns an array containing all of the Cookie objects the client sent 
   *  with this request. This method returns null if no cookies were sent.
   */
    public Cookie[] getCookies(){

      //Parse only once and only when needed
        if (cookies==null){
            cookies = new java.util.ArrayList<Cookie>();

            String cookies = getHeader("Cookie");
            if (cookies!=null){
                for (String cookie : cookies.split(";")){
                    cookie = cookie.trim();
                    int x = cookie.indexOf("=");
                    if (x>0){
                        String name = cookie.substring(0, x).trim();
                        String value = cookie.substring(x+1).trim();
                        this.cookies.add(new Cookie(name, value));
                    }
                }
            }
        }

        if (cookies.size()==0) return null;
        else return cookies.toArray(new Cookie[cookies.size()]);
    }


  //**************************************************************************
  //** setAttribute
  //**************************************************************************
  /** Returns the value of a given attribute. Returns null if no attribute of 
   *  the given name exists. <p/>
   *  Attributes contain custom information about a request. Attributes are 
   *  set programatically using the setAttribute() method and are typically 
   *  used in conjunction with a RequestDispatcher. Attribute names should 
   *  follow the same conventions as package names. The servlet specification 
   *  reserves names matching "java.*", "javax.*", and "sun.*".
   */
    public Object getAttribute(String name){
        return attributes.get(name);
    }


  //**************************************************************************
  //** setAttribute
  //**************************************************************************
  /** Used to add, update, or delete an attribute associated with this request. 
   *  Attributes contain custom information about a request and are typically 
   *  used in conjunction with a RequestDispatcher. If the object passed in is 
   *  null, the effect is the same as calling removeAttribute().
   */
    public void setAttribute(String name, Object o){
        if (o==null) removeAttribute(name);
        else attributes.put(name, o);
    }


  //**************************************************************************
  //** removeAttribute
  //**************************************************************************
  /** Removes an attribute associated with this request. See getAttribute()   
   *  and setAttribute() for more information.
   */
    public void removeAttribute(String name){
        attributes.remove(name);
    }


  //**************************************************************************
  //** getAttributeNames
  //**************************************************************************
  /** Returns an Enumeration containing the names of the attributes associated 
   *  with this request. Returns an empty Enumeration if the request has no 
   *  attributes associated with it. See getAttribute() and setAttribute() for  
   *  more information.
   */
    public java.util.Enumeration<String> getAttributeNames(){
        return java.util.Collections.enumeration(attributes.keySet());
    }


  //**************************************************************************
  //** getRequestDispatcher
  //**************************************************************************
  /** This method is supposed to return a RequestDispatcher object that can be 
   *  used to forward a request to the resource or to include the resource in 
   *  a response. This server does not currently support RequestDispatcher so
   *  this method returns a null. 
   */
    @Override
	public RequestDispatcher getRequestDispatcher(String path){
        return null; //RequestDispatcher
    }


  //**************************************************************************
  //** getRequestDispatcher
  //**************************************************************************
  /** @deprecated As of Version 2.1 of the Java Servlet API. Use 
   *  ServletContext.getRealPath() instead.
   */
    public String getRealPath(String path){
        return servletContext.getRealPath(path);
    }


  //**************************************************************************
  //** getPathInfo
  //**************************************************************************
  /** Returns any extra path information associated with the URL the client 
   *  sent when it made this request. The extra path information follows the 
   *  servlet path but precedes the query string and will start with a "/"
   *  character. Consider this example:
   *  <pre>http://localhost:8080/MyServlet/Extra/Path/?abc=123</pre>
   *  In this example, "/MyServlet" is the servlet path and this method will
   *  return "/Extra/Path/" as the extra path. If no extra path is found, this
   *  method will return a null. 
   */
    public String getPathInfo(){
        String path = this.getURL().getPath();
        String servletPath = getServletPath();
        if (path.contains(servletPath)){
            path = path.substring(path.indexOf(servletPath)+servletPath.length());
        }
        if (path.length()==0) path = null;
        return path;
    }


  //**************************************************************************
  //** getPathTranslated
  //**************************************************************************
  /** Returns any extra path information after the servlet name but before the
   *  query string, and translates it to a real path. If the URL does not have 
   *  any extra path information, or if  the servlet container cannot  
   *  translate the virtual path to a real path for any reason, this method 
   *  returns a null.
   */
    public String getPathTranslated(){
      //TODO: Verify getPathTranslated implementation 
        String path = getPathInfo();
        if (path!=null) return getRealPath(path);
        return null;
    }


  //**************************************************************************
  //** getContextPath
  //**************************************************************************
  /** Returns a string in the requested URL that represents the servlet 
   *  context. This is typically defined in the META-INF/context.xml file in 
   *  Java EE web applications. For example, if a web application is called 
   *  "WebApplication", the context path might be "/WebApplication". In this
   *  case, a requested URL will include the context path like this:
   *  <pre>http://localhost:8080/WebApplication/MyServlet/?abc=123</pre><p/>
   *  
   *  The context path always comes first in a request URL. The path starts 
   *  with a "/" character but does not end with a "/" character. For servlets 
   *  in the default (root) context, this method returns "". <p/>
   *  
   *  Note that this server does not currently support the container concept
   *  where multiple servlets are managed by a servlet container. Instead, we
   *  have a single servlet that processes all web requests and can dispatch
   *  the requests to other servlets. Therefore, to retrieve a "context path"
   *  developers must explicitely set the "context path" in the servlet and 
   *  implement logic to generate/process the URLs accordingly.
   */
    public String getContextPath(){
        return servletContext.getContextPath();
    }


  //**************************************************************************
  //** getServletPath
  //**************************************************************************
  /** Returns a string in the requested URL that represents the servlet path.
   *  This path starts with a "/" character and includes either the servlet 
   *  name or a path to the servlet, but does not include any extra path
   *  information or a query string. For example, consider the following URL:
   *  <pre>http://localhost:8080/WebApplication/MyServlet/?abc=123</pre><p/>
   *  In this example, the context path is "/WebApplication" and "/MyServlet"
   *  is the servlet path. <p/>
   * 
   *  Note that this server does not require a URL "Pattern" to be defined for
   *  for individual servlets. Instead, we have a single servlet that processes 
   *  all web requests and can dispatch the requests to other servlets. 
   *  Therefore, to retrieve a "servlet path" developers must explicitely set 
   *  the servlet path in the servlet and implement logic to process the  
   *  URLs accordingly.
   */
    @Override
	public String getServletPath(){
        return servletPath;
    }


  //**************************************************************************
  //** getServletContext
  //**************************************************************************
    @Override
	public ServletContext getServletContext(){
        return servletContext;
    }


  //**************************************************************************
  //** getAuthType
  //**************************************************************************    
  /** Returns the authentication scheme used to authenticate clients (e.g. 
   *  "BASIC", "DIGEST", "CLIENT_CERT", etc). This value is retrieved from an
   *  Authenticator and does not necessarily correspond to the "Authorization"
   *  request header. If an Authenticator is not used to secure the servlet,
   *  a null is returned.
   */
    public String getAuthType(){
        if (authenticator!=null) return authenticator.getAuthType();
        else return null;
    }

    
  //**************************************************************************
  //** getCredentials
  //**************************************************************************
  /** Returns an array representing the client credentials associated with
   *  this request. The first element in the array represents the username and
   *  the second element represents the password. <p/>
   *  Credentials are retrieved from an Authenticator. If no Authenticator is
   *  defined or if the Authenticator fails to parse the credentials, this 
   *  method returns a null.
   */    
    public String[] getCredentials(){
        if (getCredentials){
            try {
                credentials = authenticator.getCredentials();
            }
            catch(Exception e){
            }
            getCredentials = false;
        }
        return credentials;
    }


  //**************************************************************************
  //** authenticate
  //**************************************************************************
  /** Used to authenticate a client request. Authentication is performed by an
   *  Authenticator. If no Authenticator is defined or if the Authenticator 
   *  fails to authenticate the client, this method throws a ServletException.
   */
    public void authenticate() throws ServletException {
        if (authenticate==true){
            try{            
                authenticator.authenticate();
            }
            catch(ServletException e){
                authenticationException = e;
            }
            catch(Exception e){
                authenticationException = new ServletException(e.getLocalizedMessage());
                authenticationException.setStackTrace(e.getStackTrace());
            }
            authenticate = false;
        }
        
        if (authenticationException!=null) throw authenticationException;
    }
    

  //**************************************************************************
  //** getRemoteUser
  //**************************************************************************
  /** Returns the login of the user making this request, if the user has been
   *  authenticated, or null if the user has not been authenticated.
   */
    public String getRemoteUser(){
        try{
            String[] credentials = getCredentials();
            authenticate();
            return credentials[0];
        }
        catch(Exception e){
        }
        return null;
    }


  //**************************************************************************
  //** isUserInRole
  //**************************************************************************
  /** Returns a boolean indicating whether the authenticated user is included
   *  in the specified "role". Roles and role membership are often managed by 
   *  an Authenticator. If no Authenticator is defined, or if the user is not
   *  authenticated, or if no role is defined for the user, the method returns
   *  false.
   */
    public boolean isUserInRole(String role){
        try{
            return authenticator.isUserInRole(role);
        }
        catch(Exception e){
        }
        return false;
    }


  //**************************************************************************
  //** getUserPrincipal
  //**************************************************************************
  /** Returns a java.security.Principal object containing the name of the 
   *  current authenticated user. User Principals are resolved by an 
   *  Authenticator. If no Authenticator is defined, or if the user has not
   *  been authenticated, the method returns a null.
   */
    public java.security.Principal getUserPrincipal(){
        if (getUserPrincipal){
            try{
                principal = authenticator.getPrinciple();
            }
            catch(Exception e){
            }
            getUserPrincipal = false;
        }
        return principal;
    }


  //**************************************************************************
  //** toString
  //**************************************************************************
  /** Returns the full HTTP Request Header.
   */
    public String toString(){
        StringBuffer out = new StringBuffer();
        for (String entry : header){
            if (entry!=null){
                entry = entry.trim();
                if (entry.length()>0){
                    out.append(entry);
                    out.append("\r\n");
                }
            }
        }
        out.append("\r\n");
        return out.toString();
    }


  //**************************************************************************
  //** clear
  //**************************************************************************
  /** Clears private variables and sets them to null. This method is called
   *  automatically after each http request to free up server resources. Do
   *  not call this method from your application.
   */
    public void clear(){

        if (parameters!=null){
            parameters.clear();
            parameters = null;
        }

        header = null;
        url = null;
        version = null;
        method = null;
        remoteSocketAddress = null;
    }

    
    protected SocketConnection getConnection(){
        return connection;
    }
    

  //**************************************************************************
  //** getApplicationData
  //**************************************************************************
  /** Returns the next available TLS application data record as a byte array.
   *  Note that the bytes are automatically decrypted (unwrapped).
   */
    private byte[] getApplicationData() throws IOException {

      //Read the next 5 bytes from the socket channel. This should contain TLS
      //record information.
        if (recordHeader==null){
            recordHeader = ByteBuffer.allocateDirect(5);
            read(recordHeader, false);
        }

      //Verify that the message contains application data
        recordHeader.rewind();
        byte firstByte = recordHeader.get();
        if (firstByte!=23) throw new IOException("Invalid record found when processing TLS application data. First byte is: " + firstByte);

      //Get the length of the TLS record
        recordHeader.position(3);
        int recordLength = Integer.parseInt(getHex(recordHeader) + getHex(recordHeader), 16);
        recordHeader.rewind();

      //Read the TLS record
        ByteBuffer recordData = ByteBuffer.allocateDirect(recordLength);
        int ttl = read(recordData, false);
        if (ttl<recordLength){
            while (ttl<recordLength){
                recordData.position(ttl);
                ByteBuffer tmp = ByteBuffer.allocateDirect(recordLength-ttl);
                ttl += read(tmp, false);
                recordData.put(tmp);
            }
            recordData.rewind();
        }
        

      //Merge the TLS header and record data into a single buffer
        ByteBuffer tlsRecord = ByteBuffer.allocateDirect(recordLength+recordHeader.capacity());
        tlsRecord.put(recordHeader);
        tlsRecord.put(recordData);
        tlsRecord.rewind();

      //Decrypt the application data
        byte[] arr = decrypt(tlsRecord);


      //Clean up
        recordHeader.clear();
        recordData.clear();
        tlsRecord.clear();
        
        recordHeader = recordData = tlsRecord = null;


      //Return array
        return arr;
    }
    
    
  //**************************************************************************
  //** decrypt
  //**************************************************************************
  /** Used to decrypt a TLS record. 
   */
    public byte[] decrypt(ByteBuffer tlsRecord) throws IOException {
        ByteBuffer output = ByteBuffer.allocateDirect(tlsRecord.capacity());
        SSLEngineResult serverResult = sslEngine.unwrap(tlsRecord, output);
        runDelegatedTasks(serverResult, sslEngine);


        byte[] arr = new byte[output.position()];
        output.rewind();
        output.get(arr);
        
        output.clear();
        output = null;
        
        return arr;
    }


  //**************************************************************************
  //** printTLS
  //**************************************************************************
  /** Used to parse the first couple bytes of a SSL/TLS message.  
   *  http://www.iana.org/assignments/tls-parameters/tls-parameters.xml
   */
    private void printTLS(ByteBuffer buf) throws IOException {

        if (1>0) return;

        int orgPos = buf.position();
        buf.rewind();

        boolean startEncryption = false;
        try{
        while (true){
            int ct = buf.get();
            String contentType = "";
            switch(ct){
                case 20: contentType = "change_cipher_spec"; break;
                case 21: contentType = "alert"; break;
                case 22: contentType = "handshake"; break;
                case 23: contentType = "application_data"; break;
                case 24: contentType = "heartbeat"; break;
                default: contentType = "unknown"; break;
            }

            if (contentType.equals("unknown")) break;



            String sslVersion = buf.get() + "." + buf.get(); //0x0301 => 3.1 - SSLv3/TLSv1
            log().t("sslVersion", sslVersion);


            int recordLength = Integer.parseInt(getHex(buf) + getHex(buf), 16);
            log().t("recordLength", recordLength);

            log().t(contentType, ct);


            if (startEncryption || contentType.equals("application_data")){
            	log().t(" *** Encrypted Message **** ");
                for (int i=0; i<recordLength; i++){
                    buf.get();
                }
            }
            else{

                int ttl = 0;
                while (ttl<recordLength){

                    int handshake = buf.get();
                    String handshakeType = "";
                    switch(handshake){
                        case 0: handshakeType = "hello_request"; break;
                        case 1: handshakeType = "client_hello"; break;
                        case 2: handshakeType = "server_hello"; break;
                        case 3: handshakeType = "hello_verify_request"; break;
                        case 4: handshakeType = "NewSessionTicket"; break;

                        case 11: handshakeType = "certificate"; break;
                        case 12: handshakeType = "server_key_exchange"; break;
                        case 13: handshakeType = "certificate_request"; break;
                        case 14: handshakeType = "server_hello_done"; break;
                        case 15: handshakeType = "certificate_verify"; break;
                        case 16: handshakeType = "client_key_exchange"; break;

                        case 20: handshakeType = "finished"; break;
                        case 21: handshakeType = "certificate_url"; break;
                        case 22: handshakeType = "certificate_status"; break;
                        case 23: handshakeType = "supplemental_data"; break;

                        default: handshakeType = "unknown"; break;
                    }                    
                    ttl++;

                    int length = 0;
                    if (recordLength>1){

                        length = Integer.parseInt( getHex(buf) + getHex(buf) + getHex(buf), 16);
                        ttl+=3;

                        for (int i=0; i<length; i++){
                            buf.get();
                            ttl++;
                        }

                    }

                    log().d(handshakeType, handshake, length);
                }

            }

            if (contentType.equals("change_cipher_spec")) startEncryption = true;
        }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        buf.position(orgPos);
    }
    

    private String getHex(java.nio.ByteBuffer buf){
        return getHex(buf.get());
    }

    private String getHex(byte[] bytes){
        StringBuffer str = new StringBuffer(bytes.length);
        for (byte b : bytes){
            str.append(String.format("%02X", b));
            str.append(" ");
        }
        return str.toString().trim();
    }

    private String getHex(byte b){
        return getHex(new byte[]{b});
    }


  //**************************************************************************
  //** read
  //**************************************************************************
  /** Used to fill a given buffer with bytes from the client. 
   *
   *  @param decrypt Flag used to indicate whether to decrypt the data read
   *  from the socket. This is required when processing SSL/TLS application
   *  data. If true, the method will fetch the next available TLS application
   *  record. Note that the TLS record will rarely match the requested buffer
   *  size/capacity. In this case, we will fetch n-number of TLS records from
   *  the client until we have filled the buffer.
   *
   *  @return The number of bytes in the buffer.
   */
    private int read(ByteBuffer buf, boolean decrypt) throws IOException {
        
        int numBytesRead;
        buf.rewind();
        
        if (decrypt){

          //Fill appData with the next TLS record
            if (appData==null || (appData!=null && (appData.position()==appData.capacity()))){
                byte[] arr = getApplicationData();
                appData = ByteBuffer.allocateDirect(arr.length);
                appData.put(arr);
                appData.rewind();
            }


            if (buf.capacity()<=appData.remaining()){
                byte[] arr = new byte[buf.capacity()];
                appData.get(arr);
                buf.put(arr);
                numBytesRead = arr.length;
                arr = null;
            }
            else{

                buf.put(appData);                
                while (buf.position()<buf.capacity()){

                    byte[] arr = getApplicationData();
                    appData = ByteBuffer.allocateDirect(arr.length);
                    appData.put(arr);
                    appData.rewind();

                    if (buf.remaining()<appData.remaining()){
                        arr = new byte[buf.remaining()];
                        appData.get(arr);
                        buf.put(arr);
                    }
                    else{
                        buf.put(appData);
                    }
                }

                numBytesRead = buf.position();
            }

        }
        else{ 
            numBytesRead = connection.read(buf);
        }

        buf.rewind();
        return numBytesRead;
    }


  //**************************************************************************
  //** nextByte
  //**************************************************************************
    protected Byte nextByte(boolean decrypt) throws IOException{
        int numBytesRead = read(oneByte, decrypt);
        if (numBytesRead==-1) return null;
        return oneByte.get(0);
    }


  //**************************************************************************
  //** initHandshake
  //**************************************************************************
  /** Used to perform an SSL/TLS handshake. The handshake consists of several
   *  message exchanges between the client and the server. The handshake is
   *  initiated with a client hello message which includes a list of supported
   *  ciphers. The server responds with a server hello response which includes
   *  a selected cipher and a certificate chain. If the certificate is accepted
   *  by the client and the selected cipher is valid, the client will send
   *  a reply that may include a certificate chain, a certificate verify
   *  message, change cipher spec request, and a client finished message. At
   *  this point, if everything goes well, the server will reply with a
   *  change cipher spec response and a server finished message. This will
   *  signal the end of a successful handshake.
   */
    private void initHandshake() throws ServletException, IOException {


      /** Stores data received directly from the network. This consists of
       *  encrypted data and handshake information. This buffer is filled
       *  with data read from the socket and emptied by SSLEngine.unwrap()
       */
        ByteBuffer inNetData = ByteBuffer.allocate(maxRecordSize);

      /** Stores decrypted data received from the peer. This buffer is filled
       *  by SSLEngine.unwrap() with decrypted application data and emptied by
       *  the application.
       */
        ByteBuffer inAppData = ByteBuffer.allocate(maxRecordSize);

      /** Stores decrypted application data that is to be sent to the other
       *  peer. The application fills this buffer, which is then emptied by
       *  SSLEngine.wrap().
       */
        ByteBuffer outAppData = ByteBuffer.allocate(maxRecordSize);

      /** Stores data that is to be sent to the network, including handshake
       *  and encrypted application data. This buffer is filled by
       *  SSLEngine.wrap() and emptied by writing it to the network.
       */
        ByteBuffer outNetData = ByteBuffer.allocate(maxRecordSize);



      //Fill the inNetData buffer with client data from the socket. Remember
      //that the first 5 bytes has already been read in the constructor.
        inNetData.put(recordHeader);
        recordHeader.position(3);
        int recordLength = Integer.parseInt(getHex(recordHeader) + getHex(recordHeader), 16);
        ByteBuffer temp = ByteBuffer.allocateDirect(recordLength);
        read(temp, false);
        temp.rewind();
        inNetData.put(temp);
        int inNetDataLength = inNetData.position();
        inNetData.rewind();
        recordHeader = null; //<--Null this out so we can process application data!


        int x=0;

        SSLEngineResult serverResult = null; //results from server's last operation


      //
        while (!isEngineClosed(sslEngine)) {

        	log().t("== Handshake ",x);


          //Read new bytes from the socket
            if (serverResult!=null){
                inNetData.clear();
                inNetDataLength = read(inNetData, false);
                inNetData.rewind();
            }



            printTLS(inNetData);


          //Try to unwrap the client message
            HandshakeStatus status = HandshakeStatus.NEED_UNWRAP;
            while (status==HandshakeStatus.NEED_UNWRAP){
                serverResult = sslEngine.unwrap(inNetData, inAppData);
                log().t("server unwrap", serverResult);
                status = runDelegatedTasks(serverResult, sslEngine);

              //Fetch more data from the client as needed
                if ((status==HandshakeStatus.NEED_UNWRAP) && (inNetDataLength==inNetData.position())) {
                    inNetData.clear();
                    inNetDataLength = read(inNetData, false);
                    inNetData.rewind();
                }
            }

            
            while (status==HandshakeStatus.NEED_WRAP){
                serverResult = sslEngine.wrap(outAppData, outNetData);
                log().t("\r\nserver wrap: ", serverResult);
                status = runDelegatedTasks(serverResult, sslEngine);
            }

            int len = outNetData.position();
            if (len>0){

                printTLS(outNetData);

                byte[] arr = new byte[len];
                outNetData.rewind();
                outNetData.get(arr);
                outNetData.clear();


                temp.clear();
                temp = ByteBuffer.allocateDirect(arr.length);
                temp.put(arr);
                temp.flip();
                connection.write(temp, temp.capacity());


            }
            else{
            	log().t("Nothing to Send?");
                throw new ServletException();
            }


            if (status==HandshakeStatus.FINISHED ||
                status==HandshakeStatus.NOT_HANDSHAKING){
                return;
            }


            x++;
        }

    }


  //**************************************************************************
  //** runDelegatedTasks
  //**************************************************************************
  /** Used to process results from the SSLEngine. If the result indicates that
   *  we have outstanding tasks to do, go ahead and run them in this thread.
   */
    private HandshakeStatus runDelegatedTasks(SSLEngineResult result,
            SSLEngine engine) throws IOException {

        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK || 
            result.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
            
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
            	log().t("\trunning delegated task...");
                runnable.run();
            }

            HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == HandshakeStatus.NEED_TASK) {
                throw new IOException(
                    "handshake shouldn't need additional tasks");
            }
            log().t("\tnew HandshakeStatus", hsStatus);

        }

        return engine.getHandshakeStatus();
    }

    private static boolean isEngineClosed(SSLEngine engine) {
        return (engine.isOutboundDone() && engine.isInboundDone());
    }


  //**************************************************************************
  //** wrap
  //**************************************************************************
  /** Used to encrypt a given buffer. Note that the output buffer size may
   *  differ from the input buffer size. The output buffer is always full.
   */
    protected ByteBuffer wrap(ByteBuffer buf) throws IOException {

        buf.rewind();

        ByteBuffer outNetData = ByteBuffer.allocate(maxRecordSize);
        SSLEngineResult serverResult = null;
        HandshakeStatus status = HandshakeStatus.NEED_WRAP;
        while (status==HandshakeStatus.NEED_WRAP){
            serverResult = sslEngine.wrap(buf, outNetData);
            log().t("server wrap", serverResult);
            status = runDelegatedTasks(serverResult, sslEngine);
        }
        

        byte[] arr = new byte[serverResult.bytesProduced()];
        outNetData.rewind();
        outNetData.get(arr);
        buf = ByteBuffer.allocate(arr.length);
        buf.put(arr);


        buf.rewind();
        return buf;
    }

//    private void log(String str, SSLEngineResult result) {
//
//        HandshakeStatus hsStatus = result.getHandshakeStatus();
//        log(str +
//            result.getStatus() + "/" + hsStatus + ", " +
//            result.bytesConsumed() + "/" + result.bytesProduced() +
//            " bytes");
//
//        //if (result.bytesProduced()>0) buf =
//        if (hsStatus == HandshakeStatus.FINISHED) {
//            log("\t...ready for application data");
//        }
//    }


	@Override
	public long getContentLengthLong() {
		// TODO Auto-generated method stub
		return 0;
	}


	@Override
	public AsyncContext startAsync() throws IllegalStateException {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
	        throws IllegalStateException {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public boolean isAsyncStarted() {
		// TODO Auto-generated method stub
		return false;
	}


	@Override
	public boolean isAsyncSupported() {
		// TODO Auto-generated method stub
		return false;
	}


	@Override
	public AsyncContext getAsyncContext() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public DispatcherType getDispatcherType() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String changeSessionId() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public boolean authenticate(HttpServletResponse response) throws IOException, javax.servlet.ServletException {
		// TODO Auto-generated method stub
		return false;
	}


	@Override
	public void login(String username, String password) throws javax.servlet.ServletException {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void logout() throws javax.servlet.ServletException {
		// TODO Auto-generated method stub
		
	}


	@Override
	public Collection<Part> getParts() throws IOException, javax.servlet.ServletException {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Part getPart(String name) throws IOException, javax.servlet.ServletException {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass)
	        throws IOException, javax.servlet.ServletException {
		// TODO Auto-generated method stub
		return null;
	}

}