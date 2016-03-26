package de.mallorn.blinkenlight;

import java.util.LinkedList;
import java.util.List;

/**
 * This class implements parsing a CSV-formatted line according to RFC 4180.
 */
public class CSV {

	private CSV() {}
	
	private static enum State { ValueStart, Value, QuotedValue, Quote }
	
	public static List<String> parseLine(String line) {
		List<String> values = new LinkedList<>();
		
		State state = State.ValueStart;
		String nextValue = "";
		
		int idx = 0;
		while (idx < line.length()) {
			char c = line.charAt(idx);
			
			switch (state) {
			case ValueStart:
				switch (c) {
				case ',':
					values.add(nextValue);
					nextValue = "";
					state = State.ValueStart;
					break;
				case '"':
					state = State.QuotedValue;
					break;
				default:
					nextValue += String.valueOf(c);
					state = State.Value;
					break;
				}
				break;
			case Value:
				switch (c) {
				case ',':
					values.add(nextValue);
					nextValue = "";
					state = State.ValueStart;
					break;
				default:
					nextValue += String.valueOf(c);
					state = State.Value;
					break;
				}
				break;
			case QuotedValue:
				switch (c) {
				case '"':
					state = State.Quote;
					break;
				default:
					nextValue += String.valueOf(c);
					break;
				}
				break;
			case Quote:
				switch (c) {
				case ',':
					values.add(nextValue);
					nextValue = "";
					state = State.ValueStart;
					break;
				case '"':
					nextValue += "\"";
					state = State.QuotedValue;
					break;
				default:
					nextValue += "\"" + String.valueOf(c);
					state = State.QuotedValue;
					break;
				}
				break;
			}
			idx++;
		}
		
		if (!nextValue.isEmpty()) {
			values.add(nextValue);
		}
		
		return values;
	}
	
}
