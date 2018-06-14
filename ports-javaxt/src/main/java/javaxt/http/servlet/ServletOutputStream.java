package javaxt.http.servlet;
import java.io.IOException;

import javax.servlet.WriteListener;

import de.mhus.lib.errors.NotSupportedException;
import javaxt.http.servlet.HttpServletResponse.ConnectionOutputStream;

//******************************************************************************
//**  ServletOutputStream
//******************************************************************************
/**
 *   Provides an output stream for writing to the body of an http response. 
 *   Writes raw bytes to a socket connection. Automatically encrypts the data 
 *   if the socket is SSL/TLS encrypted.
 *
 ******************************************************************************/

public class ServletOutputStream extends javax.servlet.ServletOutputStream {
    
    private ConnectionOutputStream out;
    
    public ServletOutputStream(ConnectionOutputStream out){
        this.out = out;
    }
    
    public void write(int b) throws IOException {
        out.write(b);
    }
    
    public void flush() throws IOException{
        out.flush();
    }
    
    public void close() throws IOException{
        out.close();
    }

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public void setWriteListener(WriteListener writeListener) {
		throw new NotSupportedException();
	}    
}