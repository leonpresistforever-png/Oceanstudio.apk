#define _GNU_SOURCE
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <pty.h>
#include <signal.h>
#include <stdint.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "OceanPty"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef struct {
    _Atomic int master;
    pid_t pid;
    _Atomic int exit_status;
    _Atomic int closed;
    _Atomic int wait_started;
    _Atomic int wait_finished;
    int log_fd;
} ocean_pty;

static _Thread_local int ocean_last_errno = 0;
static _Thread_local char ocean_last_error_detail[1024] = "None";

typedef struct {
    char stage[32];
    int err_num;
    char err_str[128];
    char details[512];
} pty_child_error;

static void breadcrumb(int fd, const char *message, size_t length) {
    if (fd < 0) return;
    size_t written = 0;
    while (written < length) {
        ssize_t n = write(fd, message + written, length - written);
        if (n > 0) { written += (size_t)n; continue; }
        if (n < 0 && errno == EINTR) continue;
        break;
    }
    (void)fsync(fd);
}
#define CRUMB(fd, literal) breadcrumb((fd), (literal), sizeof(literal) - 1)

static char **strings(JNIEnv *env, jobjectArray source) {
    jsize count = source ? (*env)->GetArrayLength(env, source) : 0;
    char **result = calloc((size_t)count + 1, sizeof(char *));
    if (!result) { ocean_last_errno = ENOMEM; return NULL; }
    for (jsize i = 0; i < count; i++) {
        jstring value = (jstring)(*env)->GetObjectArrayElement(env, source, i);
        if (!value) { ocean_last_errno = EINVAL; goto failure; }
        const char *utf = (*env)->GetStringUTFChars(env, value, NULL);
        if (!utf) { (*env)->DeleteLocalRef(env, value); ocean_last_errno = ENOMEM; goto failure; }
        result[i] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, value, utf);
        (*env)->DeleteLocalRef(env, value);
        if (!result[i]) { ocean_last_errno = ENOMEM; goto failure; }
    }
    return result;
failure:
    for (jsize i = 0; i < count; i++) free(result[i]);
    free(result);
    return NULL;
}

static void release_strings(char **values) {
    if (!values) return;
    for (char **p = values; *p; p++) free(*p);
    free(values);
}

