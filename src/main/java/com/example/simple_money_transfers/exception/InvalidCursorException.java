package com.example.simple_money_transfers.exception;

public class InvalidCursorException extends RuntimeException {

	public InvalidCursorException(String cursor) {
		super("Malformed cursor: " + cursor);
	}

}
