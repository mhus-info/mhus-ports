package javaxt.http.servlet;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

//******************************************************************************
//**  FormValue
//******************************************************************************
/**
 *   Used to retrieve the value associated with a form input found in the body
 *   of an http request. Form values are retrieved on-demand.
 *
 ******************************************************************************/

public class FormValue {

    private FormInput input;
    private byte[] value = null;
    private final static byte[] CRLF = new byte[]{'\r','\n'};
    private FormInputStream formInputStream;
    

  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of Value. */

    protected FormValue(FormInput input){
        this.input = input;
        formInputStream = new FormInputStream();
    }


  //**************************************************************************
  //** toInteger
  //**************************************************************************
  /** Returns the value as an integer. Returns a null if there was a problem
   *  converting the value to an integer or if the value is null.
   */
    public Integer toInteger(){
        if (isNull()) return null;
        try{
            return Integer.valueOf(this.toString());
        }
        catch(Exception e){
            return null;
        }
    }


  //**************************************************************************
  //** toShort
  //**************************************************************************
  /** Returns the value as a short. Returns a null if there was a problem
   *  converting the value to a short or if the value is null.
   */
    public Short toShort(){
        if (isNull()) return null;
        try{
            return Short.valueOf(this.toString());
        }
        catch(Exception e){
            return null;
        }
    }


  //**************************************************************************
  //** toDouble
  //**************************************************************************
  /** Returns the value as a double. Returns a null if there was a problem
   *  converting the value to a double or if the value is null.
   */
    public Double toDouble(){
        if (isNull()) return null;
        try{
            return Double.valueOf(this.toString());
        }
        catch(Exception e){
            return null;
        }
    }


  //**************************************************************************
  //** toLong
  //**************************************************************************
  /** Returns the value as a long. Returns a null if there was a problem
   *  converting the value to a long or if the value is null.
   */
    public Long toLong(){
        if (isNull()) return null;
        try{
            return Long.valueOf(this.toString());
        }
        catch(Exception e){
            return null;
        }
    }


  //**************************************************************************
  //** toBigDecimal
  //**************************************************************************
  /** Returns the value as a BigDecimal. Returns a null if there was a problem
   *  converting the value to a BigDecimal or if the value is null.
   */
    public java.math.BigDecimal toBigDecimal(){
        if (isNull()) return null;
        try{
            return java.math.BigDecimal.valueOf(toDouble());
        }
        catch(Exception e){
            return null;
        }
    }


  //**************************************************************************
  //** toByteArray
  //**************************************************************************
  /** Returns the value as a byte array.
   */
    public byte[] toByteArray(){
        return getByteArray();
    }



  //**************************************************************************
  //** toBoolean
  //**************************************************************************
  /** Returns the value as a boolean. Performs a case insensitive comparison
   *  between string values representing known booleans (e.g. "true", "false",
   *  "yes", "no", "t", "f", "y", "n", "1", "0").
   */
    public Boolean toBoolean(){
        if (isNull()) return null;
        
        String value = this.toString().toLowerCase().trim();

        if (value.equals("true")) return true;
        if (value.equals("false")) return false;

        if (value.equals("yes")) return true;
        if (value.equals("no")) return false;

        if (value.equals("y")) return true;
        if (value.equals("n")) return false;

        if (value.equals("t")) return true;
        if (value.equals("f")) return false;

        if (value.equals("1")) return true;
        if (value.equals("0")) return false;

        return null;
    }


  //**************************************************************************
  //** isNumeric
  //**************************************************************************
  /**  Used to determine if the value is numeric. */

    public boolean isNumeric(){
        if (isNull()) return false;
        if (toDouble()==null) return false;
        else return true;
    }


  //**************************************************************************
  //** isNull
  //**************************************************************************
  /**  Used to determine whether the value is null. */

    public boolean isNull(){
        return getByteArray().length<1;
    }


  //**************************************************************************
  //** toString
  //**************************************************************************
  /** Returns the value as a string. Automatically decodes the data if it is
   *  url encoded.
   */
    public String toString(){
        String value = new String(getByteArray());
        if (input.getBoundary().equals("&")){

          //Decode the value
            try{
                value = java.net.URLDecoder.decode(value, "UTF-8");
            }
            catch(Exception e){
              //Try to decode the string manually
                String find[] = new String[]{"%2C","%2F","%3A"};
                String replace[] = new String[]{",","/",":"};
                for (int i=0; i<find.length; i++){
                     value = value.replace(find[i],replace[i]);
                }
            }

        }
        return value;
    }