static jlong ocean_pty_create_internal(
        JNIEnv *env, jstring executable, jobjectArray arguments,
        jobjectArray environment, jstring cwd, jint rows, jint columns, const char *diagnostic) {
    ocean_last_errno = 0;
    snprintf(ocean_last_error_detail, sizeof(ocean_last_error_detail), "None");

    if (!executable || !arguments || !environment || !cwd || rows <= 0 || columns <= 0) {
        ocean_last_errno = EINVAL;
        snprintf(ocean_last_error_detail, sizeof(ocean_last_error_detail),
                 "Invalid arguments: executable=%p, args=%p, env=%p, cwd=%p, rows=%d, cols=%d",
                 executable, arguments, environment, cwd, (int)rows, (int)columns);
        LOGE("%s", ocean_last_error_detail);
        return 0;
    }

    const char *exe = (*env)->GetStringUTFChars(env, executable, NULL);
    const char *directory = (*env)->GetStringUTFChars(env, cwd, NULL);
    if (!exe || !directory) {
        if (exe) (*env)->ReleaseStringUTFChars(env, executable, exe);
        if (directory) (*env)->ReleaseStringUTFChars(env, cwd, directory);
        ocean_last_errno = ENOMEM;
        snprintf(ocean_last_error_detail, sizeof(ocean_last_error_detail), "Out of memory copying strings");
        LOGE("%s", ocean_last_error_detail);
        return 0;
    }

    char **argv = strings(env, arguments);
    char **envp = strings(env, environment);
    int master = -1, slave = -1;
    int diagnostic_fd = diagnostic ? open(diagnostic, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0600) : -1;
    struct winsize size = { (unsigned short)rows, (unsigned short)columns, 0, 0 };

    CRUMB(diagnostic_fd, "[N001] nativeCreate entered\n");
    LOGI("[N001] nativeCreate: exe=%s cwd=%s rows=%d cols=%d", exe, directory, (int)rows, (int)columns);

    if (!argv || !envp) {
        snprintf(ocean_last_error_detail, sizeof(ocean_last_error_detail), "Failed preparing argv/envp arrays");
        LOGE("%s", ocean_last_error_detail);
        goto failure;
    }

    CRUMB(diagnostic_fd, "[N002] arguments validated\n[N010] openpty BEGIN\n");
    if (openpty(&master, &slave, NULL, NULL, &size) < 0) {
        ocean_last_errno = errno;
        snprintf(ocean_last_error_detail, sizeof(ocean_last_error_detail),
                 "openpty() failed: errno=%d (%s)", errno, strerror(errno));
        LOGE("%s", ocean_last_error_detail);
        CRUMB(diagnostic_fd, "[N010-ERR] openpty failed\n");
        goto failure;
    }
    CRUMB(diagnostic_fd, "[N011] openpty END rc=0; master/slave created\n");
    LOGI("[N011] openpty OK master=%d slave=%d", master, slave);

    // Error communication pipe from child to parent
    int err_pipe[2];
    if (pipe2(err_pipe, O_CLOEXEC) < 0) {
        ocean_last_errno = errno;
        snprintf(ocean_last_error_detail, sizeof(ocean_last_error_detail),
                 "pipe2() failed: errno=%d (%s)", errno, strerror(errno));
        LOGE("%s", ocean_last_error_detail);
        CRUMB(diagnostic_fd, "[N015-ERR] pipe2 failed\n");
        goto failure;
    }

    CRUMB(diagnostic_fd, "[N020] fork BEGIN\n");
    pid_t pid = fork();
    if (pid < 0) {
        ocean_last_errno = errno;
        snprintf(ocean_last_error_detail, sizeof(ocean_last_error_detail),
                 "fork() failed: errno=%d (%s)", errno, strerror(errno));
        LOGE("%s", ocean_last_error_detail);
        CRUMB(diagnostic_fd, "[N020-ERR] fork failed\n");
        close(err_pipe[0]);
        close(err_pipe[1]);
        goto failure;
    }

    if (pid == 0) {
        // Child
        close(master);
        close(err_pipe[0]);

        pty_child_error err_info;
        memset(&err_info, 0, sizeof(err_info));

        CRUMB(diagnostic_fd, "[N030] setsid BEGIN\n");
        if (setsid() < 0) {
            strncpy(err_info.stage, "setsid()", sizeof(err_info.stage) - 1);
            err_info.err_num = errno;
            strncpy(err_info.err_str, strerror(errno), sizeof(err_info.err_str) - 1);
            (void)write(err_pipe[1], &err_info, sizeof(err_info));
            _exit(126);
        }

        CRUMB(diagnostic_fd, "[N032] ioctl TIOCSCTTY BEGIN\n");
        if (ioctl(slave, TIOCSCTTY, 0) < 0) {
            strncpy(err_info.stage, "ioctl(TIOCSCTTY)", sizeof(err_info.stage) - 1);
            err_info.err_num = errno;
            strncpy(err_info.err_str, strerror(errno), sizeof(err_info.err_str) - 1);
            (void)write(err_pipe[1], &err_info, sizeof(err_info));
            _exit(126);
        }

        CRUMB(diagnostic_fd, "[N034] dup2 stdio BEGIN\n");
        if (dup2(slave, 0) < 0 || dup2(slave, 1) < 0 || dup2(slave, 2) < 0) {
            strncpy(err_info.stage, "dup2()", sizeof(err_info.stage) - 1);
            err_info.err_num = errno;
            strncpy(err_info.err_str, strerror(errno), sizeof(err_info.err_str) - 1);
            (void)write(err_pipe[1], &err_info, sizeof(err_info));
            _exit(126);
        }
        if (slave > 2) close(slave);

        CRUMB(diagnostic_fd, "[N037] chdir BEGIN\n");
        if (chdir(directory) < 0) {
            strncpy(err_info.stage, "chdir()", sizeof(err_info.stage) - 1);
            err_info.err_num = errno;
            strncpy(err_info.err_str, strerror(errno), sizeof(err_info.err_str) - 1);
            snprintf(err_info.details, sizeof(err_info.details), "Target directory: %s", directory);
            (void)write(err_pipe[1], &err_info, sizeof(err_info));
            _exit(126);
        }

        // Section 5: Log before exec
        struct stat st;
        int stat_rc = stat(exe, &st);
        int acc_rc = access(exe, X_OK);
        int acc_err = acc_rc < 0 ? errno : 0;

        char log_buf[512];
        snprintf(log_buf, sizeof(log_buf),
                 "[N038-EXEC-INFO]\n"
                 "  shell: %s\n"
                 "  cwd: %s\n"
                 "  HOME: %s\n"
                 "  PREFIX: %s\n"
                 "  PATH: %s\n"
                 "  stat: rc=%d, mode=0%o, uid=%d, size=%lld\n"
                 "  access(X_OK): rc=%d, errno=%d (%s)\n",
                 exe, directory,
                 getenv("HOME") ? getenv("HOME") : "(null)",
                 getenv("PREFIX") ? getenv("PREFIX") : "(null)",
                 getenv("PATH") ? getenv("PATH") : "(null)",
                 stat_rc, stat_rc == 0 ? (unsigned int)st.st_mode : 0,
                 stat_rc == 0 ? (int)st.st_uid : -1,
                 stat_rc == 0 ? (long long)st.st_size : -1LL,
                 acc_rc, acc_err, acc_err ? strerror(acc_err) : "OK");
        CRUMB(diagnostic_fd, log_buf);
        LOGI("%s", log_buf);

        CRUMB(diagnostic_fd, "[N039] execve BEGIN\n");
        execve(exe, argv, envp);

        // execve failed!
        int exec_errno = errno;
        strncpy(err_info.stage, "execve()", sizeof(err_info.stage) - 1);
        err_info.err_num = exec_errno;
        strncpy(err_info.err_str, strerror(exec_errno), sizeof(err_info.err_str) - 1);
        snprintf(err_info.details, sizeof(err_info.details),
                 "exe=%s, stat_rc=%d, mode=0%o, uid=%d, size=%lld, access_x=%d (err=%d)",
                 exe, stat_rc, stat_rc == 0 ? (unsigned int)st.st_mode : 0,
                 stat_rc == 0 ? (int)st.st_uid : -1,
                 stat_rc == 0 ? (long long)st.st_size : -1LL,
                 acc_rc, acc_err);

        CRUMB(diagnostic_fd, "[N040-ERR] execve failed!\n");
        LOGE("[N040-ERR] execve failed: errno=%d (%s) exe=%s", exec_errno, strerror(exec_errno), exe);
        (void)write(err_pipe[1], &err_info, sizeof(err_info));
        _exit(exec_errno == ENOENT ? 127 : 126);
    }

    // Parent
    close(slave);
    slave = -1;
    close(err_pipe[1]);

    pty_child_error child_err;
    memset(&child_err, 0, sizeof(child_err));
    ssize_t n = read(err_pipe[0], &child_err, sizeof(child_err));
    close(err_pipe[0]);

    if (n > 0) {
        ocean_last_errno = child_err.err_num;
        snprintf(ocean_last_error_detail, sizeof(ocean_last_error_detail),
                 "Child %s failed: errno=%d (%s) details: %s",
                 child_err.stage, child_err.err_num, child_err.err_str, child_err.details);
        LOGE("%s", ocean_last_error_detail);
        CRUMB(diagnostic_fd, "[N045-ERR] Parent observed child exec error\n");
        CRUMB(diagnostic_fd, ocean_last_error_detail);
        CRUMB(diagnostic_fd, "\n");
        kill(pid, SIGKILL);
        waitpid(pid, NULL, 0);
        close(master);
        master = -1;
        goto failure;
    }

    CRUMB(diagnostic_fd, "[N046] Child execve confirmed successful (pipe EOF)\n");
    LOGI("[N046] Child execve successful pid=%d", pid);

    if (fcntl(master, F_SETFD, FD_CLOEXEC) < 0) {
        ocean_last_errno = errno;
        snprintf(ocean_last_error_detail, sizeof(ocean_last_error_detail),
                 "fcntl(master, FD_CLOEXEC) failed: errno=%d (%s)", errno, strerror(errno));
        LOGE("%s", ocean_last_error_detail);
        kill(pid, SIGKILL);
        close(master);
        master = -1;
        goto failure;
    }

    ocean_pty *pty = calloc(1, sizeof(*pty));
    if (!pty) {
        ocean_last_errno = ENOMEM;
        snprintf(ocean_last_error_detail, sizeof(ocean_last_error_detail), "calloc(ocean_pty) out of memory");
        LOGE("%s", ocean_last_error_detail);
        kill(pid, SIGKILL);
        close(master);
        master = -1;
        goto failure;
    }

    pty->master = master;
    pty->pid = pid;
    pty->exit_status = -1;
    pty->log_fd = diagnostic_fd;

    CRUMB(diagnostic_fd, "[N051] session constructed; master registered\n");
    diagnostic_fd = -1;

    release_strings(argv);
    release_strings(envp);
    (*env)->ReleaseStringUTFChars(env, executable, exe);
    (*env)->ReleaseStringUTFChars(env, cwd, directory);
    return (jlong)(intptr_t)pty;

failure:
    if (master >= 0) close(master);
    if (slave >= 0) close(slave);
    release_strings(argv);
    release_strings(envp);
    CRUMB(diagnostic_fd, "[NXXX] nativeCreate failed: ");
    CRUMB(diagnostic_fd, ocean_last_error_detail);
    CRUMB(diagnostic_fd, "\n");
    if (diagnostic_fd >= 0) close(diagnostic_fd);
    (*env)->ReleaseStringUTFChars(env, executable, exe);
    (*env)->ReleaseStringUTFChars(env, cwd, directory);
    return 0;
}

