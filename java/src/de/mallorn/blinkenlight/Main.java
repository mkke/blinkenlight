package de.mallorn.blinkenlight;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jssc.SerialPort;
import jssc.SerialPortException;

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
			executeCommand(args[0], cmd.toString());
			
			Timer timer = new Timer("IdleTimer", true);
			timer.scheduleAtFixedRate(new TimerTask() {

				@Override
				public void run() {
					List<String> ports = new LinkedList<>();
					synchronized (openPorts) {
						ports.addAll(openPorts.keySet());
					}
					
					for (String port: ports) {
						try {
							executeCommand(port, "I");
						} catch (IOException e) {}
					}
				}
				
			}, 60000, 60000);
		} catch (IOException e) {
			executeRemoteCommand(args[0], cmd.toString());
		}
	}

	private static void executeRemoteCommand(String port, String command) throws IOException {
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

	private static Map<String, SerialPort> openPorts = Collections.synchronizedMap(new HashMap<>()); 
	
	private static void executeCommand(String port, String command) throws IOException {
		System.out.println("executing command " + port + " " + command);
		SerialPort serial = null;
		synchronized (openPorts) {
			serial = openPorts.get(port);
			if (serial == null || !serial.isOpened()) {
				System.out.println("opening port " + port);
				try {
					serial = new SerialPort(port);
				    serial.openPort();
				    serial.setParams(SerialPort.BAUDRATE_57600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				    // wait for Arduino to finish initialization
				    try {
						Thread.sleep(3000L);
					} catch (InterruptedException e) {}
				    openPorts.put(port, serial);
				} catch (SerialPortException e) {
					throw new IOException(e);
				}
			}
		}
		
		synchronized (serial) {
			try {
				for (String c: command.split("\\s+")) { 
					serial.writeBytes((c + "\n").getBytes("ISO-8859-1"));
				}
			} catch (SerialPortException e) {
				synchronized (openPorts) {
					try {
						serial.closePort();
					} catch (SerialPortException e1) {}
					openPorts.remove(port);
				}
				throw new IOException(e);
			}
		}		
	}

	static class ServerThread extends Thread {
		private final ServerSocket serverSocket;
		
		public ServerThread(int port) throws IOException {
			super("ServerThread");
			serverSocket = new ServerSocket(port, 0, InetAddress.getLoopbackAddress());
		}
		
		public void run() {
			try {
				while (true) {
					Socket client = serverSocket.accept();
					System.out.println("accepting new connection from " + client.getRemoteSocketAddress()); 
					new ConnectionThread(client).start();
				}
			} catch (IOException e) {}
		}
	}
	
	static class ConnectionThread extends Thread {
		private final Socket client;
		public ConnectionThread(Socket client) {
			super("Connection Thread " + client);
			this.client = client;
		}
		
		private static final Pattern linePattern = Pattern.compile("^(\\S+)\\s+(.+)$");
		
		public void run() {
			try {
				client.setSoTimeout(10000);
				Reader r = new InputStreamReader(client.getInputStream(), "ISO-8859-1");
				Writer w = new OutputStreamWriter(client.getOutputStream(), "ISO-8859-1");
				
				
				try (Scanner s = new Scanner(r)) {
					while (s.hasNextLine()) {
						Matcher m = linePattern.matcher(s.nextLine());
						if (m.find()) {
							executeCommand(m.group(1), m.group(2));
							w.write("OK\n");
						} else {
							w.write("Syntax error\n");
						}
						w.flush();
					}
				} catch (SocketTimeoutException te) {
					w.write("Command timeout, disconnecting\n");
				}
			} catch (IOException e) {}
			try {
				client.close();
			} catch (IOException e) {}
		}
	}
	
}
