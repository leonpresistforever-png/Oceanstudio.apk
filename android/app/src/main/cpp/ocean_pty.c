#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <pty.h>
#include <signal.h>
#include <stdint.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

typedef struct { _Atomic int master; pid_t pid; int exit_status; _Atomic int closed; } ocean_pty;
static _Thread_local int ocean_last_errno;

static void breadcrumb(int fd,const char *message,size_t length) {
    if(fd<0)return;
    size_t written=0;
    while(written<length){ssize_t n=write(fd,message+written,length-written);if(n>0){written+=(size_t)n;continue;}if(n<0&&errno==EINTR)continue;break;}
    (void)fsync(fd);
}
#define CRUMB(fd,literal) breadcrumb((fd),(literal),sizeof(literal)-1)

static char **strings(JNIEnv *env, jobjectArray source) {
    jsize count = source ? (*env)->GetArrayLength(env, source) : 0;
    char **result = calloc((size_t)count + 1, sizeof(char *));
    if (!result) { ocean_last_errno=ENOMEM; return NULL; }
    for (jsize i=0;i<count;i++) {
        jstring value=(jstring)(*env)->GetObjectArrayElement(env,source,i);
        if (!value) { ocean_last_errno=EINVAL; goto failure; }
        const char *utf=(*env)->GetStringUTFChars(env,value,NULL);
        if (!utf) { (*env)->DeleteLocalRef(env,value); ocean_last_errno=ENOMEM; goto failure; }
        result[i]=strdup(utf);
        (*env)->ReleaseStringUTFChars(env,value,utf);
        (*env)->DeleteLocalRef(env,value);
        if (!result[i]) { ocean_last_errno=ENOMEM; goto failure; }
    }
    return result;
failure:
    for (jsize i=0;i<count;i++) free(result[i]);
    free(result); return NULL;
}
static void release_strings(char **values) { if (!values) return; for(char **p=values;*p;p++) free(*p); free(values); }
static void child_exec_error(int error) {
    char message[]="Ocean execve failed errno=000\r\n";
    message[26]=(char)('0'+(error/100)%10); message[27]=(char)('0'+(error/10)%10); message[28]=(char)('0'+error%10);
    (void)write(STDERR_FILENO,message,sizeof(message)-1);
}

