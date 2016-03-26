package de.mallorn.blinkenlight;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConnectionThread extends Thread {
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
						try {
							PortInfo pi = Ports.getPortInfo(m.group(1));
							String response = pi.executeCommands(m.group(2));
							w.write(response.trim() + "\n");
						} catch (IOException e) {
							w.write(e.toString() + "\n");
						}
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