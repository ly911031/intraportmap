/* Test-only local echo and TCP-framed UDP bridge for headless adb testing. */
#include <arpa/inet.h>
#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <unistd.h>

static int transfer(int fd, void *buffer, size_t size, int writing)
{
    char *p=buffer;
    while(size) {
        ssize_t n=writing ? write(fd,p,size) : read(fd,p,size);
        if(n<0 && errno==EINTR) continue;
        if(n<=0) return -1;
        p+=n; size-=(size_t)n;
    }
    return 0;
}
static struct sockaddr_in address(int port)
{
    struct sockaddr_in a={0}; a.sin_family=AF_INET; a.sin_addr.s_addr=htonl(INADDR_LOOPBACK); a.sin_port=htons(port); return a;
}
int main(void)
{
    prctl(PR_SET_PDEATHSIG,SIGTERM); signal(SIGPIPE,SIG_IGN);
    int listener=socket(AF_INET,SOCK_STREAM,0), echo=socket(AF_INET,SOCK_DGRAM,0), client=socket(AF_INET,SOCK_DGRAM,0);
    int yes=1, connection=-1;
    setsockopt(listener,SOL_SOCKET,SO_REUSEADDR,&yes,sizeof(yes));
    struct sockaddr_in bridge=address(32182), target=address(32190), local=address(32181);
    if(bind(listener,(struct sockaddr*)&bridge,sizeof(bridge)) || listen(listener,4) || bind(echo,(struct sockaddr*)&local,sizeof(local))) return 1;
    puts("READY"); fflush(stdout);
    unsigned char buffer[65535];
    for(;;) {
        struct pollfd fds[4]={{listener,POLLIN,0},{echo,POLLIN,0},{client,POLLIN,0},{connection,POLLIN,0}};
        if(poll(fds,4,-1)<0) { if(errno==EINTR) continue; return 2; }
        if(fds[0].revents & POLLIN) {
            int fd=accept(listener,0,0);
            if(connection>=0) close(connection);
            connection=fd;
        }
        if(fds[1].revents & POLLIN) {
            struct sockaddr_in from; socklen_t length=sizeof(from);
            ssize_t size=recvfrom(echo,buffer,sizeof(buffer),0,(struct sockaddr*)&from,&length);
            if(size>=0) sendto(echo,buffer,(size_t)size,0,(struct sockaddr*)&from,length);
        }
        if(fds[2].revents & POLLIN) {
            ssize_t size=recv(client,buffer,sizeof(buffer),0);
            uint32_t length=htonl((uint32_t)size);
            if(size>=0 && connection>=0 && (transfer(connection,&length,4,1) || transfer(connection,buffer,(size_t)size,1))) {
                close(connection); connection=-1;
            }
        }
        if(connection==fds[3].fd && (fds[3].revents & (POLLIN|POLLHUP|POLLERR))) {
            uint32_t length;
            if(transfer(connection,&length,4,0)) { close(connection); connection=-1; continue; }
            size_t size=ntohl(length);
            if(size>sizeof(buffer) || transfer(connection,buffer,size,0)) { close(connection); connection=-1; continue; }
            sendto(client,buffer,size,0,(struct sockaddr*)&target,sizeof(target));
        }
    }
}
