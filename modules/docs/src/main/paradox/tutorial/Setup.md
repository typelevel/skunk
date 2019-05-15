# Setup

Welcome to the Wonderful World of Skunk! This section will help you get everything set up.

## Database Setup

In order to run the tutorial examples you will need a Postgres server with the `world` database loaded up, writable by the `postgres` user, who must be able to log in without a password.

The easiest option is to use our [Docker](http://docker.com) image, which exposes a pre-populated instance of Postgres $postgres-version$ on port $postgres-port$.

```
docker run -d tpolecat/skunk-tutorial
```

Or, if you wish to use your own Postgres server you can download the `world.sql` file from the Skunk repository and install it yourself. As noted above you will need a `postgres` user who can log in with no password.

```
psql -c 'create database world;' -U postgres
psql -c '\i world.sql' -d world -U postgres
```

## Scala Setup

You'll need to add Skunk as a project dependency.

@@dependency[sbt,Maven,Gradle] {
  group="$org$"
  artifact="$core-name$_2.12"
  version="$version$"
}

## Verify Your Setup

Try out this minimal [IOApp](https://typelevel.org/cats-effect/datatypes/ioapp.html) that connects to the database and selects the current date.

@@snip [Setup.scala](/modules/docs/src/main/scala/tutorial/Setup.scala) { #hello }

Let's examine the code above.

- At ① we define a [Resource](https://typelevel.org/cats-effect/datatypes/resource.html)  that yields un-pooled @ref:[Session](../reference/Sessions.md) values and ensures that they are closed after use. We specify the host, port, user, and database.

@@@ note
Skunk does not support authenticated (or encrypted) connections yet. You must connect with a user who can log in without a password.
@@@

- At ② we `use` the resource, specifying a block to execute during the `Session`'s lifetime. No matter how the block terminates (success, failure, cancellation) the `Session` will be closed properly.
- At ③ we use the @ref:[sql interpolator](../reference/Fragments.md) to construct a `Query` that selects a single column of schema type `date` (which maps to JDK type @javadoc[LocalDate](java.time.LocalDate)), then we ask the session to execute it, expecting a *unique* value back; i.e., exactly one row.

When we run the program we will see the current date.

```
The current date is 2019-05-11.
```