  //**************************************************************************
  //** toFile
  //**************************************************************************
  /** Used to save a form value to a file. This is particularly useful when
   *  processing large binary data (e.g. uploaded file).
   */
    public boolean toFile(java.io.File file){
        try{
            if (value==null){

                int bufferSize = 2048;
                if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                FileOutputStream output = new FileOutputStream(file);
                final ReadableByteChannel inputChannel = Channels.newChannel(getInputStream());
                final WritableByteChannel outputChannel = Channels.newChannel(output);
                final java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(bufferSize);

                while (inputChannel.read(buffer) != -1) {
                    buffer.flip();
                    outputChannel.write(buffer);
                    buffer.compact();
                }
                buffer.flip();
                while (buffer.hasRemaining()) {
                    outputChannel.write(buffer);
                }

                inputChannel.close();
                outputChannel.close();

                //file.setLastModified(File.lastModified());
                return true;


            }
            else{

                FileOutputStream output = new FileOutputStream(file);
                output.write(value);
                output.close();
                return true;

            }
        }
        catch(Exception e){
            e.printStackTrace();
            return false;
        }
    }


  //**************************************************************************
  //** getInputStream
  //**************************************************************************
  /** Returns a java.io.InputStream used to read raw bytes associated with
   *  the form value. This is particularly useful when processing large
   *  values (e.g. uploaded file).
   */
    public java.io.InputStream getInputStream(){
        return formInputStream;
    }


  //**************************************************************************
  //** readFully
  //**************************************************************************
  /** Reads any remaining bytes associated with this input. This is extremely
   *  important if we're going to give users access to the raw InputStream.
   */
    protected void readFully(){
        if (value!=null) return;

        try{
            java.io.InputStream inputStream = this.getInputStream();
            while (inputStream.read()!=-1){}
        }
        catch(Exception e){
        }
    }


  //**************************************************************************
  //** getByteArray
  //**************************************************************************
  /** Used to read data from the client into a byte array. The byte array is
   *  then persisted as a class variable.
   */
    private byte[] getByteArray(){

        if (value==null){

            ByteArrayOutputStream bas = new ByteArrayOutputStream();
            java.io.InputStream inputStream = this.getInputStream();
            try{
                int x=0;
                while ( (x = inputStream.read()) != -1) {
                   bas.write(x);
                }
                value = bas.toByteArray();
            }
            catch(Exception e){
                e.printStackTrace();
                value = new byte[0]; //<-- is this a good idea??
            }
        }

        return value;
    }


  //**************************************************************************
  //** FormInputStream
  //**************************************************************************
  /** Simple implementation of a java.io.InputStream used to read bytes
   *  associated with a form value.
   */
    private class FormInputStream extends java.io.InputStream {

        private byte[] cache;
        private String boundary;
        private ServletInputStream is;

        public FormInputStream(){
            boundary = input.getBoundary();
            is = input.getInputStream();
            cache = new byte[0];
        }


        public int read() throws IOException {

            if (input.isFullyRead()) return -1;

            if (boundary.equals("&")){ //Content-Type: application/x-www-form-urlencoded

                int a = getNextByte();
                if (a=='&' || a==-1){
                    input.setReadFully();
                    return -1;
                }
                else return a;

            }
            else{ //Content-Type: multipart/form-data

                int a = getNextByte();
                if (a==-1) return -1;

                if (a=='\r'){

                    int b = getNextByte();
                    if (b==-1) return -1;

                    if (b=='\n'){


                        byte[] arr = new byte[boundary.length()+2];
                        read(arr);

                        String str = new String(arr);
                        if (str.equals("--" + boundary)){

                          //Read next few bytes: either "\r\n" or "--\r\n" or EOS
                            while (true){
                                arr = new byte[2];
                                if (read(arr)<0) break;
                                if (java.util.Arrays.equals(arr, CRLF)) break;
                            }

                            input.setReadFully();
                            return -1;
                        }
                        else{
                            for (int i=arr.length-1; i>-1; i--){
                                updateCache(arr[i]);
                            }
                            updateCache((byte) b);
                            return a;
                        }
                    }
                    else{
                        updateCache((byte) b);
                        return a;
                    }
                }
                else{
                    return a;
                }
            }
            //return -1;
        }


        public int read(byte[] b) throws IOException{
            int totalBytesRead = 0;
            for (int i=0; i<b.length; i++){
                int x = getNextByte();
                if (x==-1){
                    totalBytesRead = -1; //<-- This violates the java spec...
                    break;
                }
                else{
                    b[i] = (byte) x; 
                    totalBytesRead++;
                }
            }
            return totalBytesRead;
        }


      /** Prepends a byte to the cache */
        private void updateCache(byte b){
            byte[] temp = new byte[cache.length + 1];
            temp[0] = b;
            int i = 1;
            for (byte _byte : cache){
                temp[i] = _byte;
                i++;
            }
            cache = temp;
            temp = null;
        }


      /** Returns the next available byte from the cache. If the cache is
       *  empty, returns the next available byte from the socket channel.
       */
        private int getNextByte() throws IOException {

            if (cache.length>0){
                byte b = cache[0];
                byte[] a = new byte[cache.length-1];
                System.arraycopy(cache, 1, a, 0, a.length);
                cache = a;
                return (int) b & 0xFF; //convert unsigned Byte to Int
                //return b;
            }
            else{
                return is.read();
            }
        }
    }
}