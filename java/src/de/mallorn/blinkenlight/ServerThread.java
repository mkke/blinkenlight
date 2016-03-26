package de.mallorn.blinkenlight;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerThread extends Thread {
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