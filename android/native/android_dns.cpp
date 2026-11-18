#include <event2/util.h>
#include <event2/dns.h>

#undef evdns_base_new
extern "C" struct evdns_base *evdns_base_new(struct event_base *, int);
extern "C" struct evdns_base *ipm_android_dns_base_new(struct event_base *base, int)
{
    // No resolv.conf is present on Android; the system resolver below handles DNS.
    return evdns_base_new(base, 0);
}

// Android has no resolv.conf. Resolve through Bionic/netd so hostnames use
// the device's current network, VPN and private DNS settings on every retry.
// Like libevent's numeric-address fast path, this completes synchronously.
extern "C" struct evdns_getaddrinfo_request *ipm_android_getaddrinfo(
    struct evdns_base *, const char *node, const char *service,
    const struct evutil_addrinfo *hints, evdns_getaddrinfo_cb callback, void *arg)
{
    struct evutil_addrinfo *result = NULL;
    int error = evutil_getaddrinfo(node, service, hints, &result);
    callback(error, result, arg);
    return NULL;
}
