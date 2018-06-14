package javaxt.http.servlet;
import java.io.IOException;

//******************************************************************************
//**  FormInput
//******************************************************************************
/**
 *   Used to represent a form input found in the body of an http request.
 *
 ******************************************************************************/

public class FormInput {

    private String name;
    private ServletInputStream is;
    private boolean readFully = false;
    private FormValue value;
    private String boundary;
    private java.util.HashMap<String, String> metadata;
    private String contentDisposition;
    private String fileName;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to create a new instance of this class using
   *
   */
    protected FormInput(ServletInputStream is, FormInput prevInput, String boundary) throws IOException {

        this.is = is;
        this.boundary = boundary;


      //Read all remaining bytes from the previous input as needed.
        if (prevInput!=null){
            if (!prevInput.readFully) prevInput.getValue().readFully();
            prevInput.readFully = true;
        }



        if (boundary.equals("&")){ //Content-Type: application/x-www-form-urlencoded

            //TODO: Check first byte. See if it start with a "&".
            //byte a = (byte) is.read();
            //if (a=='&') a = (byte) is.read();

            
            java.io.ByteArrayOutputStream bas = new java.io.ByteArrayOutputStream();
            while (true){
                byte a = (byte) is.read();

                if (a=='=' || a==-1) break;
                bas.write(a);
            }

            if (bas.size()<1) throw new IOException();

            name = bas.toString();
            value = new FormValue(this);

        }
        else{ //Content-Type: multipart/form-data

            if (prevInput==null) is.readLine();


          //Extract form metadata (e.g. "Content-Type", "Content-Disposition", etc.)
            metadata = new java.util.HashMap<String, String>();
            while (true){
                String str = new String(is.readLine(), "UTF-8").trim();
                if (str.length()==0) break;

                String key = str.substring(0, str.indexOf(":")).trim();
                String val = str.substring(str.indexOf(":")+1).trim();
                metadata.put(key, val);
            }


          //Extract input name from the "Content-Disposition"
            java.util.Iterator<String> it = metadata.keySet().iterator();
            while (it.hasNext()){
                String key = it.next();
                if (key.equalsIgnoreCase("Content-Disposition")){
                    contentDisposition = metadata.get(key);
                    if (contentDisposition!=null){
                        for (String str : contentDisposition.split(";")){
                            str = str.trim();
                            if (str.contains("=")){
                                String val = str.substring(str.indexOf("\"")+1, str.lastIndexOf("\"")).trim();
                                if (str.toLowerCase().startsWith("name=")){
                                    name = val;
                                }
                                else if (str.toLowerCase().startsWith("filename=")){
                                    if (val.length()>0) fileName = val;
                                }
                            }
                        }
                    }
                    break;
                }
            }
            
            
          //Throw an exception if no form name is found. This is our key to
          //stop parsing form data.
            if (name==null) throw new IOException();

            
          //If the input is a file and no filename is supplied, don't generate
          //a value. 
            if (this.isFile()){
                if (fileName==null) return;
            }

          //If we're still here, get the form value.            
            value = new FormValue(this);
        }
    }


  //**************************************************************************
  //** getContentDisposition
  //**************************************************************************
  /** Returns the "Content-Disposition" value associated with this form input.
   *  This attribute is unique to "multipart/form-data".
   */
    public String getContentDisposition(){
        return contentDisposition;
    }


  //**************************************************************************
  //** getFileName
  //**************************************************************************
  /** Returns the "filename" attribute found in the "Content-Disposition"
   *  value associated with this form input. This attribute is unique to
   *  "multipart/form-data".
   */
    public String getFileName(){
        return fileName;
    }


  //**************************************************************************
  //** isFile
  //**************************************************************************
  /** Convenience method used to determine whether the input is associated
   *  with a file upload. Returns true if a "filename" attribute is found in
   *  the "Content-Disposition" metadata. This attribute is unique to
   *  "multipart/form-data". 
   */
    public boolean isFile(){
        return fileName!=null;
    }
    

  //**************************************************************************
  //** getMetadata
  //**************************************************************************
  /** Used to return metadata associated with this form input. This attribute 
   *  is unique to "multipart/form-data". Each input may include information
   *  such as the "Content-Disposition", "Content-Type", and 
   *  "Content-Transfer-Encoding".
   */
    public java.util.HashMap<String, String> getMetadata(){
        return metadata;
    }


  //**************************************************************************
  //** getName
  //**************************************************************************
  /** Used to return the name associated with this form input. */

    public String getName(){
        return name;
    }


  //**************************************************************************
  //** getValue
  //**************************************************************************
  /** Used to return the value associated with this form input. */

    public FormValue getValue(){
        return value;
    }



    protected String getBoundary(){
        return boundary;
    }

    protected ServletInputStream getInputStream(){
        return is;
    }

    /** Used to set flag used to indicate whether the entire input has been read. */
    protected void setReadFully(){
        this.readFully = true;
    }

    /** Returns boolean used to indicate whether the entire input has been read.*/
    protected boolean isFullyRead(){
        return readFully;
    }

}