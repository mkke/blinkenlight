package de.mallorn.blinkenlight;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Consumer;

public class WindowsPerformance {
	
	private Process typePerfProcess;
	private Scanner scanner;
	
	public synchronized void startCounter(int interval) throws IOException {
		File typePerfExe = new File(System.getenv("SystemRoot"), "system32" + File.separator + "typeperf.exe");
		if (!typePerfExe.canExecute()) {
			throw new IOException(typePerfExe.getAbsolutePath() + ": cannot execute");
		}
		
		ProcessBuilder pb = new ProcessBuilder(typePerfExe.getAbsolutePath(), 
				"-si", Integer.toString(interval),
				"\\ProzessorInformationen(_Total)\\Prozessorzeit (%)",
				"\\ProzessorInformationen(*)\\Prozessorfrequenz");
		pb.redirectErrorStream(true);
		
		Process newProcess = typePerfProcess = pb.start();
		newProcess.getOutputStream().close();
		scanner = new Scanner(newProcess.getInputStream(), "ISO-8859-1");
		
		new Thread() {
			public void run() {
				List<String> header = null;
				while (scanner.hasNextLine() && (header == null || header.isEmpty())) {
					header = CSV.parseLine(scanner.nextLine());
				}
				
				if (header != null && !header.isEmpty()) {
					while (scanner.hasNextLine()) {
						List<String> line = CSV.parseLine(scanner.nextLine());
						
						Iterator<String> hi = header.iterator();
						Iterator<String> li = line.iterator();
						
						Map<String,String> values = new HashMap<>();
						while (hi.hasNext() && li.hasNext()) {
							values.put(hi.next(), li.next());
						}
						
						notifyListener(l -> l.onCounterChanged(values));
					}
				}
			}
		}.start();
		
		typePerfProcess = newProcess;
	}
	
	public synchronized void stopCounter() {
		if (typePerfProcess != null) {
			if (typePerfProcess.isAlive()) {
				typePerfProcess.destroy();
			}
			typePerfProcess = null;
		}
		
		if (scanner != null) {
			scanner.close();
			scanner = null;
		}
	}
	
	private Set<Listener> listeners = Collections.synchronizedSet(new HashSet<>());
	
	public void addListener(Listener l) {
		listeners.add(l);
	}
	
	public void removeListener(Listener l) {
		listeners.remove(l);
	}

	protected void notifyListener(Consumer<Listener> f) {
		synchronized (listeners) {
			for (Listener l: listeners) {
				try {
					f.accept(l);
				} catch (Throwable t) {}
			}
		}
	}
	
	public interface Listener {
		public void onCounterChanged(Map<String,String> values);
	}
	
}
