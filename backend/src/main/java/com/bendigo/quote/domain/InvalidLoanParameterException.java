package com.bendigo.quote.domain;

/** Raised when a loan parameter passes transport validation but is rejected by the domain. */
public class InvalidLoanParameterException extends RuntimeException {

    public InvalidLoanParameterException(String message) {
        super(message);
    }
}
