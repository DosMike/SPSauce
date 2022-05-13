package com.dosmike.valvekv;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;

/**
 * KVArrays represent a key filtered view on a VPObject.<br>
 * Modifications in this list are not reflected in the KVObject and vice versa.<br>
 * Modifications in list values ARE reflected in the parent Object.
 */
public class KVArray extends LinkedList<KVElement> {

	public KVArray() {
		super();
	}

	public KVArray(Collection<? extends KVObject> c) {
		super(c);
	}

	public KVArray(@NotNull KVElement one) {
		super();
		add(one);
	}

	public KVPrimitive asPrimitive() {
		if (size() == 1) return getFirst().asPrimitive();
		throw new IllegalStateException("This view is not a singleton list");
	}

	public KVObject asObject() {
		if (size() == 1) return getFirst().asObject();
		throw new IllegalStateException("This view is not a singleton list");
	}

	public KVArray asArray() {
		return this;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[ ");
		boolean first = true;
		for (KVElement o : this) {
			if (first) first = false;
			else sb.append(", ");
			sb.append(o.toString());
		}
		sb.append(" ]");
		return sb.toString();
	}
}
