package javaxt.http.servlet;

//******************************************************************************
//**  Authenticator Interface
//******************************************************************************
/**
 *   Implementations of this class are used to parse credentials and  
 *   authenticate client requests. 
 *
 ******************************************************************************/

public interface Authenticator {


  /** String identifier for "BASIC" authentication. */
    public static final String BASIC_AUTH = "BASIC";

  /** String identifier for "FORM" authentication. */
    public static final String FORM_AUTH = "FORM";

  /** String identifier for Client Certificate authentication.*/
    public static final String CLIENT_CERT_AUTH = "CLIENT_CERT";

  /** String identifier for "DIGEST" authentication. */
    public static final String DIGEST_AUTH = "DIGEST";


  //**************************************************************************
  //** newInstance
  //**************************************************************************
  /** Returns a new instance of an Authenticator used to authenticate requests.
   *  This method is called with each new http request.
   */
    public Authenticator newInstance(HttpServletRequest request);


  //**************************************************************************
  //** getCredentials
  //**************************************************************************
  /** Returns an array representing the client credentials associated with
   *  this request. The first element in the array represents the username 
   *  and the second element represents the password. Client credentials may
   *  be found in the "Authorization" request header, in a client certificate,
   *  etc. Implementations of this class must communicate the authentication
   *  scheme via the getAuthType() method. If the Authenticator fails to parse 
   *  the credentials, this method returns a null.
   */
    public String[] getCredentials();


  //**************************************************************************
  //** authenticate
  //**************************************************************************
  /** Used to authenticate a client request. If the Authenticator fails to
   *  authenticate the client, this method throws a ServletException.
   */
    public void authenticate() throws ServletException;


  //**************************************************************************
  //** getUserPrincipal
  //**************************************************************************
  /** Returns a java.security.Principal object containing the name of a given 
   *  user. If the user has not been authenticated, the method returns a null.
   */
    public java.security.Principal getPrinciple();


  //**************************************************************************
  //** isUserInRole
  //**************************************************************************
  /** Returns a boolean indicating whether a user is included in the specified
   *  "role". Roles and role membership are often managed by instances of this
   *  class using deployment descriptors. If the user is not authenticated, or
   *  if no role is defined for the user, the method returns false.
   */
    public boolean isUserInRole(String role);


  //**************************************************************************
  //** getAuthType
  //**************************************************************************    
  /** Returns the authentication scheme used to authenticate clients (e.g. 
   *  "BASIC", "DIGEST", "CLIENT_CERT", etc). 
   */
    public String getAuthType();

}