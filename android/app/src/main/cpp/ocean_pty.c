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
static _Atomic int fatal_log_fd=-1;
static _Atomic int native_log_fd=-1;
static _Atomic int fatal_stage=0;

static void breadcrumb(int fd,const char *message,size_t length) {
    if(fd<0)return;
    size_t written=0;
    while(written<length){ssize_t n=write(fd,message+written,length-written);if(n>0){written+=(size_t)n;continue;}if(n<0&&errno==EINTR)continue;break;}
    (void)fsync(fd);
}
#define CRUMB(fd,literal) breadcrumb((fd),(literal),sizeof(literal)-1)
static void fatal_handler(int signal_number) {
    int fd=atomic_load(&fatal_log_fd);char marker[]="[FATAL] signal=00 stage=000\n";
    marker[15]=(char)('0'+(signal_number/10)%10);marker[16]=(char)('0'+signal_number%10);
    int stage=atomic_load(&fatal_stage);marker[24]=(char)('0'+(stage/100)%10);marker[25]=(char)('0'+(stage/10)%10);marker[26]=(char)('0'+stage%10);
    if(fd>=0)(void)write(fd,marker,sizeof(marker)-1);
    signal(signal_number,SIG_DFL);raise(signal_number);
}
static void install_fatal_handlers(int fd){atomic_store(&fatal_log_fd,fd);int signals[]={SIGSEGV,SIGABRT,SIGBUS,SIGILL,SIGFPE};struct sigaction action;memset(&action,0,sizeof(action));action.sa_handler=fatal_handler;sigemptyset(&action.sa_mask);action.sa_flags=SA_RESETHAND;for(size_t i=0;i<sizeof(signals)/sizeof(signals[0]);i++)sigaction(signals[i],&action,NULL);}

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
    int master=-1,slave=-1,diagnostic_fd=open(diagnostic,O_WRONLY|O_CREAT|O_APPEND|O_CLOEXEC,0600);char marker_path[4096];size_t diagnostic_length=strlen(diagnostic);int marker_fd=-1;if(diagnostic_length<sizeof(marker_path)){memcpy(marker_path,diagnostic,diagnostic_length+1);char *slash=strrchr(marker_path,'/');if(slash){memcpy(slash+1,"native-crash-marker.log",24);marker_fd=open(marker_path,O_WRONLY|O_CREAT|O_APPEND|O_CLOEXEC,0600);}}
    struct winsize size={(unsigned short)rows,(unsigned short)columns,0,0};
    atomic_store(&native_log_fd,diagnostic_fd);install_fatal_handlers(marker_fd);atomic_store(&fatal_stage,1);CRUMB(diagnostic_fd,"[N001] nativeCreate entered\n");
    if(!argv||!envp)goto failure;
    atomic_store(&fatal_stage,2);CRUMB(diagnostic_fd,"[N002] arguments validated\n[N003] shell path copied\n[N004] argv prepared\n[N005] envp prepared\n[N006] cwd prepared\n[N010] openpty BEGIN\n");
    if(openpty(&master,&slave,NULL,NULL,&size)<0){ocean_last_errno=errno;goto failure;}
    atomic_store(&fatal_stage,11);CRUMB(diagnostic_fd,"[N011] openpty END rc=0 errno=0; master/slave ownership created\n[N020] fork BEGIN\n");
    pid_t pid=fork(); if(pid<0){ocean_last_errno=errno;goto failure;}
    if(pid==0){
        atomic_store(&fatal_stage,21);CRUMB(diagnostic_fd,"[N021-CHILD] child execution begins\n[N038] inherited fd cleanup BEGIN: close master\n");
        close(master);
        CRUMB(diagnostic_fd,"[N038] inherited fd cleanup END: master closed\n[N030] setsid BEGIN\n");
        if(setsid()<0){child_exec_error(errno);_exit(126);}
        CRUMB(diagnostic_fd,"[N031] setsid END rc=0 errno=0\n[N032] ioctl TIOCSCTTY BEGIN\n");
        if(ioctl(slave,TIOCSCTTY,0)<0){child_exec_error(errno);_exit(126);}
        CRUMB(diagnostic_fd,"[N033] ioctl TIOCSCTTY END rc=0 errno=0\n[N034] dup2 stdin BEGIN\n");
        if(dup2(slave,0)<0){child_exec_error(errno);_exit(126);}
        CRUMB(diagnostic_fd,"[N034] dup2 stdin END\n[N035] dup2 stdout BEGIN\n");
        if(dup2(slave,1)<0){child_exec_error(errno);_exit(126);}
        CRUMB(diagnostic_fd,"[N035] dup2 stdout END\n[N036] dup2 stderr BEGIN\n");
        if(dup2(slave,2)<0){child_exec_error(errno);_exit(126);}
        CRUMB(diagnostic_fd,"[N036] dup2 stderr END\n[N038] inherited fd cleanup BEGIN: close slave\n");if(slave>2)close(slave);CRUMB(diagnostic_fd,"[N038] inherited fd cleanup END: slave closed\n[N037] chdir BEGIN\n");
        if(chdir(directory)<0){child_exec_error(errno);_exit(126);}
        CRUMB(diagnostic_fd,"[N037] chdir END rc=0 errno=0\n[N039] execve BEGIN\n");
        execve(exe,argv,envp); int error=errno; CRUMB(diagnostic_fd,"[N040] execve FAILED (errno emitted to PTY)\n");child_exec_error(error); _exit(error==ENOENT?127:126);
    }
    atomic_store(&fatal_stage,21);CRUMB(diagnostic_fd,"[N021-PARENT] fork returned child pid\n[N050] slave close BEGIN owner=parent\n");
    close(slave); slave=-1;CRUMB(diagnostic_fd,"[N050] slave close END\n");
    if(fcntl(master,F_SETFD,FD_CLOEXEC)<0){ocean_last_errno=errno;kill(pid,SIGKILL);close(master);master=-1;goto failure;}
    ocean_pty *pty=calloc(1,sizeof(*pty));
    if(!pty){ocean_last_errno=ENOMEM;kill(pid,SIGKILL);close(master);master=-1;goto failure;}
    pty->master=master;pty->pid=pid;pty->exit_status=-1;
    atomic_store(&fatal_stage,51);CRUMB(diagnostic_fd,"[N051] session native object constructed; master owner=native-session\n[N052] session registered return-to-Java\n");
    /* Keep the append descriptor open so a fatal signal can persist its marker. */
    release_strings(argv);release_strings(envp);(*env)->ReleaseStringUTFChars(env,executable,exe);(*env)->ReleaseStringUTFChars(env,cwd,directory);(*env)->ReleaseStringUTFChars(env,diagnostic_path,diagnostic);return (jlong)(intptr_t)pty;
