# Contributing Guide

## Testing

Ensure you are not running a local postgres server (such as the Postgres app for OSX).

Start up / tear down the local network:

```bash
> ./bin/local up
> ./bin/local down
```

Then to run all the tests, in a separate window:

```bash
> sbt test
```

To run a specific test:

```bash
> sbt
> test:testOnly tests.simulation.StartupSimTest
```
