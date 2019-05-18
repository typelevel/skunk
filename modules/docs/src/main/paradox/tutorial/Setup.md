# Setup

Welcome to the Wonderful World of Skunk! This section will help you get everything set up.

## Database Setup

In order to run the tutorial examples you will need a Postgres server with the `world` database loaded up, writable by the `postgres` user, who must be able to log in without a password. Our [Docker](http://docker.com) image does exactly that.

```
docker run -p5432:5432 -d tpolecat/skunk-world
```

If you wish to use your own Postgres server you can download `world/world.sql` from the Skunk repository and load it up yourself.

## Scala Setup

Create a new project with Skunk as a dependency.

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

## Experiment

Here are some modifications that will cause runtime failures. Give them a try and see how Skunk responds.

- Introduce a typo into the SQL string.
- Change the decoder from `date` to another type like `timestamp`.

We will see more examples later in the tutorial.