failure:
    if(master>=0)close(master);
    if(slave>=0)close(slave);
    release_strings(argv);release_strings(envp);
    CRUMB(diagnostic_fd,"[NXXX] nativeCreate failed\n");if(diagnostic_fd>=0)close(diagnostic_fd);
    (*env)->ReleaseStringUTFChars(env,executable,exe);(*env)->ReleaseStringUTFChars(env,cwd,directory);(*env)->ReleaseStringUTFChars(env,diagnostic_path,diagnostic);return 0;
}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_lastErrno(JNIEnv*e,jclass t){(void)e;(void)t;return ocean_last_errno;}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_read(JNIEnv*env,jclass type,jlong handle,jbyteArray target){
    (void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p||!target||atomic_load(&p->closed))return-EINVAL;
    jsize size=(*env)->GetArrayLength(env,target);if(size<=0)return-EINVAL;
    jbyte*bytes=(*env)->GetByteArrayElements(env,target,NULL);if(!bytes)return-ENOMEM;
    int logfd=atomic_load(&native_log_fd);atomic_store(&fatal_stage,55);CRUMB(logfd,"[N055] first/read BEGIN\n");ssize_t n;do{n=read(atomic_load(&p->master),bytes,(size_t)size);}while(n<0&&errno==EINTR);CRUMB(logfd,"[N056] read END (result returned to Java)\n");
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
JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_close(JNIEnv*env,jclass type,jlong handle){(void)env;(void)type;int logfd=atomic_load(&native_log_fd);ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p){CRUMB(logfd,"[FD] close ignored null handle\n");return;}if(atomic_exchange(&p->closed,1)){CRUMB(logfd,"[FD] double close ignored\n");return;}CRUMB(logfd,"[FD] master close requested\n");int fd=atomic_exchange(&p->master,-1);if(fd>=0)close(fd);CRUMB(logfd,"[FD] master close completed\n");if(p->exit_status<0)kill(-p->pid,SIGHUP);}
JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_destroy(JNIEnv*env,jclass type,jlong handle){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p)return;if(!atomic_exchange(&p->closed,1)){int fd=atomic_exchange(&p->master,-1);if(fd>=0)close(fd);kill(-p->pid,SIGHUP);}while(waitpid(p->pid,NULL,0)<0&&errno==EINTR){}free(p);}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_pid(JNIEnv*env,jclass type,jlong handle){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;return p?(jint)p->pid:-EINVAL;}
