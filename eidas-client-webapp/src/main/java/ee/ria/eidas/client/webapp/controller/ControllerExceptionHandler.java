package ee.ria.eidas.client.webapp.controller;

import ee.ria.eidas.client.exception.AuthenticationFailedException;
import ee.ria.eidas.client.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@ControllerAdvice
public class ControllerExceptionHandler {

    @ExceptionHandler({InvalidRequestException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map handleBadRequest(Exception exception) {
        log.error("Bad request!", exception);

        if (exception instanceof MethodArgumentTypeMismatchException) {
            String name = ((MethodArgumentTypeMismatchException) exception).getName();
            return getMap(HttpStatus.BAD_REQUEST, "Invalid value for parameter " + name);
        } else {
            return getMap(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @ExceptionHandler
    @ResponseBody
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map handleAuthenticationFailure(AuthenticationFailedException exception) {
        log.error("Authentication failed!", exception);
        if (exception.getSubStatus() != null)
            return getMap(HttpStatus.UNAUTHORIZED, exception.getMessage(), exception.getStatus(), exception.getSubStatus());
        else
            return getMap(HttpStatus.UNAUTHORIZED, exception.getMessage(), exception.getStatus());
    }

    @ExceptionHandler
    @ResponseBody
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Map handleAuthenticationFailure(HttpRequestMethodNotSupportedException exception) {
        log.error("Method not allowed!", exception);
        return getMap(HttpStatus.METHOD_NOT_ALLOWED, exception.getMessage());
    }

    @ExceptionHandler
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map handleInternalError(Throwable exception) {
        log.error("Internal server error!", exception);
        return getMap(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong internally. Please consult server logs for further details.");
    }

    private Map<String, String> getMap(HttpStatus badRequest, String message) {
        return Collections.unmodifiableMap(Stream.of(
                new SimpleEntry<>("message", message),
                new SimpleEntry<>("error", badRequest.getReasonPhrase()))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue)));
    }

    private Map<String, String> getMap(HttpStatus badRequest, String message, String status) {
        return Collections.unmodifiableMap(Stream.of(
                new SimpleEntry<>("message", message),
                new SimpleEntry<>("error", badRequest.getReasonPhrase()),
                new SimpleEntry<>("status", status))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue)));
    }

    private Map<String, String> getMap(HttpStatus badRequest, String message, String status, String subStatus) {
        return Collections.unmodifiableMap(Stream.of(
                new SimpleEntry<>("message", message),
                new SimpleEntry<>("error", badRequest.getReasonPhrase()),
                new SimpleEntry<>("status", status),
                new SimpleEntry<>("subStatus", subStatus))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue)));
    }
}
