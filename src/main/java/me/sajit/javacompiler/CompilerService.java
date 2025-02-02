package me.sajit.javacompiler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.tools.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.sajit.javacompiler.HttpRequestValidator;
import me.sajit.javacompiler.CodeValidator;

@Service
public class CompilerService {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final int MAX_INPUT_COUNT = 100;
    private static final int TIMEOUT = 5; //sec

    private final List<CodeValidator> validators;

    @Autowired
    public CompilerService(HttpRequestValidator httpRequestValidator) {
        this.validators = List.of(httpRequestValidator);
    }

    private static String extractClassName(String sourceCode) {
        Pattern pattern = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
        Matcher matcher = pattern.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Map<String, Object> validateInputArguments(String javaCode, String inputStr) {
        Map<String, Object> result = new HashMap<>();
        result.put("isValid", true);
        int expectedInputCount = countExpectedInputs(javaCode);
        List<Map<String, Object>> errorDetails = new ArrayList<>();

        // If no inputs are expected, return valid
        if (expectedInputCount == 0) {
            return result;
        }

        // If input is empty and inputs are expected
        if (inputStr == null || inputStr.trim().isEmpty()) {
            result.put("isValid", false);
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("message", "Input required: Expected " + expectedInputCount + " input argument(s).");
            errorInfo.put("line", findInputLineNumber(javaCode));
            errorDetails.add(errorInfo);
            result.put("errors", errorDetails);
            return result;
        }

        // Check input count
        String[] inputArgs = inputStr.trim().split("\\s+");

        if (inputArgs.length < expectedInputCount) {
            result.put("isValid", false);
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("message", String.format(
                    "Input mismatch: Expected %d input argument(s), but got %d.",
                    expectedInputCount,
                    inputArgs.length));
            errorInfo.put("line", findInputLineNumber(javaCode));
            errorDetails.add(errorInfo);
            result.put("errors", errorDetails);
        }

        return result;
    }

    private int countExpectedInputs(String javaCode) {
        Pattern scannerPattern = Pattern.compile("new\\s+Scanner\\s*\\(\\s*System\\.in\\s*\\)");
        Pattern nextIntPattern = Pattern.compile("\\.nextInt\\(\\)");
        Pattern nextDoublePattern = Pattern.compile("\\.nextDouble\\(\\)");
        Pattern nextLinePattern = Pattern.compile("\\.nextLine\\(\\)");

        Matcher scannerMatcher = scannerPattern.matcher(javaCode);

        if (!scannerMatcher.find()) {
            return 0;
        }

        scannerMatcher.reset();
        int inputCount = 0;
        Matcher nextIntMatcher = nextIntPattern.matcher(javaCode);
        Matcher nextDoubleMatcher = nextDoublePattern.matcher(javaCode);
        Matcher nextLineMatcher = nextLinePattern.matcher(javaCode);

        while (nextIntMatcher.find()) inputCount++;
        while (nextDoubleMatcher.find()) inputCount++;
        while (nextLineMatcher.find()) inputCount++;

        return inputCount;
    }
    private int findInputLineNumber(String javaCode) {
        String[] lines = javaCode.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("new Scanner(System.in)") ||
                    lines[i].contains(".nextInt()") ||
                    lines[i].contains(".nextDouble()") ||
                    lines[i].contains(".nextLine()")) {
                return i + 1;
            }
        }
        return -1;
    }

    private ResponseEntity<Map<String, Object>> formatMessage(String status, String message) {
        List<Map<String, Object>> errorDetails = new ArrayList<>();
        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("line", 0);
        errorInfo.put("message", message);
        errorDetails.add(errorInfo);
        return ResponseEntity.ok(Map.of(
                "status", status,
                "errors", errorDetails
        ));
    }

    private ResponseEntity<Map<String, Object>> executeCodeWithInput(Path executionDir, String className, String input) {
        try {
            String javaCode = Files.readString(executionDir.resolve(className + ".java"));
            int expectedInputCount = countExpectedInputs(javaCode);

            String[] inputs = input == null ? new String[0] : input.trim().split("\\s+");

            if (inputs.length < expectedInputCount) {
                return formatMessage("error", String.format(
                        "Insufficient input: Expected %d input argument(s), but got %d.",
                        expectedInputCount, inputs.length
                ));
            }
            else if(inputs.length > MAX_INPUT_COUNT){
                return formatMessage("error", String.format(
                        "Too many input: Expected less than %d input argument(s), but got %d.", MAX_INPUT_COUNT, inputs.length));
            }

            ProcessBuilder processBuilder = new ProcessBuilder("java", "-cp", executionDir.toString(), className);
            Process process = processBuilder.start();

            CompletableFuture<Void> inputFuture = CompletableFuture.runAsync(() -> {
                try (PrintWriter processInput = new PrintWriter(process.getOutputStream(), true)) {
                    for (String arg : inputs) {
                        processInput.println(arg);
                        processInput.flush();
                    }
                    processInput.close();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }, EXECUTOR);

            boolean processExited = process.waitFor(TIMEOUT, TimeUnit.SECONDS);

            if (!processExited) {
                process.destroyForcibly();
                return formatMessage("error", "Execution timed out");
            }

            Future<String> outputFuture = EXECUTOR.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                }
            });

            Future<String> errorFuture = EXECUTOR.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                }
            });

            inputFuture.get(TIMEOUT, TimeUnit.SECONDS);
            String output = outputFuture.get(TIMEOUT, TimeUnit.SECONDS);
            String error = errorFuture.get(TIMEOUT, TimeUnit.SECONDS);

            deleteDirectory(executionDir);
            String combinedOutput = (output + error).trim();
            int exitCode = process.exitValue();
            String status = exitCode == 0 ? "success" : "error";
            if (status.equals("error")) {
                return formatMessage(status, combinedOutput.isEmpty() ? "Execution failed" : combinedOutput);
            } else {
                return ResponseEntity.ok(Map.of(
                        "status", status,
                        "message", combinedOutput.isEmpty() ? "No output" : combinedOutput
                ));
            }

        } catch (Exception e) {
            return formatMessage("error", e.getMessage());
        }
    }
    public ResponseEntity<Map<String, Object>> compile(String javaCode, String inputStr) {
        // Проверка с использованием всех валидаторов
        for (CodeValidator validator : validators) {
            if (!validator.validate(javaCode)) {
                return formatMessage("error", "HTTP requests are not allowed in the code.");
            }
        }

        Map<String, Object> validationResult = validateInputArguments(javaCode, inputStr);
        if (!(boolean) validationResult.get("isValid")) {
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "errors", validationResult.get("errors")
            ));
        }

        String className = extractClassName(javaCode);
        try {
            // Create a completely unique directory for each request
            Path requestTempDir = Files.createTempDirectory("java-compiler-request-");
            requestTempDir.toFile().deleteOnExit();

            // Create source file in the unique directory
            Path sourceFile = requestTempDir.resolve(className + ".java");
            Files.writeString(sourceFile, javaCode);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

            List<String> compilationOptions = Arrays.asList("-d", requestTempDir.toString());
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(sourceFile.toFile()));
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, compilationOptions, null, compilationUnits);

            boolean compilationSuccess = task.call();

            if (!compilationSuccess) {
                 List<Map<String, Object>> errorDetails = new ArrayList<>();
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    Map<String, Object> errorInfo = new HashMap<>();

                    long lineNumber = diagnostic.getLineNumber();
                    if (lineNumber > 0) {
                        errorInfo.put("line", (int) lineNumber);
                    }

                    errorInfo.put("message", diagnostic.getMessage(null));
                    errorDetails.add(errorInfo);
                }
                deleteDirectory(requestTempDir);
                return ResponseEntity.ok(Map.of(
                        "status", "error",
                        "errors", errorDetails
                ));
            }
            return executeCodeWithInput(requestTempDir, className, inputStr);
        } catch (Exception e) {
            return formatMessage("error", e.getMessage());
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }


}
