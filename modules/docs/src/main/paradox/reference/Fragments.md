# Fragments

## The SQL Interpolator

## Interpolating Parameter Encoders

Note what happens when we interpolate a multi-column encoder.

## Interpolating Literal Strings
`#` should precede literals to pass it as constants.
val tableName = "table"
sql"""
  SELECT column_a, column_b
  FROM #$table
"""

## Composing Fragments

## Nested Fragments

