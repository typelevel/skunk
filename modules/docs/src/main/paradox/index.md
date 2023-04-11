# Skunk

## Overview

- Skunk is a [Postgres](https://www.postgresql.org) library for [Scala](https://www.scala-lang.org).
- Skunk is powered by [cats](http://typelevel.org/cats/), [cats-effect](https://typelevel.org/cats-effect/), [scodec](http://scodec.org), and [fs2](https://fs2.io).
- Skunk is purely functional, non-blocking, and provides a tagless-final API.
- Skunk gives very good error messages.
- Skunk embraces the [Scala Code of Conduct](http://scala-lang.org/conduct.html).
- **Skunk is pre-release software!** Code and documentation are under active development!

Skunk is published for Scala 2.12, 2.13, and 3 on JVM, Node.js, and Native. It can be included in your project thus:

```scala
libraryDependencies += "org.tpolecat" %% "skunk-core" % "@VERSION@"
```

## How to Learn

Prerequisites:

- We assume you are comfortable with Postgres.
- We assume you are comfortable with [cats](http://typelevel.org/cats/), [cats-effect](https://typelevel.org/cats-effect/), and [fs2](https://fs2.io).
- If not, give it a try anyway! If you run into trouble the linked websites have many learning resources.

To learn about Skunk:

- Read the [Tutorial](tutorial/Index.md).
- Peruse the [Reference](tutorial/Index.md) material.

Other resources:

- Come chat on [Discord](https://sca.la/typeleveldiscord).
- Skunk is featured in the book [*Practical FP in Scala*](https://leanpub.com/pfp-scala) by [Gabriel Volpe](https://twitter.com/volpegabriel87).
- [tpolecat](https://twitter.com/tpolecat) did a [talk](https://www.youtube.com/watch?v=NJrgj1vQeAI) on Skunk's architecture at Scala Days 2019 at EPFL.

Reference documentation:

- Skunk [API Documentation](https://javadoc.io/doc/org.tpolecat/skunk-core_$scala.binary.version$/$version$/index.html).
- [Postgres](https://www.postgresql.org/docs/) documentation.

## How to Contribute

- Test it out!
- If you see a typo in the doc, click the link at the bottom and fix it!
- If you find a bug, open an issue (or fix it and open a PR) at our [GitHub Repository](https://github.com/tpolecat/skunk).
- If you want to make a larger contribution please open an issue first so we can discuss.
