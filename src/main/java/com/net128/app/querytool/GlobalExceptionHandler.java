package com.net128.app.querytool;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<String> handleSQLException(SQLException ex) {
        var message = ex.getMessage().replace("[select", "[\nselect");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(message);
    }
}