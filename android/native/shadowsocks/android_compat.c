#include <stdio.h>
#include <stdarg.h>
#include <stdint.h>
#include <android/log.h>
#include <sys/syscall.h>
#include <unistd.h>

ssize_t ss_getrandom(void *buffer, size_t length, unsigned int flags)
{
    return syscall(__NR_getrandom, buffer, length, flags);
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
