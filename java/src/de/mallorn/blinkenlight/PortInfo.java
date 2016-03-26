package de.mallorn.blinkenlight;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jssc.SerialPort;
import jssc.SerialPortException;

public class PortInfo {

	public final String name;
	private volatile long lastAccess;
	private volatile boolean connected;
	private final SerialPort serialPort;
	private final Map<String, String> lastState = new HashMap<>();
	
	public PortInfo(String name) {
		this.name = name;
		this.serialPort = new SerialPort(name);
	}

	public long getLastAccess() {
		return lastAccess;
	}
	
	private void touchLastAccess() {
		lastAccess = System.currentTimeMillis();
	}
	
	public boolean isConnected() {
		return connected;
	}
	
	private String sendCommand(String command) throws IOException {
		if (!connected) {
			openPort();
		}
		
		synchronized (serialPort) {
			try {
				// drain input buffer
				serialPort.readBytes();
				
				serialPort.writeBytes((command + "\n").getBytes("ISO-8859-1"));
				
				return receiveLine(1000);
			} catch (SerialPortException|InterruptedException e) {
				closePort();
				throw new IOException(e);
			}
		}		
	}
	
	private String receiveLine(long timeout) throws InterruptedException, SerialPortException {
		long timeoutTime = System.currentTimeMillis() + timeout;
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		while (true) {
			byte[] buffer = serialPort.readBytes();
			if (buffer != null) {
				try {
					baos.write(buffer);
					if (buffer[buffer.length - 1] == '\n') {
						touchLastAccess();
						return baos.toString("ISO-8859-1");
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else if (timeoutTime > System.currentTimeMillis()) {
				Thread.sleep(50);
			} else {
				throw new InterruptedException("Serial response timeout");
			}
		}
	}
	
	public synchronized String executeCommand(String command) throws IOException {
		System.out.println("executing command " + name + " " + command);
		
		String prefix = command.substring(0, 1);
		if (!prefix.equals("I") && !prefix.equals("V")) {
			lastState.put(prefix, command);
		}
		
		return sendCommand(command);
	}
	
	public synchronized void resendLastState() throws IOException {
		for (String command: lastState.values()) {
			sendCommand(command);
		}
	}
	
	private void closePort() {
		try {
			serialPort.closePort();
		} catch (SerialPortException e) {}
		connected = false;
	}
	
	private void openPort() throws IOException {
		if (serialPort.isOpened()) {
			closePort();
		}

		System.out.println("opening port " + name);
		try {
		    serialPort.openPort();
		    serialPort.setParams(SerialPort.BAUDRATE_57600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
		    // wait for Arduino to finish initialization
		    try {
				receiveLine(3000);
			} catch (InterruptedException e) {}
		    connected = true;
		} catch (SerialPortException e) {
			throw new IOException(e);
		}
	}
	
	public synchronized String executeCommands(String command) throws IOException {
		StringBuffer sb = new StringBuffer();
		for (String c: command.split("\\s+")) { 
			sb.append(executeCommand(c).trim() + "\n");
		}
		return sb.toString();
	}
	
	
}
