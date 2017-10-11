package de.mhus.ports.jbossmodules;

import java.io.File;
import java.io.IOException;

public class TestMain {
// http://grepcode.com/file/repository.jboss.org/nexus/content/repositories/releases/org.jboss.as/jboss-as-server/7.0.0.Beta1/org/jboss/as/server/Main.java
	
	public static void main(String[] args) throws IOException, InterruptedException {
		WildflyController control = WildflyController.getInstance();
		control.setHome(new File("/Users/mikehummel/Downloads/BonitaBPMCommunity-7.5.4-wildfly-10.1.0.Final/server"));
		System.out.println("START");
		control.start();
		
		while (true) {
			Thread.sleep(10000);
			
			try {
				Class<?> clazz = control.getClassLoader().loadClass("org.jboss.as.server.Bootstrap");
				System.out.println("Clazz found!");
				
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
	}

}
