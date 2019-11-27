package com.vsoontech.game.exec;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <pre>
 *      @author  : Nelson
 *      @since   : 2019/11/8
 *      github  : https://github.com/Nelson-KK
 *      desc    :
 * </pre>
 */
public class CommandTask {

    public static final String TAG = CommandTask.class.getSimpleName();

    private static final int STATUS_WAITING = 0;
    private static final int STATUS_FINISHED = 1;
    private static final int STATUS_RUNNING = 2;
    private static final int STATUS_INTERRUPT = 3;

    private int mCurrentState = STATUS_WAITING;
    private CommandTaskListener mListener;
    private List<String> mCommand = null;
    private String mCmd;
    private long mTimeDelay = 0;
    private TimeUnit mTimeUnit;
    private Disposable mSubscribe;
    private Process mProcess;
    private Commander mCommander;

    private CommandTask(Builder builder) {
        mCommand = builder.mCommand;
        mTimeDelay = builder.mTimeDelay;
        mTimeUnit = builder.mTimeUnit;
        mListener = new CommandTaskListener();
    }

    void setCommander(Commander commander) {
        mCommander = commander;
    }

    void deploy(ExecListener listener) {
        if (mCurrentState != STATUS_WAITING) {
            return;
        }
        mListener.setListener(listener);
        mCurrentState = STATUS_RUNNING;
        mSubscribe = Observable.timer(mTimeDelay, mTimeUnit).observeOn(Schedulers.io())
            .subscribe(new Consumer<Long>() {
                @Override
                public void accept(Long aLong) throws Exception {
                    // 如果延时运行途中，打断可终止Command执行
                    if (mCurrentState == STATUS_INTERRUPT) {
                        return;
                    }
                    // 组装命令
                    StringBuilder cmd = new StringBuilder();
                    for (String item : mCommand) {
                        cmd.append(item).append(" ");
                    }
                    mCmd = cmd.toString();
                    mListener.onPre(mCmd);
                    mProcess = new ProcessBuilder(mCommand).redirectErrorStream(true).start();

                    // 读取执行内容
                    BufferedReader stdin = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
                    StringBuilder result = new StringBuilder();
                    String line = null;

                    try {
                        while (mCurrentState == STATUS_RUNNING && (line = stdin.readLine()) != null) {
                            mListener.onProgress(line);
                            result.append(line);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        ProcessorUtils.closeStream(mProcess);
                        stdin.close();
                    }
                    if (mCurrentState == STATUS_RUNNING) {
                        mListener.onSuccess(result.toString());
                        mCurrentState = STATUS_FINISHED;
                    }
                }
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {
                    mListener.onError(throwable);
                    mCurrentState = STATUS_FINISHED;
                }
            });
    }

    public boolean isRunning() {
        return mCurrentState == STATUS_RUNNING;
    }

    public void cancel() {
        mCurrentState = STATUS_INTERRUPT;
        mCommander.removeTask(this);
        killProcess();
    }

    private void killProcess() {
        if (mProcess != null) {
            ProcessorUtils.killProcess(mProcess);
        }
    }


    private class CommandTaskListener implements ExecListener {

        private ExecListener mListener;

        private void setListener(ExecListener listener) {
            mListener = listener;
        }

        @Override
        public void onPre(final String command) {
            mCommander.getResponsePoster().execute(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onPre(command);
                    }
                }
            });
        }

        @Override
        public void onProgress(final String message) {
            mCommander.getResponsePoster().execute(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onProgress(message);
                    }
                }
            });
        }

        @Override
        public void onError(final Throwable t) {
            mCommander.removeTask(CommandTask.this);
            mCommander.getResponsePoster().execute(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onError(t);
                    }
                }
            });
        }

        @Override
        public void onSuccess(final String message) {
            mCommander.removeTask(CommandTask.this);
            mCommander.getResponsePoster().execute(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onSuccess(message);
                    }
                }
            });
        }
    }

    public static class Builder {

        private List<String> mCommand = new ArrayList<>();
        private TimeUnit mTimeUnit = TimeUnit.MILLISECONDS;
        private long mTimeDelay = 0;

        /**
         * 单个命令参数
         */
        public Builder command(String command) {
            mCommand.add(command);
            return this;
        }

        /**
         * 任务命令：字符串列表形式
         */
        public Builder commands(List<String> commands) {
            mCommand.addAll(commands);
            return this;
        }

        /**
         * 任务命令：字符串、字符串数组形式
         */
        public Builder commands(String... commands) {
            return commands(Arrays.asList(commands));
        }

        /**
         * 任务命令：字符串带空格转字符串数组命令
         */
        public Builder commands(String command) {
            String[] commands = command.split(" ");
            return commands(commands);
        }

        /**
         * 延时、超时的时间单位，默认:ms {@link TimeUnit#MILLISECONDS}
         */
        public Builder timeUnit(TimeUnit timeUnit) {
            mTimeUnit = timeUnit;
            return this;
        }

        /**
         * 设置任务延时启动，默认:0ms
         */
        public Builder timeDelay(long timeDelay) {
            mTimeDelay = timeDelay;
            return this;
        }

        public CommandTask build() {
            return new CommandTask(this);
        }
    }
}
