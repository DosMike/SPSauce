package com.dosmike.valvekv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class KeyValueIO {

	private static boolean parse_doEscapes = false;
	/** Escape sequences are not parsed by default in vales KV parser, so it is disabled by default here as well.
	 * Be aware that this will cause values with quotes breaking the format, making files potentially unreadable. */
	public static void EnableEscapeSequenceParsing(boolean value) {
		parse_doEscapes = value;
	}
	private static class RecursionShortcutExceptionWrapper extends Exception {
		private RecursionShortcutExceptionWrapper(String message, Throwable cause) {
			super(message, cause);
		}
	}

	public static void write(OutputStreamWriter writer, KVObject rootObject) throws IOException {
		try {
			writeObjectIndent(new BufferedWriter(writer), rootObject, "", "");
		} catch (RecursionShortcutExceptionWrapper e) {
			throw new IOException(e.getMessage(), e.getCause());
		}
	}
	private static void writeObjectIndent(BufferedWriter writer, KVObject object, String indent, String sourceStack) throws IOException, RecursionShortcutExceptionWrapper {
		List<String> keys = object.getKeys();
		String stack, key;
		for (int i = 0; i < object.count(); i++) {
			key = keys.get(i);
			stack = sourceStack + ">>" + key + "(#" + i + ')';
			try {
				KVElement element = object.at(i);

				if (element instanceof KVPrimitive) {
					writer.write(String.format("%s%s %s%n", indent, escapeValue(key, true), escapeValue(element.asString(), true)));
				} else {
					writer.write(String.format("%s%s%n%s{%n", indent, escapeValue(key, false), indent));
					writeObjectIndent(writer, element.asObject(), indent+"\t", stack);
					writer.write(String.format("%s}%n", indent));
				}
				writer.flush();
			} catch (IOException e) {
				throw new RecursionShortcutExceptionWrapper("Failed to write entry "+stack, e);
			}
		}
	}
	public static String escapeValue(String key, boolean alwaysQuote) {
		if (key.isEmpty()) return "\"\"";
		else if (key.matches("^[a-zA-Z_]+$")) {
			if (alwaysQuote)
				return '"' + key + '"';
			else
				return key;
		}
		else if (parse_doEscapes) {
			return '"' + key.replace("\\", "\\\\")
					.replace("\r", "\\r")
					.replace("\n", "\\n")
					.replace("\t", "\\t")
					.replace("\"", "\\\"") + '"';
		} else
			return '"' + key + '"';
	}

	public static KVObject read(BufferedInputStream reader) throws IOException {
		KVObject root = new KVObject();
		try {
			readWith(new KVTokenStream(reader), root, "");
		} catch (RecursionShortcutExceptionWrapper e) {
			throw new IOException(e.getMessage(), e.getCause());
		}
		return root;
	}
	private static void readWith(KVTokenStream reader, KVObject object, String sourceStack) throws IOException, RecursionShortcutExceptionWrapper {
		try {
			String key;
			while (!(key = reader.readToken(parse_doEscapes)).isEmpty() && key.charAt(0) != '}') {
				reader.skipComments();
				if (reader.peek() == '{') {
					reader.skip(1);
					KVObject child = new KVObject();
					readWith(reader, child, sourceStack.isEmpty() ? key : sourceStack+">>"+key);
					object.push(key, child);
				} else {
					object.push(key, reader.readToken(parse_doEscapes));
				}
				reader.skipComments();
				if (reader.peek() == '}') {
					reader.skip(1);
					break;
				}
			}
		} catch (IOException e) {
			throw new RecursionShortcutExceptionWrapper("Failed to parse: "+sourceStack, e);
		}
	}

	public static KVObject loadFrom(String keyvaluestring) throws IOException {
		try (BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(keyvaluestring.getBytes(StandardCharsets.UTF_8)))) {
			return read(bis);
		}
	}
	public static KVObject loadFrom(File file) throws IOException {
		return loadFrom(file.toPath());
	}
	public static KVObject loadFrom(Path path) throws IOException {
		try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(path))) {
			return read(bis);
		}
	}

	public static String stringify(KVObject element) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
		try (OutputStreamWriter osw = new OutputStreamWriter(baos)) {
			write(osw, element);
		}
		return baos.toString(StandardCharsets.UTF_8.name());
	}
	public static void saveTo(File file, KVObject element) throws IOException {
		saveTo(file.toPath(), element);
	}
	public static void saveTo(Path path, KVObject element) throws IOException {
		try (OutputStreamWriter osw = new OutputStreamWriter(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
			write(osw, element);
		}
	}

}