// 7-argument signature (diagnostic_path passed)
JNIEXPORT jlong JNICALL Java_studio_ocean_app_terminal_NativePty_create__Ljava_lang_String_2_3Ljava_lang_String_2_3Ljava_lang_String_2Ljava_lang_String_2IILjava_lang_String_2(
        JNIEnv *env, jclass type, jstring executable, jobjectArray arguments,
        jobjectArray environment, jstring cwd, jint rows, jint columns, jstring diagnostic_path) {
    (void)type;
    const char *diag = diagnostic_path ? (*env)->GetStringUTFChars(env, diagnostic_path, NULL) : NULL;
    jlong handle = ocean_pty_create_internal(env, executable, arguments, environment, cwd, rows, columns, diag);
    if (diag) (*env)->ReleaseStringUTFChars(env, diagnostic_path, diag);
    return handle;
}

// Non-overloaded name for 7-argument signature
JNIEXPORT jlong JNICALL Java_studio_ocean_app_terminal_NativePty_create(
        JNIEnv *env, jclass type, jstring executable, jobjectArray arguments,
        jobjectArray environment, jstring cwd, jint rows, jint columns, jstring diagnostic_path) {
    (void)type;
    const char *diag = diagnostic_path ? (*env)->GetStringUTFChars(env, diagnostic_path, NULL) : NULL;
    jlong handle = ocean_pty_create_internal(env, executable, arguments, environment, cwd, rows, columns, diag);
    if (diag) (*env)->ReleaseStringUTFChars(env, diagnostic_path, diag);
    return handle;
}

