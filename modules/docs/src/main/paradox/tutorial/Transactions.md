# Transactions

...intro to transactions


@@@ warning

There is never more than one transaction in progress on a given session, and all operations on that session during the transaction's lifespan will take part in the transaction. **It is recommended that sessions not be used concurrently in the presence of transactions**.

@@@

See [§3.4](https://www.postgresql.org/docs/10/tutorial-transactions.html) in the PostgreSQL documentation for an introduction to transactions. The text that follows assumes you have a working knowledge of the information in that section.

## A Simple Transaction

... code example

### Transaction Finalization

If the `use` block exits normally there are three cases to consider, based on the session's transaction status on exit.

- **Active** means all went well and the transaction will be committed. This is the typical success case.
- **Error** means the user encountered and handled an error, but left the transaction in a failed state. In this case the transaction will be rolled back.
- **Idle** means the user terminated the transaction explicitly inside the block and there is nothing to be done.

If the block exits due to cancellation or an error and the session transaction status is **Active** or **Error** (the common failure case) then the transaction will be rolled back and any error will be re-raised.

Transaction finalization is summarized in the following matrix.

| Status | Normal Exit | Cancellation | Error Raised |
|--------------------|-------------|--------------|------|
| **Idle**   | — | — | re-raise |
| **Active** | commit | roll back | roll back, re-raise |
| **Error**  | roll back | roll back | roll back, re-raise |

## Savepoints

talk about savepoints

### A Retry Combinator

## Full Example

## Experiment

- PostgreSQL does not permit nested transactions. What happens if you attempt this?