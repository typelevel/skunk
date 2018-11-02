
TODO:

- Send a `Sync` immediately following any `ErrorResponse` so we can get straight with the back end and ensure that our `TransactionStatus` is always current.
- Create constants for session parameter keys (low-priority).

Tests for Session indirectly test all the deeper plumbing.

- transactionStatus
  - should be idle initially
  - should be idle following an successful simple query
  - shuold be idle following an errored simple query
  - should be active following BEGIN
  - should be idle following ROLLBACK
  - should be idle following COMMIT
  - should be errored following an error within a transaction

- parameters
  - should have all spec'd keys defined post-startup
  - should respond to an encoding change

- parameter
  - should respond to distinct encoding changes

