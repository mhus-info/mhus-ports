package javaxt.http.servlet;
import java.io.IOException;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

//******************************************************************************
//**  JSP Servlet
//******************************************************************************
/**
 *   Http Servlet used to serve JSP files. JSP files are converted into servlet
 *   code and compiled at runtime. <p/>
 * 
 *   Note that this class is only a partial implementation of the JavaServer 
 *   Pages 2.1 specification. Tag libraries and other "advanced" features are
 *   not supported at this time.
 *
 ******************************************************************************/

public class JspServlet extends HttpServlet {

    private java.io.File jspFile;
    private long date;
    private Object instance;
    private java.lang.reflect.Method method;
    private static final Class<?>[] parameterTypes = 
            { HttpServletRequest.class, HttpServletResponse.class };
    private String pageEncoding = "ISO-8859-1";


  //**************************************************************************
  //** Constructor
  //**************************************************************************    
  /** Instantiates this class using a single JSP file.
   *  @param jspFile A JSP file.
   */
    public JspServlet(java.io.File jspFile) throws Exception {
        this.jspFile = jspFile;
        this.date = jspFile.lastModified();
        compile(jspFile);
    }


  //**************************************************************************
  //** Constructor
  //************************************************************************** 
  /** Constructor for application developers who wish to extend this class. 
   */
    protected JspServlet(){}


  //**************************************************************************
  //** processRequest
  //**************************************************************************
	public void service(ServletRequest request, ServletResponse response) throws javax.servlet.ServletException, IOException {
	        
        //response.setContentType("text/html");
        //response.setCharacterEncoding(pageEncoding);
        
        try{
            if (jspFile!=null && jspFile.lastModified()!=date){ 
                compile(jspFile);
                date = jspFile.lastModified();
            }
            method.invoke(instance, new Object[] { request, response });
        }
        catch(Exception e){
            throw new ServletException(e.getLocalizedMessage());
        }
    }


  //**************************************************************************
  //** compile
  //**************************************************************************
  /** Used to parse a JSP file and generate servlet code. The servlet is then
   *  compiled into byte-code and invoked by the processRequest() method. 
   */
    protected void compile(java.io.File jspFile) throws java.io.IOException, 
        java.lang.ClassNotFoundException, java.lang.InstantiationException, 
        java.lang.IllegalAccessException, java.lang.NoSuchMethodException {
        
        String source = this.getText(jspFile);
        String className = jspFile.getName();
        compile(source, className);
    }


