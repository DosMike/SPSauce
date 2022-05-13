package com.dosmike.valvekv;

import java.io.BufferedInputStream;
import java.io.IOException;

class KVTokenStream {

	BufferedInputStream reader;

	public KVTokenStream(BufferedInputStream bis) {
		this.reader = bis;
	}

	@FunctionalInterface
	public interface PredicateIO<T> {
		boolean test(T a) throws IOException;
	}

	/** @see BufferedInputStream#skip(long)  */
	public long skip(long n) throws IOException {
		return reader.skip(n);
	}

	public void skipComments() throws IOException {
		skipSpaces();
		while ("//".equals(peek(2))) {
			readUntil((s)->peek()=='\n');
			skipSpaces();
		}
	}
	/**
	 * @param escapes parse \escape sequences, default for valve is false, although that might break kvs containing quotes
	 * @return empty if EOF
	 */
	public String readToken(boolean escapes) throws IOException {
		skipComments();
		String token;
		if (peek() == '"') {
			reader.skip(1);
			// s.length == 0 checks if the quotes immediately close again, otherwise s.charAt(-1)!='\' makes sure the quote is not escaped
			token = readUntil((s)-> peek()=='"' && (!escapes || (s.length() == 0 || s.charAt(s.length()-1)!='\\')));
			reader.skip(1);
		} else {
			token = readWhile((s)->!Character.isWhitespace(peek()));
		}
		if (escapes)
			return token.replace("\\t", "\t")
				.replace("\\n", "\n")
				.replace("\\\\", "\\")
				.replace("\\\"", "\"");
		else return token;
	}

	public String peek(int amount) throws IOException {
		byte[] buf = new byte[amount];
		reader.mark(amount);
		int read = reader.read(buf);
		if (read < 1) return "";
		reader.reset();
		return new String(buf);
	}
	public byte[] peekRaw(int amount) throws IOException {
		byte[] buf = new byte[amount];
		reader.mark(amount);
		int read = reader.read(buf);
		if (read < 1) return new byte[0];
		reader.reset();
		return buf;
	}
	public int peek() throws IOException {
		int c;
		reader.mark(1);
		c = reader.read();
		reader.reset();
		return c;
	}
	public byte[] read(int amount) throws IOException {
		byte[] buf = new byte[amount];
		int read = reader.read(buf);
		if (read < 1) return new byte[0];
		return buf;
	}
	public int read() throws IOException {
		return reader.read();
	}
	public void skipSpaces() throws IOException {
		reader.mark(8);
		int skipped = 0;
		while (true) {
			int c = reader.read();
			if (c == -1) {
				reader.reset();
				reader.skip(skipped);
				break;
			}
			if (Character.isWhitespace(c)) {
				if (++skipped == 8) {
					skipped = 0;
					reader.reset();
					reader.skip(8);
					reader.mark(8);
				}
			} else {
				reader.reset();
				reader.skip(skipped);
				break;
			}
		}
	}
	public String readWhile(PredicateIO<StringBuilder> condition) throws IOException {
		StringBuilder sb = new StringBuilder();
		int c;
		while ((c=peek()) >= 0 && condition.test(sb)) {
			sb.append((char)(reader.read()));
		}
		return sb.toString();
	}
	/** @return excluding the first character that failed the condition */
	public String readUntil(PredicateIO<StringBuilder> condition) throws IOException {
		StringBuilder sb = new StringBuilder();
		int c;
		while ((c=peek()) >= 0 && !condition.test(sb)) {
			sb.append((char)(reader.read()));
		}
		return sb.toString();
	}

	public void close() throws IOException {
		reader.close();
	}

}
