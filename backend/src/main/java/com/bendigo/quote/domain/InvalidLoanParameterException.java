package com.bendigo.quote.domain;

/**
 * Domain exception thrown when a loan quotation request contains invalid parameters that violate
 * business rules, such as an unknown or unsupported risk band tier.
 */
public class InvalidLoanParameterException extends RuntimeException {

  public InvalidLoanParameterException(String message) {
    super(message);
  }

  public InvalidLoanParameterException(String message, Throwable cause) {
    super(message, cause);
  }
}
