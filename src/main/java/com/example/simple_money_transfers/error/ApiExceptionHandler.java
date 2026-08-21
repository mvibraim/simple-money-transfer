package com.example.simple_money_transfers.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.simple_money_transfers.money.InvalidMoneyException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	public ProblemDetail handleNotFound(NotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(InvalidMoneyException.class)
	public ProblemDetail handleInvalidMoney(InvalidMoneyException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(BusinessRuleException.class)
	public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		problem.setProperty("errors",
				ex.getFieldErrors()
					.stream()
					.map(error -> error.getField() + ": " + error.getDefaultMessage())
					.toList());
		return problem;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleMalformedBody(HttpMessageNotReadableException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Missing required header: " + ex.getHeaderName());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Malformed value for parameter '" + ex.getName() + "'");
	}

	/**
	 * Pessimistic locking (F09) turns one slow or stuck transaction into held row locks;
	 * without this mapping, pool exhaustion or a lock/ statement timeout under contention
	 * would fall through to the generic 500 handler below and look like an application
	 * bug rather than a temporary capacity limit a client can reasonably retry.
	 */
	@ExceptionHandler({ CannotCreateTransactionException.class, PessimisticLockingFailureException.class,
			QueryTimeoutException.class })
	public ProblemDetail handleTemporarilyUnavailable(Exception ex) {
		log.warn("Temporarily unavailable: {}", ex.getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The service is temporarily unable to process this request; please retry");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
	}

}
