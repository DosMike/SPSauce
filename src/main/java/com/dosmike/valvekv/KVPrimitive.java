package com.dosmike.valvekv;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class KVPrimitive implements KVElement {

	private final String stringRep;

	KVPrimitive(@NotNull String entry) {
		this.stringRep = entry;
	}

	@Override
	public int asInt() {
		return stringRep.isEmpty() ? 0 : Integer.parseInt(stringRep);
	}

	@Override
	public float asFloat() {
		return stringRep.isEmpty() ? 0f : Float.parseFloat(stringRep);
	}

	@Override
	public String asString() {
		return stringRep;
	}

	@Override
	public boolean asBool() {
		return asInt() != 0;
	}

	@Override
	public KVVector asVector() {
		String[] sa = stringRep.split(" ");
		float[] fa = new float[sa.length];
		for (int i=0; i<sa.length; i++) fa[i] = Float.parseFloat(sa[i]);
		return new KVVector(fa);
	}

	@Override
	public Color asColor() {
		String[] sa = stringRep.split(" ");
		int[] ia = new int[sa.length];
		for (int i=0; i<sa.length; i++) ia[i] = Integer.parseInt(sa[i]);
		assert ia.length == 3;
		return new Color(ia[0], ia[1], ia[2]);
	}

	@Override
	public KVPrimitive asPrimitive() {
		return this;
	}

	@Override
	public KVObject asObject() {
		throw new IllegalStateException("This element is a primitive");
	}

	@Override
	public KVArray asArray() {
		return new KVArray(this);
	}

	@Override
	public String toString() {
		return '"'+stringRep+'"';
	}
}
