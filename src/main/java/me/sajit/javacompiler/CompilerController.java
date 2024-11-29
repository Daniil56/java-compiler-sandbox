package me.sajit.javacompiler;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/compile")
public class CompilerController {

    private final CompilerService compilerService;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final int REQUEST_LIMIT = 30;
    private static final Duration TIME_FRAME = Duration.ofSeconds(30);

    public CompilerController(CompilerService compilerService) {
        this.compilerService = compilerService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> compileCode(@RequestBody Map<String, String> request,
                                                           @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = "UNKNOWN";
        }

        if (!isRequestAllowed(ipAddress)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many requests. Please try again later."));
        }
        String javaCode = request.get("code");
        String inputStr = request.get("input");
        return compilerService.compile(javaCode, inputStr);
    }

    private boolean isRequestAllowed(String ipAddress) {
        Bucket bucket = buckets.computeIfAbsent(ipAddress, this::createNewBucket);
        return bucket.tryConsume(1);
    }

    private Bucket createNewBucket(String ipAddress) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(REQUEST_LIMIT, Refill.greedy(REQUEST_LIMIT, TIME_FRAME)))
                .build();
    }
}