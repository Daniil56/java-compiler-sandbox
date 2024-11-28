package me.sajit.examhub.javacompiler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/compile")
public class CompilerController {

    private final CompilerService compilerService;

    public CompilerController(CompilerService compilerService) {
        this.compilerService = compilerService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> compileCode(@RequestBody Map<String, String> request) {
        String javaCode = request.get("code");
        String inputStr = request.get("input");
        return compilerService.compile(javaCode, inputStr);
    }
}