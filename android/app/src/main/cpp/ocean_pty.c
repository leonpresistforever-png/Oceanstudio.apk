#define _GNU_SOURCE
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

static char **strings(JNIEnv *env, jobjectArray input) {
    jsize count = input ? (*env)->GetArrayLength(env, input) : 0;
    char **result = calloc((size_t) count + 1, sizeof(char *));
    if (!result) return NULL;
    for (jsize i = 0; i < count; i++) {
        jstring value = (jstring) (*env)->GetObjectArrayElement(env, input, i);
        const char *utf = (*env)->GetStringUTFChars(env, value, NULL);
        result[i] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, value, utf);
        (*env)->DeleteLocalRef(env, value);
    }
    return result;
}

static void release_strings(char **values) {
    if (!values) return;
    for (size_t i = 0; values[i]; i++) free(values[i]);
    free(values);
}

JNIEXPORT jintArray JNICALL Java_studio_ocean_app_terminal_NativePty_create(
        JNIEnv *env, jclass type, jobjectArray command, jobjectArray environment,
        jstring working_directory, jint rows, jint columns) {
    (void) type;
    char **argv = strings(env, command);
    char **envp = strings(env, environment);
    if (!argv || !argv[0] || !envp) { release_strings(argv); release_strings(envp); return NULL; }

    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0 || grantpt(master) || unlockpt(master)) goto fail;
    char slave_name[128];
    if (ptsname_r(master, slave_name, sizeof(slave_name))) goto fail;
    int slave = open(slave_name, O_RDWR | O_NOCTTY);
    if (slave < 0) goto fail;
    struct winsize size = {.ws_row=(unsigned short)rows, .ws_col=(unsigned short)columns};
    ioctl(slave, TIOCSWINSZ, &size);

    pid_t pid = fork();
    if (pid < 0) { close(slave); goto fail; }
    if (pid == 0) {
        close(master);
        setsid();
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO); dup2(slave, STDOUT_FILENO); dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        if (working_directory) {
            const char *cwd = (*env)->GetStringUTFChars(env, working_directory, NULL);
            if (cwd) { chdir(cwd); (*env)->ReleaseStringUTFChars(env, working_directory, cwd); }
        }
        execve(argv[0], argv, envp);
        dprintf(STDERR_FILENO, "Ocean Terminal: exec failed: %s\r\n", strerror(errno));
        _exit(127);
    }
    close(slave);
    int writer = dup(master);
    release_strings(argv); release_strings(envp);
    if (writer < 0) { close(master); kill(pid, SIGKILL); return NULL; }
    jint result_values[3] = {master, writer, (jint)pid};
    jintArray result = (*env)->NewIntArray(env, 3);
    if (result) (*env)->SetIntArrayRegion(env, result, 0, 3, result_values);
    return result;
fail:
    if (master >= 0) close(master);
    release_strings(argv); release_strings(envp);
    return NULL;
}

JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_read(
        JNIEnv *env, jclass type, jint fd, jbyteArray destination) {
    (void) type;
    jsize capacity = (*env)->GetArrayLength(env, destination);
    jbyte *bytes = (*env)->GetByteArrayElements(env, destination, NULL);
    ssize_t count;
    do { count = read(fd, bytes, (size_t)capacity); } while (count < 0 && errno == EINTR);
    if (count > 0) (*env)->ReleaseByteArrayElements(env, destination, bytes, 0);
    else (*env)->ReleaseByteArrayElements(env, destination, bytes, JNI_ABORT);
    return (jint)count;
}

JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_write(
        JNIEnv *env, jclass type, jint fd, jbyteArray source) {
    (void) type;
    jsize length = (*env)->GetArrayLength(env, source);
    jbyte *bytes = (*env)->GetByteArrayElements(env, source, NULL);
    size_t offset = 0;
    while (offset < (size_t)length) {
        ssize_t count = write(fd, bytes + offset, (size_t)length - offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) { (*env)->ReleaseByteArrayElements(env, source, bytes, JNI_ABORT); return -errno; }
        offset += (size_t)count;
    }
    (*env)->ReleaseByteArrayElements(env, source, bytes, JNI_ABORT);
    return (jint)offset;
}

JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_resize(
        JNIEnv *env, jclass type, jint fd, jint rows, jint columns) {
    (void)env; (void)type;
    struct winsize size = {.ws_row=(unsigned short)rows, .ws_col=(unsigned short)columns};
    ioctl(fd, TIOCSWINSZ, &size);
}

JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_waitFor(
        JNIEnv *env, jclass type, jint pid) {
    (void)env; (void)type;
    int status;
    while (waitpid((pid_t)pid, &status, 0) < 0) if (errno != EINTR) return -errno;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_signal(
        JNIEnv *env, jclass type, jint pid, jint signal_number) {
    (void)env; (void)type;
    return kill(-((pid_t)pid), signal_number) == 0 ? 0 : -errno;
}

JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_close(
        JNIEnv *env, jclass type, jint fd) { (void)env; (void)type; close(fd); }
