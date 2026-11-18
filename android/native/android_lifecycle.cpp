#include <sys/prctl.h>
#include <signal.h>
#include <unistd.h>

// Prevent orphan tunnels when Android kills Java without calling onDestroy.
__attribute__((constructor)) static void follow_service_lifetime()
{
    const pid_t parent = getppid();
    if (parent == 1 || prctl(PR_SET_PDEATHSIG, SIGTERM) != 0 || getppid() != parent)
        _exit(1);
}
