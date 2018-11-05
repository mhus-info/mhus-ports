package com.fasteasytrade.jrandtest.crypta;

import com.fasteasytrade.jrandtest.io.RandomStream;

import de.mhus.lib.core.MApi;
import de.mhus.lib.core.crypt.MRandom;

public class MRandomStream implements RandomStream {

	private MRandom rnd;
	private int size;

	public MRandomStream(int size) {
		rnd = MApi.lookup(MRandom.class);
		this.size = size;
	}
	
	@Override
	public boolean isOpen() {
		return size > 0;
	}

	@Override
	public void setFilename(String s) {
	}

	@Override
	public String getFilename() {
		return null;
	}

	@Override
	public boolean openInputStream() {
		return true;
	}

	@Override
	public boolean closeInputStream() {
		return true;
	}

	@Override
	public byte readByte() {
		size--;
		return rnd.getByte();
	}

	@Override
	public int readInt() {
		size--;
		return rnd.getInt();
	}

	@Override
	public long readLong() {
		size--;
		return rnd.getLong();
	}

	@Override
	public double read32BitsAsDouble() {
		size--;
		return rnd.getDouble();
	}

}
