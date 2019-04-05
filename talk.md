## Things that bug me about doobie

### JDBC is awful

  - 20-year old Java
    - lots of casting
    - lots of exceptions
    - very primitive
  - blocking
    - in doobie we need three execution contexts to deal with this
  - layer of fog in between you and the database
    - type mappings are vague, you have to experiment
    - dealing with dates and times is particularly hard
    - if your database doesn't support a feature it just throws an exception!
    - puppet strings
  - and the only database I care about is Postgres
    - for weird databases JDBC is fine but most of the time I'm using Postgres and I don't want to pay the weird leaky abstraction tax.

### The implicit codec pattern is in fact an antipattern
- We end up doing a ton of newtyping just to convince the derivation machinery to derive the instance we want.
- It's not much harder to just construct the instance as a piece of data.

### Bad diagnostics

When something goes wrong it's really hard to figure out what happened, which is something we could improve with doobie but ultimately we're limited by the information JDBC provides, which is really minimal and depends a lot on what the driver decides to give you.

### Not fun

Doobie works but it's not a lot of fun to use and not a lot of fun to work on because ultimately JDBC is cramping my style. It's a least-common-denominator API and ultimately there's no way to sweep it completely under the rug.

## Skunk

Ok so that leads to this new thing I have been working on called Skunk

- Specifically for Postgres
  - Speaks the Postgres wire protocol
  - API works in terms of schema types, and we'll see more of that later
  - Completely nonblocking
- Purely functional
  - of course
  - Built on cats, cats-effect, scodec, and fs2
- Experimental
  - Don't use it
  - API is changing very quickly
  - [show repo, show scaladoc]

## Demo

So I'm just going to show you code.

- Minimal1
