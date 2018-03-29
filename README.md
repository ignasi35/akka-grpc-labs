# akka-grpc-labs

Review some client alternatives to introduce service discovery on the Akka gRPC client.


## Problem

Internal implementation of Akka-gRPC (netty based) reuses a single `Channel` which is built for a given `host:port` 
and further requests are _just_ a relative path. This differs from traditional HTTP clients where each request 
usually includes an absolute URI.

Adding Service Discovery on Akka-gRPC requires going back to the `ClientFactory` approach of traditional HTTP clients
so that each new request may be balanced to a different `host:port`. At same time we still want the user-facing 
interface to be a 100% gRPC like.

This repo investigates a couple of options:

 * `channel-per-call`: every new call creates a new `Channel` instance and destroys it on completion
 * `channel-pool`: every call obtains a `Channel` from a pool (may add load balance) and releases it

TODO: investigate issues when pooling `Channel` instances that are used for long-lived streaming calls.

