#include <jni.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <string.h>
#include <errno.h>
#include <pty.h>
#include <utmp.h>

static char **jobject_array_to_c(JNIEnv *env, jobjectArray arr, int *out_len) {
    if (arr == NULL) {
        *out_len = 0;
        return NULL;
    }
    jsize len = (*env)->GetArrayLength(env, arr);
    char **result = (char **) malloc(sizeof(char *) * (len + 1));
    if (result == NULL) {
        *out_len = 0;
        return NULL;
    }
    for (jsize i = 0; i < len; i++) {
        jstring jstr = (jstring) (*env)->GetObjectArrayElement(env, arr, i);
        const char *cstr = (*env)->GetStringUTFChars(env, jstr, NULL);
        result[i] = strdup(cstr);
        (*env)->ReleaseStringUTFChars(env, jstr, cstr);
        (*env)->DeleteLocalRef(env, jstr);
    }
    result[len] = NULL;
    *out_len = (int) len;
    return result;
}

static void free_c_array(char **arr, int len) {
    if (arr == NULL) return;
    for (int i = 0; i < len; i++) {
        free(arr[i]);
    }
    free(arr);
}

JNIEXPORT jintArray JNICALL
Java_studio_ocean_nativeterminal_PtyBridge_nativeCreatePty(
        JNIEnv *env, jclass clazz,
        jobjectArray cmdArray,
        jobjectArray envArray,
        jstring cwdStr,
        jint rows,
        jint cols) {

    int cmd_len = 0;
    int env_len = 0;
    char **argv = jobject_array_to_c(env, cmdArray, &cmd_len);
    char **envp = jobject_array_to_c(env, envArray, &env_len);

    if (argv == NULL || cmd_len == 0) {
        free_c_array(argv, cmd_len);
        free_c_array(envp, env_len);
        return NULL;
    }

    int master_fd = -1;
    int slave_fd = -1;
    struct winsize ws = { .ws_row = (unsigned short) rows, .ws_col = (unsigned short) cols };

    if (openpty(&master_fd, &slave_fd, NULL, NULL, &ws) < 0) {
        free_c_array(argv, cmd_len);
        free_c_array(envp, env_len);
        return NULL;
    }

    pid_t pid = fork();
    if (pid < 0) {
        close(master_fd);
        close(slave_fd);
        free_c_array(argv, cmd_len);
        free_c_array(envp, env_len);
        return NULL;
    }

    if (pid == 0) {
        close(master_fd);
        setsid();
        ioctl(slave_fd, TIOCSCTTY, 0);
        dup2(slave_fd, 0);
        dup2(slave_fd, 1);
        dup2(slave_fd, 2);
        if (slave_fd > 2) close(slave_fd);

        if (cwdStr != NULL) {
            const char *cwd = (*env)->GetStringUTFChars(env, cwdStr, NULL);
            if (cwd != NULL) {
                chdir(cwd);
                (*env)->ReleaseStringUTFChars(env, cwdStr, cwd);
            }
        }

        if (envp != NULL && env_len > 0) {
            execve(argv[0], argv, envp);
        } else {
            execvp(argv[0], argv);
        }
        _exit(127);
    }

    close(slave_fd);
    free_c_array(argv, cmd_len);
    free_c_array(envp, env_len);

    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        close(master_fd);
        return NULL;
    }
    jint values[2] = { master_fd, (jint) pid };
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    return result;
}

JNIEXPORT void JNICALL
Java_studio_ocean_nativeterminal_PtyBridge_nativeSetWindowSize(
        JNIEnv *env, jclass clazz, jint masterFd, jint rows, jint cols) {
    struct winsize ws = { .ws_row = (unsigned short) rows, .ws_col = (unsigned short) cols };
    ioctl(masterFd, TIOCSWINSZ, &ws);
}

JNIEXPORT jint JNICALL
Java_studio_ocean_nativeterminal_PtyBridge_nativeWaitPid(
        JNIEnv *env, jclass clazz, jint pid) {
    int status = 0;
    if (waitpid((pid_t) pid, &status, 0) < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}
