package net.ladenthin.bitcoinaddressfinder;

public class InvalidWorkSizeException extends Exception {

	private static final long serialVersionUID = 1716547986284729020L;

	public InvalidWorkSizeException(String msg) {
		super(msg);
	}
}