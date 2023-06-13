# Contributing Guide

## Testing

Ensure you are not running a local postgres server (such as the Postgres app for OSX).

Start up / tear down the local network & containers:

```bash
> export SERVER_KEY=$(cat world/server.key)
> export SERVER_CERT=$(cat world/server.crt)
> docker-compose up
```
If the above stops working, check the [Github action](https://github.com/typelevel/skunk/blob/main/.github/workflows/ci.yml) for what runs during CI.

Then to run all the tests, in a separate window:

```bash
> sbt test
```

To run a specific test:

```bash
> sbt
> test:testOnly tests.simulation.StartupSimTest
```
