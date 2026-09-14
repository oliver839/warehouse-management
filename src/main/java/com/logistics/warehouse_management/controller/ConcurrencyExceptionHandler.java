package com.logistics.warehouse_management.controller;

import jakarta.persistence.OptimisticLockException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ConcurrencyExceptionHandler {

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleOptimisticLockingConflict() {
        return Map.of("error", "Der Datensatz wurde parallel geändert. Bitte laden Sie die Daten neu und versuchen Sie es erneut.");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalState(IllegalStateException exception) {
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Die Aktion konnte nicht ausgeführt werden."
                : exception.getMessage();
        return Map.of("error", message);
    }
}