package com.solvelify.routeplanner.api;

import com.solvelify.routeplanner.planning.UnroutablePlaceException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns validation failures into RFC 9457 Problem Details that name the offending fields.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} rather than writing a plain
 * {@code @ExceptionHandler} method is deliberate. Spring Boot registers its own problem details
 * advice at a higher precedence, so a plain advice is quietly ignored and every bad request comes
 * back with "Invalid request content." and no field names. Boot's own handler steps aside as soon
 * as the application defines one of these.
 */
@RestControllerAdvice
class ValidationExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                // Sorted, so the same bad request always produces the same response.
                .sorted(Comparator.comparing(error -> error.get("field")))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(pathOf(request)));
        problem.setProperty("errors", errors);

        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /**
     * A place with no road near it is not a broken request and not a broken server, so neither
     * 400 nor 500 fits. 422 says the request was understood but cannot be carried out.
     */
    @ExceptionHandler(UnroutablePlaceException.class)
    ProblemDetail handleUnroutablePlace(UnroutablePlaceException exception, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setTitle("Unroutable place");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("place", exception.placeName());
        return problem;
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : request.getDescription(false);
    }
}
