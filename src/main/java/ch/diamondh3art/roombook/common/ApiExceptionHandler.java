package ch.diamondh3art.roombook.common;

import ch.diamondh3art.roombook.common.user.UserService;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

// Maps errors to RFC 9457 ProblemDetail; ResponseStatusException and validation errors are handled by the base class
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // readable messages for known DB rules, other violations fall back to the constraint name
    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.of(
            "ck_location_no_cycle", "Location cannot be moved below itself or one of its descendants",
            "uq_room_location_id_name", "A room with this name already exists in this location",
            "ex_booking_room_id", "Room is already booked in this period");

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex instanceof MissingRequestHeaderException m && UserService.USER_HEADER.equals(m.getHeaderName())) {
            ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                    "Header " + UserService.USER_HEADER + " is missing");
            return handleExceptionInternal(ex, body, headers, HttpStatus.UNAUTHORIZED, request);
        }
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    @ExceptionHandler
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        String sqlState = null;
        String constraint = null;
        // read from the server error, Hibernate's name extraction misses constraints raised by triggers
        if (ex.getMostSpecificCause() instanceof PSQLException p) {
            sqlState = p.getSQLState();
            constraint = p.getServerErrorMessage() == null ? null : p.getServerErrorMessage().getConstraint();
        }
        // 23514 check, 23502 not null → invalid input; 23505 unique, 23P01 exclusion, 23503 FK → conflict with stored data
        HttpStatus status = "23514".equals(sqlState) || "23502".equals(sqlState)
                ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        String detail = constraint == null
                ? "Data integrity violation"
                : CONSTRAINT_MESSAGES.getOrDefault(constraint, "Constraint violated: " + constraint);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("constraint", constraint);
        return problem;
    }

    @ExceptionHandler
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Resource was modified concurrently, reload and retry");
    }
}
