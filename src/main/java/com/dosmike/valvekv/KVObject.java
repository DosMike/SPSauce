package com.dosmike.valvekv;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class KVObject implements KVElement {

	private List<String> keys = new LinkedList<>();
	private List<KVElement> values = new LinkedList<>();
	private void setInternal(String key, KVElement value) {
		int at = keys.indexOf(key);
		if (at < 0) {
			keys.add(key);
			values.add(value);
		} else {
			values.set(at, value);
		}
	}

	/** assumes key is unique and replaces if already existing */
	public void set(@NotNull String key, int value) {
		setInternal(key, new KVPrimitive(String.valueOf(value)));
	}
	/** assumes key is unique and replaces if already existing */
	public void set(@NotNull String key, float value) {
		setInternal(key, new KVPrimitive(String.valueOf(value)));
	}
	/** assumes key is unique and replaces if already existing */
	public void set(@NotNull String key, @NotNull String value) {
		setInternal(key, new KVPrimitive(value));
	}
	/** assumes key is unique and replaces if already existing */
	public void set(@NotNull String key, boolean value) {
		setInternal(key, new KVPrimitive(value ? "1" : "0"));
	}
	/** assumes key is unique and replaces if already existing */
	public void set(@NotNull String key, @NotNull KVVector value) {
		setInternal(key, new KVPrimitive(value.toString()));
	}
	/** assumes key is unique and replaces if already existing */
	public void set(@NotNull String key, @NotNull Color value) {
		String color = value.getRed() + " " + value.getGreen() + " " + value.getBlue();
		setInternal(key, new KVPrimitive(color));
	}
	/** assumes key is unique and replaces if already existing */
	public void set(@NotNull String key, @NotNull KVObject object) {
		setInternal(key, object);
	}
	/** adds the property to this object unchecked. this means that keys can duplicate */
	public void pushInternal(@NotNull String key, @NotNull KVElement object) {
		keys.add(key);
		values.add(object);
	}
	/** adds the property to this object unchecked. this means that keys can duplicate */
	public void push(@NotNull String key, int value) {
		pushInternal(key, new KVPrimitive(String.valueOf(value)));
	}
	/** adds the property to this object unchecked. this means that keys can duplicate */
	public void push(@NotNull String key, float value) {
		pushInternal(key, new KVPrimitive(String.valueOf(value)));
	}
	/** adds the property to this object unchecked. this means that keys can duplicate */
	public void push(@NotNull String key, @NotNull String value) {
		pushInternal(key, new KVPrimitive(value));
	}
	/** adds the property to this object unchecked. this means that keys can duplicate */
	public void push(@NotNull String key, boolean value) {
		pushInternal(key, new KVPrimitive(value ? "1" : "0"));
	}
	/** adds the property to this object unchecked. this means that keys can duplicate */
	public void push(@NotNull String key, @NotNull KVVector value) {
		pushInternal(key, new KVPrimitive(value.toString()));
	}
	/** adds the property to this object unchecked. this means that keys can duplicate */
	public void push(@NotNull String key, @NotNull Color value) {
		String color = value.getRed() + " " + value.getGreen() + " " + value.getBlue();
		pushInternal(key, new KVPrimitive(color));
	}
	/** adds the property to this object unchecked. this means that keys can duplicate */
	public void push(@NotNull String key, @NotNull KVObject object) {
		pushInternal(key, object);
	}

	public boolean contains(@NotNull String key) {
		return keys.contains(key);
	}
	public int count(@NotNull String key) {
		int at = -1;
		int count = 0;
		while ((at = findNextOffset(key, at))>=0) count++;
		return count;
	}
	public int count() {
		return keys.size();
	}
	/** assumes the key is unique and returns the first result */
	public KVElement get(@NotNull String key) {
		int at = keys.indexOf(key);
		return at < 0 ? null : values.get(at);
	}
	/** find the first offset with the given key */
	public int findOffset(@NotNull String key) {
		return findNextOffset(key, -1);
	}
	/** find the next offset with the given key, starting AFTER the specified offset */
	public int findNextOffset(@NotNull String key, int first) {
		int at = -1;
		for (int i=first+1;i<keys.size();i++) {
			if (keys.get(i).equals(key)) {
				at = i;
				break;
			}
		}
		return at;
	}

	/**
	 * Collect all values for the specified key recursively to a certain depth.
	 * This means if maxDepth > 0 and a value is a VPObject, the key will be searched
	 * within the child using findAll(needle, maxDepth-1).
	 * @param needle key to collect
	 * @param maxDepth number of levels to traverse
	 * @return collection of results
	 * @see #findAll(String, int, Predicate)
	 */
	public KVArray findAll(@NotNull String needle, int maxDepth) {
		KVArray accu = new KVArray();
		for (int i=0;i<keys.size(); i++) {
			if (keys.get(i).equals(needle)) {
				accu.add(values.get(i));
			} else if (maxDepth > 0 && values.get(i) instanceof KVObject) {
				accu.addAll(findAll(needle, maxDepth-1));
			}
		}
		return accu;
	}
	/**
	 * Collect all values for the specified key recursively to a certain depth.
	 * This means if maxDepth > 0 and a value is a VPObject, the key will be searched
	 * within the child using findAll(needle, maxDepth-1).
	 * @param needle key to collect
	 * @param maxDepth number of levels to traverse
	 * @param classFilter only children stored on keys with these classnames will be traversed
	 * @return collection of results
	 * @see #findAll(String, int)
	 */
	public KVArray findAll(@NotNull String needle, int maxDepth, Predicate<String> classFilter) {
		KVArray accu = new KVArray();
		for (int i=0;i<keys.size(); i++) {
			if (keys.get(i).equals(needle)) {
				accu.add(values.get(i));
			} else if (maxDepth > 0 && values.get(i) instanceof KVObject && classFilter.test(keys.get(i))) {
				accu.addAll(((KVObject)values.get(i)).findAll(needle, maxDepth-1));
			}
		}
		return accu;
	}

	/** return an element at the specified offset */
	public KVElement at(int index) {
		return values.get(index);
	}
	/** assumes the key is unique and returns the first result */
	public int getAsInt(@NotNull String key) {
		KVElement element = get(key);
		if (element == null) throw new IllegalArgumentException("Key '"+key+"' was not set");
		return element.asInt();
	}
	/** assumes the key is unique and returns the first result. if key was not found, one will be inserted with the specified default */
	public int getAsInt(@NotNull String key, int defaultValue) {
		KVElement element = get(key);
		if (element == null) {
			set(key, defaultValue);
			return defaultValue;
		} else return element.asInt();
	}
	/** assumes the key is unique and returns the first result */
	public float getAsFloat(@NotNull String key) {
		KVElement element = get(key);
		if (element == null) throw new IllegalArgumentException("Key '"+key+"' was not set");
		return element.asFloat();
	}
	/** assumes the key is unique and returns the first result. if key was not found, one will be inserted with the specified default */
	public float getAsFloat(@NotNull String key, float defaultValue) {
		KVElement element = get(key);
		if (element == null) {
			set(key, defaultValue);
			return defaultValue;
		} else return element.asFloat();
	}
	/** assumes the key is unique and returns the first result */
	public String getAsString(@NotNull String key) {
		KVElement element = get(key);
		if (element == null) throw new IllegalArgumentException("Key '"+key+"' was not set");
		return element.asString();
	}
	/** assumes the key is unique and returns the first result. if key was not found, one will be inserted with the specified default */
	public String getAsString(@NotNull String key, @NotNull String defaultValue) {
		KVElement element = get(key);
		if (element == null) {
			set(key, defaultValue);
			return defaultValue;
		} else return element.asString();
	}
	/** assumes the key is unique and returns the first result */
	public boolean getAsBool(@NotNull String key) {
		KVElement element = get(key);
		if (element == null) throw new IllegalArgumentException("Key '"+key+"' was not set");
		return element.asBool();
	}
	/** assumes the key is unique and returns the first result. if key was not found, one will be inserted with the specified default */
	public boolean getAsBool(@NotNull String key, boolean defaultValue) {
		KVElement element = get(key);
		if (element == null) {
			set(key, defaultValue);
			return defaultValue;
		} else return element.asBool();
	}
	/** assumes the key is unique and returns the first result */
	public KVVector getAsVector(@NotNull String key) {
		KVElement element = get(key);
		if (element == null) throw new IllegalArgumentException("Key '"+key+"' was not set");
		return element.asVector();
	}
	/** assumes the key is unique and returns the first result. if key was not found, one will be inserted with the specified default */
	public KVVector getAsVector(@NotNull String key, @NotNull KVVector defaultValue) {
		KVElement element = get(key);
		if (element == null) {
			set(key, defaultValue);
			return defaultValue;
		} else return element.asVector();
	}
	/** assumes the key is unique and returns the first result */
	public Color getAsColor(@NotNull String key) {
		KVElement element = get(key);
		if (element == null) throw new IllegalArgumentException("Key '"+key+"' was not set");
		return element.asColor();
	}
	/** assumes the key is unique and returns the first result. if key was not found, one will be inserted with the specified default */
	public Color getAsColor(@NotNull String key, @NotNull Color defaultValue) {
		KVElement element = get(key);
		if (element == null) {
			set(key, defaultValue);
			return defaultValue;
		} else return element.asColor();
	}
	/** assumes the key is unique and returns the first result */
	public KVPrimitive getAsPrimitive(@NotNull String key) {
		KVElement element = get(key);
		if (element == null) throw new IllegalArgumentException("Key '"+key+"' was not set");
		return element.asPrimitive();
	}
	/** assumes the key is unique and returns the first result. if key was not found, one will be inserted with the specified default */
	public KVPrimitive getAsPrimitive(@NotNull String key, @NotNull KVPrimitive defaultValue) {
		KVElement element = get(key);
		if (element == null) {
			setInternal(key, defaultValue);
			return defaultValue;
		} else return element.asPrimitive();
	}
	/** assumes the key is unique and returns the first result */
	public KVObject getAsObject(@NotNull String key) {
		KVElement element = get(key);
		if (element == null) throw new IllegalArgumentException("Key '"+key+"' was not set");
		return element.asObject();
	}
	/** assumes the key is unique and returns the first result. if key was not found, one will be inserted with the specified default */
	public KVObject getAsObject(@NotNull String key, @NotNull KVObject defaultValue) {
		KVElement element = get(key);
		if (element == null) {
			set(key, defaultValue);
			return defaultValue;
		} else return element.asObject();
	}
	/**
	 * As KeyValues do not know array types, this is equal to getAll, but throws if no entries were found.
	 * @see #getAll(String) */
	public KVArray getAsArray(@NotNull String key) {
		KVArray array = getAll(key);
		if (array.isEmpty()) throw new IllegalArgumentException("Key '"+key+"' was not set");
		return array;
	}
	/** collects all entries with this key and returns it as list.
	 * Changes in the list through add, remove, set are not reflected in this object, but changes in values are.
	 */
	public KVArray getAll(@NotNull String key) {
		KVArray array = new KVArray();
		for (int i=0;i<keys.size();i++) {
			if (keys.get(i).equals(key)) array.add(values.get(i));
		}
		return array;
	}

	/**
	 * Changes in this collection do not reflect in the underlying map, though it should honor the order in which
	 * keys were initialy set
	 */
	public List<String> getKeys() {
		return new LinkedList<>(keys);
	}
	public boolean delete(@NotNull String key) {
		int at = keys.indexOf(key);
		if (at >= 0) {
			keys.remove(at);
			values.remove(at);
			return true;
		}
		return false;
	}
	public boolean deleteNth(@NotNull String key, int n) {
		int at = -1;
		for (int i = 0; i < n; i++) {
			if ((at = findNextOffset(key, at)) < 0) break;
		}
		if (at < 0) return false;
		keys.remove(at);
		values.remove(at);
		return true;
	}
	public boolean deleteAll(@NotNull String key) {
		boolean one = false;
		while (delete(key)) {
			one = true;
		}
		return one;
	}
	/** no guarantee for the traversal order */
	public void deleteIf(BiPredicate<String, KVElement> filter) {
		// going from the back means we don't need fix i
		for (int i = keys.size()-1; i >= 0; i--) {
			if (filter.test(keys.get(i), values.get(i))) {
				keys.remove(i);
				values.remove(i);
			}
		}
	}

	@Override
	public KVPrimitive asPrimitive() {
		throw new IllegalStateException("This element is not a primitive");
	}

	@Override
	public KVObject asObject() {
		return this;
	}

	@Override
	public KVArray asArray() {
		return new KVArray(this);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{ ");
		List<String> distinctKeys = keys.stream().distinct().collect(Collectors.toList());
		for (int i = 0; i < distinctKeys.size(); i++) {
			if (i > 0) sb.append(", ");
			sb.append(KeyValueIO.escapeValue(distinctKeys.get(i), true));
			sb.append(": ");
			KVArray values = getAll(distinctKeys.get(i));
			if (values.size() > 1)
				sb.append(values.toString());
			else
				sb.append(values.getFirst().toString());
		}
		sb.append(" }");
		return sb.toString();
	}
}
