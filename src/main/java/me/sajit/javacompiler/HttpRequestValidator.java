package me.sajit.javacompiler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
public class HttpRequestValidator implements CodeValidator {

    private final Pattern httpRequestPattern;

    public HttpRequestValidator(@Value("${validator.http.pattern}") String pattern) {
        this.httpRequestPattern = Pattern.compile(pattern);
    }

    @Override
    public boolean validate(String javaCode) {
        return !httpRequestPattern.matcher(javaCode).find();
    }
} 