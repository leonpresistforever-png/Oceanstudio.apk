#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <pty.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

typedef struct { int master; pid_t pid; int exit_status; } ocean_pty;

static char **strings(JNIEnv *env, jobjectArray source) {
    jsize count = source ? (*env)->GetArrayLength(env, source) : 0;
    char **result = calloc((size_t)count + 1, sizeof(char *));
    if (!result) return NULL;
    for (jsize i=0;i<count;i++) { jstring value=(jstring)(*env)->GetObjectArrayElement(env,source,i); const char *utf=(*env)->GetStringUTFChars(env,value,NULL); result[i]=strdup(utf); (*env)->ReleaseStringUTFChars(env,value,utf); (*env)->DeleteLocalRef(env,value); }
    return result;
}
static void release_strings(char **values) { if (!values) return; for(char **p=values;*p;p++) free(*p); free(values); }

JNIEXPORT jlong JNICALL Java_studio_ocean_app_terminal_NativePty_create(JNIEnv *env,jclass type,jstring executable,jobjectArray arguments,jobjectArray environment,jstring cwd,jint rows,jint columns) {
    (void)type; const char *exe=(*env)->GetStringUTFChars(env,executable,NULL); const char *directory=(*env)->GetStringUTFChars(env,cwd,NULL);
    char **argv=strings(env,arguments), **envp=strings(env,environment); int master=-1,slave=-1; struct winsize size={(unsigned short)rows,(unsigned short)columns,0,0};
    if(!argv||!envp||openpty(&master,&slave,NULL,NULL,&size)<0) goto failure;
    pid_t pid=fork(); if(pid<0) goto failure;
    if(pid==0){ close(master); if(setsid()<0)_exit(126); if(ioctl(slave,TIOCSCTTY,0)<0)_exit(126); if(dup2(slave,STDIN_FILENO)<0||dup2(slave,STDOUT_FILENO)<0||dup2(slave,STDERR_FILENO)<0)_exit(126); if(slave>2)close(slave); if(chdir(directory)<0)_exit(126); execve(exe,argv,envp); _exit(errno==ENOENT?127:126); }
    close(slave); fcntl(master,F_SETFD,FD_CLOEXEC); ocean_pty *pty=calloc(1,sizeof(*pty)); if(!pty){kill(pid,SIGKILL);close(master);goto failure;} pty->master=master;pty->pid=pid;pty->exit_status=-1;
    release_strings(argv);release_strings(envp);(*env)->ReleaseStringUTFChars(env,executable,exe);(*env)->ReleaseStringUTFChars(env,cwd,directory);return (jlong)(intptr_t)pty;
failure: if(master>=0)close(master);if(slave>=0)close(slave);release_strings(argv);release_strings(envp);(*env)->ReleaseStringUTFChars(env,executable,exe);(*env)->ReleaseStringUTFChars(env,cwd,directory);return 0;
}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_read(JNIEnv *env,jclass type,jlong handle,jbyteArray target){(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p)return-EINVAL;jsize size=(*env)->GetArrayLength(env,target);jbyte*bytes=(*env)->GetByteArrayElements(env,target,NULL);ssize_t n=read(p->master,bytes,(size_t)size);if(n>0)(*env)->ReleaseByteArrayElements(env,target,bytes,0);else(*env)->ReleaseByteArrayElements(env,target,bytes,JNI_ABORT);return n<0?-errno:(jint)n;}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_write(JNIEnv *env,jclass type,jlong handle,jbyteArray source,jint length){(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p)return-EINVAL;jbyte*b=(*env)->GetByteArrayElements(env,source,NULL);ssize_t n=write(p->master,b,(size_t)length);(*env)->ReleaseByteArrayElements(env,source,b,JNI_ABORT);return n<0?-errno:(jint)n;}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_resize(JNIEnv*env,jclass type,jlong handle,jint rows,jint columns,jint width,jint height){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p)return-EINVAL;struct winsize s={(unsigned short)rows,(unsigned short)columns,(unsigned short)width,(unsigned short)height};return ioctl(p->master,TIOCSWINSZ,&s)<0?-errno:0;}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_pollExit(JNIEnv*env,jclass type,jlong handle){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p)return-EINVAL;if(p->exit_status>=0)return p->exit_status;int status;pid_t result=waitpid(p->pid,&status,WNOHANG);if(result==0)return-1;if(result<0)return-errno;p->exit_status=WIFEXITED(status)?WEXITSTATUS(status):WIFSIGNALED(status)?128+WTERMSIG(status):255;return p->exit_status;}
JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_signal(JNIEnv*env,jclass type,jlong handle,jint signal){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(p)kill(-p->pid,signal);}
JNIEXPORT void JNICALL Java_studio_ocean_app_terminal_NativePty_close(JNIEnv*env,jclass type,jlong handle){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;if(!p)return;close(p->master);if(p->exit_status<0){kill(-p->pid,SIGHUP);while(waitpid(p->pid,NULL,0)<0&&errno==EINTR){}}free(p);}
JNIEXPORT jint JNICALL Java_studio_ocean_app_terminal_NativePty_pid(JNIEnv*env,jclass type,jlong handle){(void)env;(void)type;ocean_pty*p=(ocean_pty*)(intptr_t)handle;return p?(jint)p->pid:-EINVAL;}
