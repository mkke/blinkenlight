package de.mallorn.blinkenlight;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.GenericArrayType;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Map;
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

		final String port = args[0];
		
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
			
			if (cmd.toString().equals("perf")) {
				System.out.println("enabling performance counter updates on port " + port);
				WindowsPerformance perf = new WindowsPerformance();
				perf.addListener(new WindowsPerformance.Listener() {
					
					int lastColor = -1;
					int lastSpeed = -1;
					
					@Override
					public void onCounterChanged(Map<String, String> values) {
						float cpuTime = 0;
						int cpuFreqCount = 0;
						int cpuFreqSum = 0;
						for (Map.Entry<String, String> value: values.entrySet()) {
							if (value.getKey().endsWith("Prozessorzeit (%)")) {
								cpuTime = Float.parseFloat(value.getValue());
							} else if (value.getKey().endsWith("Prozessorfrequenz")) {
								float cpuFreq = Float.parseFloat(value.getValue());
								if (cpuFreq > 0) { // totals have freq == 0
									cpuFreqSum += cpuFreq;
									cpuFreqCount++;
								}
							}
						}
						int cpuFreqAvg = cpuFreqSum / cpuFreqCount;
						
						int color = (int) Math.min(Math.max(0, cpuTime * 255 / 100), 255);
						int speed = MIN_SPEED + ((cpuFreqAvg - MIN_CPU_FREQ) * (MAX_SPEED - MIN_SPEED) / (MAX_CPU_FREQ - MIN_CPU_FREQ));
//						System.out.println("cpu time % = " + cpuTime + ", freq = " + cpuFreqAvg);
						
						try {
							if (lastColor < 0 || Math.abs(lastColor - color) > 5) {
								Ports.getPortInfo(port).executeCommand("C" + color);
								lastColor = color;
							}
							
							if (lastSpeed < 0 || Math.abs(lastSpeed - speed) > 3) {
								Ports.getPortInfo(port).executeCommand("S" + speed);
								lastSpeed = speed;
							}
						} catch (IOException e) {}
					}
				});
				perf.startCounter(3);
			} else {
				Ports.getPortInfo(port).executeCommands(cmd.toString());
			}
		} catch (IOException e) {
			executeRemoteCommands(args[0], cmd.toString());
		}
	}

	private static final int MIN_SPEED = 2;
	private static final int MAX_SPEED = 80;
	
	public static final int MIN_CPU_FREQ = 1200;
	public static final int MAX_CPU_FREQ = 4400;
	
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
