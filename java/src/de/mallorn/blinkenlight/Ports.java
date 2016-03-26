package de.mallorn.blinkenlight;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Ports {

	private Ports() {}
	
	private static final Map<String, PortInfo> portInfos = new HashMap<>();
	
	public static PortInfo getPortInfo(String name) {
		PortInfo pi;
		synchronized (portInfos) {
			pi = portInfos.get(name);
			if (pi == null) {
				pi = new PortInfo(name);
				portInfos.put(name, pi);
			}
			return pi;
		}
	}
	
	public static Set<PortInfo> getPortInfos() {
		Set<PortInfo> pis = new HashSet<>();
		synchronized (portInfos) {
			pis.addAll(portInfos.values());
		}
		return pis;	
	}
	
}
