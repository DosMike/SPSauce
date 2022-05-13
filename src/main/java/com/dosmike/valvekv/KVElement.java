package com.dosmike.valvekv;

import java.awt.*;

public interface KVElement {

	default int asInt() {
		return asPrimitive().asInt();
	}
	default float asFloat() {
		return asPrimitive().asFloat();
	}
	default String asString() {
		return asPrimitive().asString();
	}
	default boolean asBool() {
		return asPrimitive().asBool();
	}
	default KVVector asVector() {
		return asPrimitive().asVector();
	}
	default Color asColor() {
		return asPrimitive().asColor();
	}
	KVPrimitive asPrimitive();
	KVObject asObject();
	KVArray asArray();

}
