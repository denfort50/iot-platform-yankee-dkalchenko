package ru.dkalchenko.orchestrator.adapter.inbound.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;
import ru.dkalchenko.orchestrator.application.exception.DeviceNotFoundException;
import ru.dkalchenko.orchestrator.application.exception.RouterNotFoundException;
import ru.dkalchenko.orchestrator.application.exception.UpstreamConflictException;

@Slf4j
@RestControllerAdvice
public class OrchestratorExceptionHandler {

	@ExceptionHandler(UpstreamConflictException.class)
	public Mono<ResponseEntity<ProblemDetail>> upstreamConflict(UpstreamConflictException ex) {
		log.warn("Upstream conflict mapped to 409: {}", ex.getMessage());
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(pd));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public Mono<ResponseEntity<ProblemDetail>> badRequest(IllegalArgumentException ex) {
		log.warn("Bad request mapped to 400: {}", ex.getMessage());
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		return Mono.just(ResponseEntity.badRequest().body(pd));
	}

	@ExceptionHandler({ DeviceNotFoundException.class, RouterNotFoundException.class })
	public Mono<ResponseEntity<ProblemDetail>> notFound(RuntimeException ex) {
		log.info("Not found mapped to 404: {}", ex.getMessage());
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd));
	}
}
