# Skunk Guide

@@@index

* [Tutorial](tutorial/Index.md)
* [Reference](reference/Index.md)

@@@

- Skunk is a Postgres library for Scala.
- Skunk is powered by [cats](), [cats-effect](), [scodec]() and [fs2]().
- Skunk is functional.
- Skunk is nonblocking.
- Skunk is written in tagless-final style.
- Skunk does not use inductive/implicit codecs.
- Skunk gives very good error messages.
- Skunk embraces the [Scala Code of Conduct](http://scala-lang.org/conduct.html).


____

@@dependency[sbt,Maven,Gradle] {
  group="$org$"
  artifact="$core-name$_2.12"
  version="$version$"
}

@@@ warning

Skunk is under active development and is **pre-release software**. We welcome experimentation and contributions and bug reports, but please don't use it for anything important yet.

@@@

## Table of Contents

@@toc