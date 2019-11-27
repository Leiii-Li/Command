package com.vsoontech.game.exec;

/**
 * <pre>
 *      @author  : Nelson
 *      @since   : 2019/11/8
 *      github  : https://github.com/Nelson-KK
 *      desc    :
 * </pre>
 */
public interface ExecListener {
    void onPre(String command);

    void onProgress(String message);

    void onError(Throwable t);

    void onSuccess(String message);
}