JNIEXPORT jlong JNICALL Java_studio_ocean_app_terminal_NativePty_create(JNIEnv *env,jclass type,jstring executable,jobjectArray arguments,jobjectArray environment,jstring cwd,jint rows,jint columns,jstring diagnostic_path) {
    (void)type; ocean_last_errno=0;
    if(!executable||!arguments||!environment||!cwd||!diagnostic_path||rows<=0||columns<=0){ocean_last_errno=EINVAL;return 0;}
    const char *exe=(*env)->GetStringUTFChars(env,executable,NULL);
    const char *directory=(*env)->GetStringUTFChars(env,cwd,NULL);
    const char *diagnostic=(*env)->GetStringUTFChars(env,diagnostic_path,NULL);
    if(!exe||!directory||!diagnostic){if(exe)(*env)->ReleaseStringUTFChars(env,executable,exe);if(directory)(*env)->ReleaseStringUTFChars(env,cwd,directory);if(diagnostic)(*env)->ReleaseStringUTFChars(env,diagnostic_path,diagnostic);ocean_last_errno=ENOMEM;return 0;}
    char **argv=strings(env,arguments), **envp=strings(env,environment);
    int master=-1,slave=-1,diagnostic_fd=open(diagnostic,O_WRONLY|O_CREAT|O_APPEND|O_CLOEXEC,0600); struct winsize size={(unsigned short)rows,(unsigned short)columns,0,0};
    CRUMB(diagnostic_fd,"[N01] nativeCreate entered\n");
    if(!argv||!envp)goto failure;
    CRUMB(diagnostic_fd,"[N02] arguments copied and validated\n[N03] openpty begin\n");
    if(openpty(&master,&slave,NULL,NULL,&size)<0){ocean_last_errno=errno;goto failure;}
    CRUMB(diagnostic_fd,"[N04] openpty success\n[N05] fork begin\n");
    pid_t pid=fork(); if(pid<0){ocean_last_errno=errno;goto failure;}
    if(pid==0){
        CRUMB(diagnostic_fd,"[N06-child] child entered\n");
        close(master);
        CRUMB(diagnostic_fd,"[N07-child] setsid begin\n");
        if(setsid()<0){child_exec_error(errno);_exit(126);}
        CRUMB(diagnostic_fd,"[N08-child] setsid success\n[N09-child] TIOCSCTTY begin\n");
        if(ioctl(slave,TIOCSCTTY,0)<0){child_exec_error(errno);_exit(126);}
        CRUMB(diagnostic_fd,"[N10-child] dup2 stdin\n");
        if(dup2(slave,0)<0){child_exec_error(errno);_exit(126);}
        CRUMB(diagnostic_fd,"[N11-child] dup2 stdout\n");
        if(dup2(slave,1)<0){child_exec_error(errno);_exit(126);}
        CRUMB(diagnostic_fd,"[N12-child] dup2 stderr\n");
        if(dup2(slave,2)<0){child_exec_error(errno);_exit(126);}
        if(slave>2)close(slave);
        CRUMB(diagnostic_fd,"[N13-child] inherited PTY fds closed\n");
        if(chdir(directory)<0){child_exec_error(errno);_exit(126);}
        CRUMB(diagnostic_fd,"[N14-child] execve begin\n");
        execve(exe,argv,envp); int error=errno; child_exec_error(error); _exit(error==ENOENT?127:126);
    }
    CRUMB(diagnostic_fd,"[N06-parent] fork returned child pid\n");
    close(slave); slave=-1;
    if(fcntl(master,F_SETFD,FD_CLOEXEC)<0){ocean_last_errno=errno;kill(pid,SIGKILL);close(master);master=-1;goto failure;}
    ocean_pty *pty=calloc(1,sizeof(*pty));
    if(!pty){ocean_last_errno=ENOMEM;kill(pid,SIGKILL);close(master);master=-1;goto failure;}
    pty->master=master;pty->pid=pid;pty->exit_status=-1;
    CRUMB(diagnostic_fd,"[N15-parent] PTY session allocated\n");
    if(diagnostic_fd>=0)close(diagnostic_fd);
    release_strings(argv);release_strings(envp);(*env)->ReleaseStringUTFChars(env,executable,exe);(*env)->ReleaseStringUTFChars(env,cwd,directory);(*env)->ReleaseStringUTFChars(env,diagnostic_path,diagnostic);return (jlong)(intptr_t)pty;
failure:
    if(master>=0)close(master);
    if(slave>=0)close(slave);
    release_strings(argv);release_strings(envp);
    CRUMB(diagnostic_fd,"[NXX] nativeCreate failed\n");if(diagnostic_fd>=0)close(diagnostic_fd);
    (*env)->ReleaseStringUTFChars(env,executable,exe);(*env)->ReleaseStringUTFChars(env,cwd,directory);(*env)->ReleaseStringUTFChars(env,diagnostic_path,diagnostic);return 0;
}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_lastErrno(JNIEnv*e,jclass t){(void)e;(void)t;return ocean_last_errno;}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_read(JNIEnv*env,jclass type,jlong handle,jbyteArray target){
    (void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p||!target||atomic_load(&p->closed))return-EINVAL;
    jsize size=(*env)->GetArrayLength(env,target);if(size<=0)return-EINVAL;
    jbyte*bytes=(*env)->GetByteArrayElements(env,target,NULL);if(!bytes)return-ENOMEM;
    ssize_t n;do{n=read(atomic_load(&p->master),bytes,(size_t)size);}while(n<0&&errno==EINTR);
    int error=errno;(*env)->ReleaseByteArrayElements(env,target,bytes,n>0?0:JNI_ABORT);
    if(n<0&&(error==EIO||error==EAGAIN))return 0;
    return n<0?-error:(jint)n;
}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_write(JNIEnv*env,jclass type,jlong handle,jbyteArray source,jint length){
    (void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p||!source||atomic_load(&p->closed))return-EINVAL;
    jsize size=(*env)->GetArrayLength(env,source);if(length<0||length>size)return-EINVAL;
    jbyte*b=(*env)->GetByteArrayElements(env,source,NULL);if(!b)return-ENOMEM;
    size_t sent=0;int error=0;while(sent<(size_t)length){ssize_t n=write(atomic_load(&p->master),b+sent,(size_t)length-sent);if(n>0){sent+=(size_t)n;continue;}if(n<0&&errno==EINTR)continue;error=n<0?errno:EIO;break;}
    (*env)->ReleaseByteArrayElements(env,source,b,JNI_ABORT);return error?-error:(jint)sent;
}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_resize(JNIEnv*env,jclass type,jlong handle,jint rows,jint columns,jint width,jint height){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p||atomic_load(&p->closed)||rows<=0||columns<=0)return-EINVAL;struct winsize s={(unsigned short)rows,(unsigned short)columns,(unsigned short)width,(unsigned short)height};return ioctl(atomic_load(&p->master),TIOCSWINSZ,&s)<0?-errno:0;}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_pollExit(JNIEnv*env,jclass type,jlong handle){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p)return-EINVAL;if(p->exit_status>=0)return p->exit_status;int status;pid_t result;do{result=waitpid(p->pid,&status,WNOHANG);}while(result<0&&errno==EINTR);if(result==0)return-1;if(result<0)return-errno;p->exit_status=WIFEXITED(status)?WEXITSTATUS(status):WIFSIGNALED(status)?128+WTERMSIG(status):255;return p->exit_status;}
JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_signal(JNIEnv*env,jclass type,jlong handle,jint signal){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(p&&!atomic_load(&p->closed)&&signal>0)kill(-p->pid,signal);}
JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_close(JNIEnv*env,jclass type,jlong handle){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p||atomic_exchange(&p->closed,1))return;int fd=atomic_exchange(&p->master,-1);if(fd>=0)close(fd);if(p->exit_status<0)kill(-p->pid,SIGHUP);}
JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_destroy(JNIEnv*env,jclass type,jlong handle){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p)return;if(!atomic_exchange(&p->closed,1)){int fd=atomic_exchange(&p->master,-1);if(fd>=0)close(fd);kill(-p->pid,SIGHUP);}while(waitpid(p->pid,NULL,0)<0&&errno==EINTR){}free(p);}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_pid(JNIEnv*env,jclass type,jlong handle){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;return p?(jint)p->pid:-EINVAL;}
