package com.dosmike.spsauce;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public interface Task {

    void run() throws Throwable;

    public default CompletableFuture<Void> submit(ExecutorService executor) {
        CompletableFuture<Void> observable = new CompletableFuture<>();
        executor.submit(()->{
            try {
                this.run();
                observable.complete(null);
            } catch (Throwable e) {
                observable.completeExceptionally(e);
            }
        });
        return observable;
    }

}
