package com.dosmike.spsauce;

import com.dosmike.spsauce.tasks.CompileTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskList {

    private final ArrayList<Task> taskList = new ArrayList<>();
    private static int parallelCompiles = 1;
    private int stepIndex = 0;
    public void and(Task task) {
        taskList.add(task);
    }

    public static void setCompilePoolSize(int sz) {
        if (sz < 1) sz = Runtime.getRuntime().availableProcessors();
        parallelCompiles = sz;
    }

    public boolean canStep() {
        return stepIndex < taskList.size();
    }

    public void step(ExecutorService service) {
        if (taskList.get(stepIndex) instanceof CompileTask) {
            List<Task> compileTasks = new ArrayList<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            //collect tasks
            while (stepIndex < taskList.size() && taskList.get(stepIndex) instanceof CompileTask) {
                compileTasks.add(taskList.get(stepIndex));
                stepIndex++;
            }
            //schedule tasks
            ExecutorService limitedPool = Executors.newFixedThreadPool(parallelCompiles);
            for (Task t : compileTasks) futures.add(t.submit(limitedPool));
            //join tasks
            for (CompletableFuture<Void> f : futures) f.join();
            limitedPool.shutdown();
        } else {
            taskList.get(stepIndex).submit(service).join();
            stepIndex++;
        }
    }

}
