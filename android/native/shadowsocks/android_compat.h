#ifndef SS_ANDROID_COMPAT_H
#define SS_ANDROID_COMPAT_H
#include <sys/types.h>
#include <stddef.h>
// libc getrandom was exposed at API 28; the kernel syscall also supports API 26.
ssize_t ss_getrandom(void *buffer, size_t length, unsigned int flags);
#define getrandom ss_getrandom
#endif
