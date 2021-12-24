package com.gauravsalvi.kubernetes.logs.downloader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.Value;

@Value
public class KubeDeploymentLogManager {

    private static final String GET_CONTEXT_COMMAND = "kubectl config view --minify --output 'jsonpath={..context.cluster}'";

    private static final String GET_POD_COMMAND = "kubectl get pods -l name=%s";

    private static final String LOG_POD_COMMAND = "kubectl logs %s -f";

    private static final Runtime RUNTIME = Runtime.getRuntime();

    private static final String PROCESS_DIR = "/Users/gauravsalvi/Desktop/logs";

    private static final String POD_REGEX = "(%s[-\\w]*)";

    String deploymentName;

    String outputDir;

    public void startStreaming() {
        List<String> pods = getPods(deploymentName);
        var futures = pods.stream()
            .map(line -> this.extractPodName(line, deploymentName))
            .flatMap(Optional::stream)
            .map(pod -> createLoggingTask(deploymentName, createOutputDir(outputDir), pod))
            .flatMap(Optional::stream)
            .map(CompletableFuture::runAsync)
            .toList();

        var combinedFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));

        while (!combinedFuture.isDone()) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @SneakyThrows
    private String createOutputDir(String outputDir) {
        var format = outputDir + "/%s/%s";
        var process = execCommand(GET_CONTEXT_COMMAND);
        var outputDestination = process.map(value -> {
                var scanner = new Scanner(value.getInputStream()).useDelimiter("\\n");
                return scanner.hasNext() ? scanner.next() : "";
            })
            .map(context -> extract("k8s(\\w*).*", context, 1))
            .map(environment -> format.formatted(environment.orElse("unknown"), LocalDate.now()))
            .orElse(format.formatted("unknown", LocalDate.now()));
        //Files.createDirectories(Path.of(outputDestination));
        return
            outputDestination;
    }

    private Optional<String> extract(String regex, String context, int groupNumber) {
        return Optional.empty();
    }

    private Optional<String> extractPodName(String line, String deploymentName) {
        //var extract = RegexFns.extract(POD_REGEX.formatted(deploymentName), line, 1);
        return Optional.empty();
    }

    private Optional<Runnable> createLoggingTask(String deploymentName, String outputDir, String pod) {
        Runnable runnable = () -> {
            var command = LOG_POD_COMMAND.formatted(pod);
            var process = execCommand(command, outputDir + "/" + pod + ".log");

            while (process.isPresent() && process.get().isAlive()) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        return Optional.of(runnable);
    }

    private List<String> getPods(String deploymentName) {
        var command = GET_POD_COMMAND.formatted(deploymentName);
        var process = execCommand(command);
        return
            process.map(processValue -> processValue.getInputStream())
                .flatMap(inputStream -> {
                    var output = processInputStream(inputStream,
                        br -> br.lines().collect(Collectors.toUnmodifiableList()));
                    return output;
                }).orElseGet(List::of);

    }

    private <T> Optional<T> processInputStream(InputStream inputStream,
        Function<BufferedReader, T> bufferedReaderTFunction) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            return Optional.ofNullable(bufferedReaderTFunction.apply(br));
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private Optional<Process> execCommand(String command) {
        try {
            ProcessBuilder pb = createProcessBuilder(command);
            return Optional.of(pb.start());
        } catch (IOException e) {
            System.err.println("command execution failed : " + e.getMessage());
            return Optional.empty();
        }
    }

    private ProcessBuilder createProcessBuilder(String command) {
        var pb = new ProcessBuilder(command.split(" "));
        pb.directory(new File(PROCESS_DIR));
        pb.redirectErrorStream(true);
        return pb;
    }

    private Optional<Process> execCommand(String command, String redirectToFile) {
        try {
            var pb = createProcessBuilder(command);
            File redirectFile = new File(redirectToFile);
            pb.redirectOutput(Redirect.to(redirectFile));
            return Optional.of(pb.start());
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Command execution failed " + e.getMessage());
            return Optional.empty();
        }
    }
}
