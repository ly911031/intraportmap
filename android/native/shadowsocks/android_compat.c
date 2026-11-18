#include <stdio.h>
#include <stdarg.h>
#include <stdint.h>
#include <android/log.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>

ssize_t ss_getrandom(void *buffer, size_t length, unsigned int flags)
{
    ssize_t result=syscall(__NR_getrandom, buffer, length, flags);
    if(result>=0 || errno!=ENOSYS) return result;
    // Android 5 devices may have kernels predating getrandom(2).
    int fd=open("/dev/urandom",O_RDONLY|O_CLOEXEC);
    if(fd<0) return -1;
    do { result=read(fd,buffer,length); } while(result<0 && errno==EINTR);
    int saved=errno; close(fd); errno=saved;
    return result;
}

// This is ss-server, not a VPN client. No socket-protection broker is needed.
int vpn = 0;
int protect_socket(int fd) { (void)fd; return 0; }
int send_traffic_stat(uint64_t tx, uint64_t rx) { (void)tx; (void)rx; return 0; }

// Upstream LOGI maps to Android DEBUG. Route normal/non-verbose output to the
// supervised pipe instead, so both services use the same bounded file logger.
int ss_file_log(int priority, const char *tag, const char *format, ...)
{
    (void)tag;
    fprintf(stderr, "%s ", priority >= ANDROID_LOG_ERROR ? "ERROR" : "INFO");
    va_list args;
    va_start(args, format);
    int result = vfprintf(stderr, format, args);
    va_end(args);
    fputc('\n', stderr);
    fflush(stderr);
    return result;
}
