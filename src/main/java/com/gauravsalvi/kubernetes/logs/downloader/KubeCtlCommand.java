package com.gauravsalvi.kubernetes.logs.downloader;

import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.With;

@RequiredArgsConstructor
@Builder
@With
public class KubeCtlCommand implements Runnable {

    private static final Set<String> SERVICES = Set.of(
        "portfolio-core-reader-task-planner",
        "portfolio-core-reader-worker"
/*        "portfolio-core-api",
        "portfolio-core"*/
    );

    private static final String GET_POD_COMMAND = "kubectl get pods -l name=portfolio-core-reader-task-planner";

    private static final String LOG_POD_COMMAND = "kubectl logs %s -f";

    private static final String POD_REGEX = "(%s[-\\w]*)";

    public static final String LOGS = "/Users/gauravsalvi/Desktop/logs";

    @SneakyThrows
    @Override
    public void run() {
        try {
            System.out.println("Hello . . .");
            System.out.printf("Enter path to store server logs to ? %n");
            var logPath = new Scanner(System.in).next();
            SERVICES.stream()
                .map(service -> CompletableFuture.runAsync(
                    () -> new KubeDeploymentLogManager(service, logPath).startStreaming()))
                .reduce((left, right) -> left.thenCombine(right, (pair1, pair2) -> pair1))
                .orElseThrow()
                .join();
        } catch (Exception th) {
            th.printStackTrace();
            System.out.printf("** %s ** %n", "Restarting ...");
            run();
        }
    }

    public static void main(String[] args) {
        new KubeCtlCommand().run();
    }
}
