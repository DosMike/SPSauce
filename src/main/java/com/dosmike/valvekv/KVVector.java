package com.dosmike.valvekv;

import org.jetbrains.annotations.NotNull;

/** vectors in kv files are used for multiple purposes: positions, angles, colors... */
public class KVVector {

	private float x,y,z;
	public KVVector(float x, float y, float z) {
		this.x=x;
		this.y=y;
		this.z=z;
	}
	public KVVector(double x, double y, double z) {
		this.x=(float)x;
		this.y=(float)y;
		this.z=(float)z;
	}
	public KVVector(float[] data) {
		assert data.length == 3;
		this.x = data[0];
		this.y = data[1];
		this.z = data[2];
	}

	public float getX() {
		return x;
	}

	public void setX(float x) {
		this.x = x;
	}

	public float getY() {
		return y;
	}

	public void setY(float y) {
		this.y = y;
	}

	public float getZ() {
		return z;
	}

	public void setZ(float z) {
		this.z = z;
	}

	public KVVector subtracted(@NotNull KVVector other) {
		return new KVVector(x-other.x, y-other.y, z-other.z);
	}
	public KVVector added(@NotNull KVVector other) {
		return new KVVector(x+other.x, y+other.y, z+other.z);
	}
	public KVVector scaled(double scale) {
		return new KVVector(x*scale, y*scale, z*scale);
	}
	public double length() {
		return Math.sqrt(x*x+y*y+z*z);
	}
	public float dot(@NotNull KVVector other) {
		return x*other.x + y*other.y + z*other.z;
	}
	public KVVector crossed(@NotNull KVVector other) {
		return new KVVector(
				y*other.z - z*other.y,
				z*other.x - x*other.z,
				x*other.y - y*other.x
		);
	}
	public KVVector normalized() {
		return scaled(1/length());
	}

	public boolean isAxisVector() {
		int c = 0;
		if (x!=0) c++;
		if (y!=0) c++;
		if (z!=0) c++;
		return c==1;
	}

	@Override
	public String toString() {
		return x + " " + y + ' ' + z;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		KVVector vector = (KVVector) o;

		if (Float.compare(vector.x, x) != 0) return false;
		if (Float.compare(vector.y, y) != 0) return false;
		return Float.compare(vector.z, z) == 0;
	}

	@Override
	public int hashCode() {
		int result = (x != +0.0f ? Float.floatToIntBits(x) : 0);
		result = 31 * result + (y != +0.0f ? Float.floatToIntBits(y) : 0);
		result = 31 * result + (z != +0.0f ? Float.floatToIntBits(z) : 0);
		return result;
	}
}
