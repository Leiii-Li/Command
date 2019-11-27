package com.vsoontech.game.exec;


import android.os.Handler;
import android.os.Looper;
import java.util.LinkedList;
import java.util.concurrent.Executor;

/**
 * <pre>
 *      @author  : Nelson
 *      @since   : 2019/11/8
 *      github  : https://github.com/Nelson-KK
 *      desc    :
 * </pre>
 */
public class Commander {

    public static final String TAG = Commander.class.getSimpleName();
    private static Commander mInstance = null;

    private LinkedList<CommandTask> mTaskQueue;
    private int mParallelTaskCount = 0;

    // 用于回调Process的执行结果
    private Executor mResponsePoster;

    public static void init(int parallelTaskCount) {
        mInstance = new Commander(parallelTaskCount);
    }

    public static Commander getInstance() {
        if (mInstance == null) {
            throw new NullPointerException("Please Init This Instance");
        }
        return mInstance;
    }

    private Commander(int parallelTaskCount) {
        mTaskQueue = new LinkedList<>();

        mParallelTaskCount = parallelTaskCount;
        if (mParallelTaskCount <= 0) {
            mParallelTaskCount = Integer.MAX_VALUE;
        }

        final Handler handler = new Handler(Looper.getMainLooper());
        // 用于在主线程中进行回调
        mResponsePoster = new Executor() {
            @Override
            public void execute(Runnable command) {
                handler.post(command);
            }
        };
    }

    Executor getResponsePoster() {
        return mResponsePoster;
    }

    public void addTask(CommandTask task) {
        schedule(task, null);
    }

    public void addTask(CommandTask task, ExecListener listener) {
        schedule(task, listener);
    }

    private synchronized void schedule(CommandTask commandTask, ExecListener listener) {

        int runningTaskCount = 0;
        for (CommandTask task : mTaskQueue) {
            if (task.isRunning()) {
                runningTaskCount++;
            }
        }

        // 如果当前正在执行的CommandTask超出限制
        if (runningTaskCount >= mParallelTaskCount) {
            throw new NullPointerException("There's not enough space");
        }

        synchronized (mTaskQueue) {
            mTaskQueue.add(commandTask);
            commandTask.setCommander(Commander.this);
        }

        for (CommandTask task : mTaskQueue) {
            task.deploy(listener);
            if (++runningTaskCount == mParallelTaskCount) {
                return;
            }
        }
    }

    public synchronized void cancelTask(CommandTask task) {
        mTaskQueue.get(mTaskQueue.indexOf(task)).cancel();
    }


    /**
     * 关闭所有下载任务
     */
    public synchronized void cancelAll() {
        while (!mTaskQueue.isEmpty()) {
            mTaskQueue.get(0).cancel();
        }
    }

    void removeTask(CommandTask task) {
        mTaskQueue.remove(task);
    }
}