// 6-argument overloaded signature (backwards compatibility with older DEX)
JNIEXPORT jlong JNICALL Java_studio_ocean_app_terminal_NativePty_create__Ljava_lang_String_2_3Ljava_lang_String_2_3Ljava_lang_String_2Ljava_lang_String_2II(
        JNIEnv *env, jclass type, jstring executable, jobjectArray arguments,
        jobjectArray environment, jstring cwd, jint rows, jint columns) {
    (void)type;
    return ocean_pty_create_internal(env, executable, arguments, environment, cwd, rows, columns, "/data/data/studio.ocean.app/files/logs/terminal-diagnostics/native-pty.log");
}

JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_lastErrno(JNIEnv *e, jclass t) {
    (void)e; (void)t;
    return ocean_last_errno;
}

JNIEXPORT jstring JNICALL Java_studio_ocean_app_terminal_NativePty_lastError(JNIEnv *env, jclass type) {
    (void)type;
    return (*env)->NewStringUTF(env, ocean_last_error_detail);
}

JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_read(JNIEnv *env, jclass type, jlong handle, jbyteArray target) {
    (void)type;
    ocean_pty *p = (ocean_pty *)(intptr_t)handle;
    if (!p || !target || atomic_load(&p->closed)) return -EINVAL;
    jsize size = (*env)->GetArrayLength(env, target);
    if (size <= 0) return -EINVAL;
    jbyte *bytes = (*env)->GetByteArrayElements(env, target, NULL);
    if (!bytes) return -ENOMEM;
    ssize_t n;
    do {
        n = read(atomic_load(&p->master), bytes, (size_t)size);
    } while (n < 0 && errno == EINTR);
    int error = errno;
    (*env)->ReleaseByteArrayElements(env, target, bytes, n > 0 ? 0 : JNI_ABORT);
    if (n < 0 && error == EIO) return 0;
    if (n < 0 && error == EAGAIN) return -EAGAIN;
    return n < 0 ? -error : (jint)n;
}

JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_write(JNIEnv *env, jclass type, jlong handle, jbyteArray source, jint length) {
    (void)type;
    ocean_pty *p = (ocean_pty *)(intptr_t)handle;
    if (!p || !source || atomic_load(&p->closed)) return -EINVAL;
    jsize size = (*env)->GetArrayLength(env, source);
    if (length < 0 || length > size) return -EINVAL;
    jbyte *b = (*env)->GetByteArrayElements(env, source, NULL);
    if (!b) return -ENOMEM;
    size_t sent = 0;
    int error = 0;
    while (sent < (size_t)length) {
        ssize_t n = write(atomic_load(&p->master), b + sent, (size_t)length - sent);
        if (n > 0) { sent += (size_t)n; continue; }
        if (n < 0 && errno == EINTR) continue;
        error = n < 0 ? errno : EIO;
        break;
    }
    (*env)->ReleaseByteArrayElements(env, source, b, JNI_ABORT);
    return error ? -error : (jint)sent;
}

JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_resize(JNIEnv *env, jclass type, jlong handle, jint rows, jint columns, jint width, jint height) {
    (void)env; (void)type;
    ocean_pty *p = (ocean_pty *)(intptr_t)handle;
    if (!p || atomic_load(&p->closed) || rows <= 0 || columns <= 0) return -EINVAL;
    struct winsize s = { (unsigned short)rows, (unsigned short)columns, (unsigned short)width, (unsigned short)height };
    return ioctl(atomic_load(&p->master), TIOCSWINSZ, &s) < 0 ? -errno : 0;
}

JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_waitExit(JNIEnv *env, jclass type, jlong handle) {
    (void)env; (void)type;
    ocean_pty *p = (ocean_pty *)(intptr_t)handle;
    if (!p) return -EINVAL;
    int known = atomic_load(&p->exit_status);
    if (known >= 0) return known;
    if (atomic_exchange(&p->wait_started, 1)) return -EALREADY;
    int status;
    pid_t result;
    do {
        result = waitpid(p->pid, &status, 0);
    } while (result < 0 && errno == EINTR);
    if (result < 0) {
        int error = errno;
        atomic_store(&p->exit_status, 255);
        atomic_store(&p->wait_finished, 1);
        return -error;
    }
    int code = WIFEXITED(status) ? WEXITSTATUS(status) : WIFSIGNALED(status) ? 128 + WTERMSIG(status) : 255;
    atomic_store(&p->exit_status, code);
    atomic_store(&p->wait_finished, 1);
    return code;
}

JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_signal(JNIEnv *env, jclass type, jlong handle, jint signal) {
    (void)env; (void)type;
    ocean_pty *p = (ocean_pty *)(intptr_t)handle;
    if (p && !atomic_load(&p->closed) && signal > 0) kill(-p->pid, signal);
}

JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_close(JNIEnv *env, jclass type, jlong handle) {
    (void)env; (void)type;
    ocean_pty *p = (ocean_pty *)(intptr_t)handle;
    if (!p) return;
    if (atomic_exchange(&p->closed, 1)) return;
    int fd = atomic_exchange(&p->master, -1);
    if (fd >= 0) close(fd);
    if (p->exit_status < 0) kill(-p->pid, SIGHUP);
}

JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_destroy(JNIEnv *env, jclass type, jlong handle) {
    (void)env; (void)type;
    ocean_pty *p = (ocean_pty *)(intptr_t)handle;
    if (!p) return;
    if (!atomic_load(&p->wait_finished)) return;
    if (!atomic_exchange(&p->closed, 1)) {
        int fd = atomic_exchange(&p->master, -1);
        if (fd >= 0) close(fd);
    }
    if (p->log_fd >= 0) close(p->log_fd);
    free(p);
}

JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_pid(JNIEnv *env, jclass type, jlong handle) {
    (void)env; (void)type;
    ocean_pty *p = (ocean_pty *)(intptr_t)handle;
    return p ? (jint)p->pid : -EINVAL;
}

JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_masterFd(JNIEnv *env, jclass type, jlong handle) {
    (void)env; (void)type;
    ocean_pty *p = (ocean_pty *)(intptr_t)handle;
    return p ? atomic_load(&p->master) : -EINVAL;
}