  //**************************************************************************
  //** compile
  //**************************************************************************
  /** Used to parse content from a JSP file and generate servlet code. The 
   *  servlet is then compiled into byte-code and invoked by the 
   *  processRequest() method. 
   */
    protected void compile(String source, String className) throws java.io.IOException, 
        java.lang.ClassNotFoundException, java.lang.InstantiationException, 
        java.lang.IllegalAccessException, java.lang.NoSuchMethodException {
        
        
        if (className.contains(".")) className = className.substring(0, className.indexOf("."));
        
        java.util.HashSet<String> imports = new java.util.HashSet<String>();
        imports.add("javaxt.http.servlet.*");
        
        StringBuffer functions = new StringBuffer();
        StringBuffer main = new StringBuffer();
                
        String[] arr = source.split("<%");
        for (String str : arr){
            
            String code = "";
            String html = "";
            
            if (str.contains("%>")){
                code = str.substring(0, str.indexOf("%>"));
                html = str.substring(str.indexOf("%>")+2);
            }
            else{
                html = str;
            }

            
            if (code.startsWith("!")){
                functions.append(code.substring(1));
                code = "";
            }
            else if (code.startsWith("=")){
                code = code.substring(1).trim();
                code = "        str.append(" + code + ");\r\n";
            }
            else if (code.startsWith("@")){
                code = code.substring(1).trim();
                if (code.startsWith("page")){
                    code = code.substring(4).trim();
                    org.w3c.dom.NamedNodeMap attributes = getAttributes(code);
                    for (int i=0; i<attributes.getLength(); i++){
                        org.w3c.dom.Node attr = attributes.item(i);
                        String name = attr.getNodeName();
                        String value = attr.getTextContent().trim();
                        if (value.length()>0){
                            if (name.equals("import")) imports.add(value);
                            else if (name.equalsIgnoreCase("pageEncoding")) pageEncoding = value; 
                            else{ 
                                log().d(name + ": " + value);
                            }
                        }
                    }
                    code = "";
                }
            }
            else if(code.startsWith("--")){
                code = "";
            }
            else{
                code = "        " + code + "\r\n";
            }

          //Wrap html in an append statement
            html = html.replace("\"", "\\\"");
            StringBuffer s = new StringBuffer();
            for (String line : html.split("\n")){
                line = line.replace("\r", "");
                if (line.trim().length()>0){
                    s.append("        str.append(\"");
                    s.append(line);
                    s.append("\");\r\n");
                    s.append("        str.append(\"\\r\\n\");\r\n");
                }
            }
            html = s.toString();

            main.append(code);
            main.append(html);
        }
        
        
        StringBuilder src = new StringBuilder();
        java.util.Iterator<String> it = imports.iterator();
        while (it.hasNext()){
            src.append("import " + it.next() + ";\r\n");
        }
        
        src.append("public class " + className + " extends HttpServlet {\r\n");
        src.append(functions);
        src.append("    public void processRequest(HttpServletRequest request, HttpServletResponse response)\r\n");
        src.append("    throws ServletException, java.io.IOException {\r\n");
        
        /*
        response.setContentType("text/html;charset=ISO-8859-1");
        JspFactory _jspxFactory = JspFactory.getDefaultFactory();
        PageContext pageContext = _jspxFactory.getPageContext(this, request, response, null, true, 8192, true);
        
        ServletContext application = pageContext.getServletContext();
        ServletConfig config = pageContext.getServletConfig();
        HttpSession session = pageContext.getSession();
        
        JspWriter out = pageContext.getOut();
        JspWriter _jspx_out = out;
        PageContext _jspx_page_context = pageContext;
        */
        
        src.append("        StringBuffer str = new StringBuffer();\r\n");
        src.append(main);
        src.append("        response.write(str.toString());\r\n");
        src.append("    }\r\n");
        src.append("}");               
        //System.out.println(src);


        
      //Get an instance of a JavaCompiler. This requires access to the JDK.
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler==null) throw new java.io.IOException(
            "Compiler not found. Are you using a JDK?");
        
      //Then we create a custom file manager
        JavaFileManager fileManager = new ClassFileManager(
            compiler.getStandardFileManager(null, null, null));

      //Dynamic compiling requires specifying a list of "files" to compile. In 
      //our case this is a list containing one "file" which is in our case our
      //own implementation (see details below)
        java.util.List<JavaFileObject> jfiles = new java.util.ArrayList<JavaFileObject>();
        jfiles.add(new CharSequenceJavaFileObject(className, src));

      //Specify a task for the compiler. Compiler should use our file manager  
      //and our list of "files". Then we run the compilation with call()
        compiler.getTask(null, fileManager, null, null, null, jfiles).call();

      //Load the class and create a new instance
        Class classToLoad = fileManager.getClassLoader(null).loadClass(className);
        instance = classToLoad.newInstance();
        
