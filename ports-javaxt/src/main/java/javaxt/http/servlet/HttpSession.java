package javaxt.http.servlet;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpSessionContext;

//******************************************************************************
//**  HttpSession
//******************************************************************************
/**
 *   Provides a way to identify a user and store information about the user 
 *   across multiple page requests/visits.
 *
 *   Note that this server uses cookies to manage sessions.
 *
 ******************************************************************************/

public class HttpSession implements javax.servlet.http.HttpSession {

    private static final ConcurrentHashMap<String, HttpSession> sessions =
                            new ConcurrentHashMap<String, HttpSession>();

    private static final ConcurrentHashMap<String, Object> map =
                            new ConcurrentHashMap<String, Object>();

    private String sessionID;
    private long lastAccessTime = -1;
    private long creationTime;



  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new session and adds it to the list of active sessions.
   */
    protected HttpSession(){

      //Create new session and add it to the list of active sessions
        while (true){
            sessionID = CreateID(28);
            synchronized(sessions){
                if (sessions.get(sessionID)==null){
                    creationTime = new java.util.Date().getTime();
                    sessions.put(sessionID, this);
                    sessions.notifyAll();
                    break;
                }
            }
        }

      //TODO: Create timer task to periodically clean up old sessions
        
    }


  //**************************************************************************
  //** getServletContext
  //**************************************************************************
  /** Returns the ServletContext.
   */
    public ServletContext getServletContext(){
        return HttpServlet.context;
    }


  //**************************************************************************
  //** getID
  //**************************************************************************
  /** Returns a string containing the unique identifier assigned to this
   *  session.
   */
    public String getID(){
        return sessionID;
    }


  //**************************************************************************
  //** getCreationTime
  //**************************************************************************
  /** Returns the time when this session was created, measured in milliseconds 
   *  since midnight January 1, 1970 GMT. 
   */
    public long getCreationTime(){
        return creationTime;
    }


  //**************************************************************************
  //** getLastAccessedTime
  //**************************************************************************
  /** Returns the time when this session was last accessed, measured in
   *  milliseconds since midnight January 1, 1970 GMT.
   */
    public long getLastAccessedTime() {
        return lastAccessTime;
    }


  //**************************************************************************
  //** isNew
  //**************************************************************************
  /** Returns true if the client does not yet know about the session or if the
   *  server has not accessed the session.
   */
    public boolean isNew(){
        return lastAccessTime<0;
    }


  //**************************************************************************
  //** getAttribute
  //**************************************************************************
  /** Returns the object bound with the specified name in this session, or
   *  null if no object is bound under the name.
   */
    public Object getAttribute(String name){
        Object val = null;
        synchronized(map){
            val = map.get(name);
        }
        return val;
    }


  //**************************************************************************
  //** setAttribute
  //**************************************************************************
  /** Binds an object to this session, using the name specified.
   */
    public void setAttribute(String name, Object value){
        synchronized(map){
            map.put(name, value);
            map.notifyAll();
        }
    }


  //**************************************************************************
  //** removeAttribute
  //**************************************************************************
  /** Removes the attribute with the given name from the servlet context. */
    public void removeAttribute(String name){
        synchronized(map){
            map.remove(name);
            map.notifyAll();
        }
    }


  //**************************************************************************
  //** invalidate
  //**************************************************************************
  /** Invalidates this session then unbinds any objects bound to it.
   */
    public void invalidate(){
        synchronized(sessions){
            sessions.remove(sessionID);
            sessions.notifyAll();
        }
        synchronized(map){
            map.clear();
            map.notifyAll();
        }
    }



    /** Returns a session associated with a given session ID. */
    protected static final HttpSession get(String sessionID) {
        HttpSession session;
        synchronized(sessions){
            session = sessions.get(sessionID);
            if (session!=null) session.lastAccessTime = new java.util.Date().getTime();
        }
        return session;
    }




    /** Generates a random sequence of alpha-numeric characters. */
    private static final String CreateID(int len){
        StringBuffer str = new StringBuffer(len);
        final String strValid = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i=1; i<=len; i++){
             int x = new java.util.Random().nextInt(strValid.length());
             str.append( strValid.substring(x,x+1) );
        }
        return str.toString();
    }

    public String toString(){
        return getID();
    }


	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public void setMaxInactiveInterval(int interval) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public int getMaxInactiveInterval() {
		// TODO Auto-generated method stub
		return 0;
	}


	@Override
	public HttpSessionContext getSessionContext() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Object getValue(String name) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Enumeration<String> getAttributeNames() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String[] getValueNames() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public void putValue(String name, Object value) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void removeValue(String name) {
		// TODO Auto-generated method stub
		
	}
}
