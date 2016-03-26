package de.mallorn.blinkenlight;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class Main {

	public static final int PORT = 29117;
	
	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.out.println("blinkenlight [port] [command]");
			System.exit(0);
		}

		StringBuffer cmd = new StringBuffer();
		cmd.append(args[1]);
		for (int i = 2; i < args.length; i++) {
			cmd.append(" ");
			cmd.append(args[i]);
		}
		
		try {
			ServerThread serverThread = new ServerThread(PORT);
			System.out.println("starting blinkenlight server on port " + PORT);
			serverThread.start();
			
			Ports.getPortInfo(args[0]).executeCommands(cmd.toString());
			
			Timer timer = new Timer("IdleTimer", true);
			timer.scheduleAtFixedRate(new TimerTask() {

				private long lastTime = System.currentTimeMillis();
				
				@Override
				public void run() {
					long now = System.currentTimeMillis();
					boolean timeWarp = (now - lastTime > 10000L);
					lastTime = now;
					
					if (timeWarp) {
						System.out.println("time warp detected, resending last state");
					}
					
					for (PortInfo pi: Ports.getPortInfos()) {
						try {
							if (!pi.isConnected() || timeWarp) {
								pi.resendLastState();
							} else if (now - pi.getLastAccess() > 60000L) {
								pi.executeCommand("I");
							}
						} catch (IOException e) {
							System.out.println(pi.name + ": " + e.toString());
						}
					}
				}
				
			}, 1000, 1000);
		} catch (IOException e) {
			executeRemoteCommands(args[0], cmd.toString());
		}
	}

	private static void executeRemoteCommands(String port, String command) throws IOException {
		System.out.println("delegating command to running server");
		try (Socket client = new Socket(InetAddress.getLoopbackAddress(), PORT)) {
			client.setSoTimeout(10000);
			
			Writer w = new OutputStreamWriter(client.getOutputStream(), "ISO-8859-1");
	
			w.write(port + " " + command + "\n");
			w.flush();
			client.shutdownOutput();
	
			try (
				Reader r = new InputStreamReader(client.getInputStream(), "ISO-8859-1"); 
				Scanner s = new Scanner(r);
			) {
				while (s.hasNextLine()) {
					System.out.println(s.nextLine());
				}
			}
		}
	}

}