      //Find the "processRequest" method
        method = classToLoad.getMethod("processRequest", parameterTypes);
        
    }


  //**************************************************************************
  //** getText
  //**************************************************************************
  /** Returns the content of a file as a String. */
    
    private String getText(java.io.File jspFile){
        
        if (jspFile.exists()){
            try{
                
                java.io.ByteArrayOutputStream bas = new java.io.ByteArrayOutputStream();
                java.io.FileInputStream is = new java.io.FileInputStream(jspFile);
                java.nio.channels.FileChannel inputStream = is.getChannel();

                java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocateDirect(1024);

                while (inputStream.read(buf)>-1){
                    
                    byte[] arr = new byte[buf.position()];
                    buf.rewind();
                    buf.get(arr);
                    bas.write(arr);                    
                    buf.clear();
                    buf.rewind();
                }

                inputStream.close();
                is.close();

                inputStream = null;
                is = null;
                
                return bas.toString("UTF-8");
            }
            catch(Exception e){}
        }
        
        return null;
    }


  //**************************************************************************
  //** getAttributes
  //**************************************************************************
  /** Returns attributes of JSP tag/element. */
    
    private org.w3c.dom.NamedNodeMap getAttributes(String str) {
        try{
            str = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><tag " + str + "/>";
            java.io.InputStream is = new java.io.ByteArrayInputStream(str.getBytes("UTF-8"));
            javax.xml.parsers.DocumentBuilderFactory builderFactory = 
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder builder = builderFactory.newDocumentBuilder();
            org.w3c.dom.Document xml = builder.parse(is);
            org.w3c.dom.NodeList OuterNodes = xml.getChildNodes();
            for (int i=0; i<OuterNodes.getLength(); i++ ) {
                 if (OuterNodes.item(i).getNodeType() == 1){
                     return OuterNodes.item(i).getAttributes();
                 }
            }
        }
        catch(Exception e){
            //e.printStackTrace();
        }  
        return null;
    }
    

    
  //**************************************************************************
  //** CharSequenceJavaFileObject Class
  //**************************************************************************
    
    private static class CharSequenceJavaFileObject extends SimpleJavaFileObject {

        /**
        * CharSequence representing the source code to be compiled
        */
        private CharSequence content;

        /**
        * This constructor will store the source code in the
        * internal "content" variable and register it as a
        * source code, using a URI containing the class full name
        *
        * @param className
        *            name of the public class in the source code
        * @param content
        *            source code to compile
        */
        public CharSequenceJavaFileObject(String className,
            CharSequence content) {
            super(java.net.URI.create("string:///" + className.replace('.', '/')
                + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        /**
        * Answers the CharSequence to be compiled. It will give
        * the source code stored in variable "content"
        */
        @Override
        public CharSequence getCharContent(
            boolean ignoreEncodingErrors) {
            return content;
        }
    }
    
    private static class JavaClassObject extends SimpleJavaFileObject {

        /**
        * Byte code created by the compiler will be stored in this
        * ByteArrayOutputStream so that we can later get the
        * byte array out of it
        * and put it in the memory as an instance of our class.
        */
        protected final java.io.ByteArrayOutputStream bos =
            new java.io.ByteArrayOutputStream();

        /**
        * Registers the compiled class object under URI
        * containing the class full name
        *
        * @param name
        *            Full name of the compiled class
        * @param kind
        *            Kind of the data. It will be CLASS in our case
        */
        public JavaClassObject(String name, Kind kind) {
            super(java.net.URI.create("string:///" + name.replace('.', '/')
                + kind.extension), kind);
        }

        /**
        * Will be used by our file manager to get the byte code that
        * can be put into memory to instantiate our class
        *
        * @return compiled byte code
        */
        public byte[] getBytes() {
            return bos.toByteArray();
        }

        /**
        * Will provide the compiler with an output stream that leads
        * to our byte array. This way the compiler will write everything
        * into the byte array that we will instantiate later
        */
        @Override
        public java.io.OutputStream openOutputStream() throws java.io.IOException {
            return bos;
        }
    }



    private static class ClassFileManager extends ForwardingJavaFileManager {
        /**
        * Instance of JavaClassObject that will store the
        * compiled bytecode of our class
        */
        private JavaClassObject jclassObject;

        /**
        * Will initialize the manager with the specified
        * standard java file manager
        *
        * @param standardManger
        */
        public ClassFileManager(StandardJavaFileManager
            standardManager) {
            super(standardManager);
        }

        /**
        * Will be used by us to get the class loader for our
        * compiled class. It creates an anonymous class
        * extending the SecureClassLoader which uses the
        * byte code created by the compiler and stored in
        * the JavaClassObject, and returns the Class for it
        */
        @Override
        public ClassLoader getClassLoader(Location location) {
            return new java.security.SecureClassLoader() {
                //@Override
                protected Class<?> findClass(String name)
                    throws ClassNotFoundException {
                    byte[] b = jclassObject.getBytes();
                    return super.defineClass(name, jclassObject
                        .getBytes(), 0, b.length);
                }
            };
        }

        /**
        * Gives the compiler an instance of the JavaClassObject
        * so that the compiler can write the byte code into it.
        */
        @Override
        public JavaFileObject getJavaFileForOutput(Location location,
            String className, Kind kind, FileObject sibling)
                throws java.io.IOException {
                jclassObject = new JavaClassObject(className, kind);
            return jclassObject;
        }
    }

}