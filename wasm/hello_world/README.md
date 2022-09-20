# AOC 2019

These are my solutions to [Advent of Code 2019](https://adventofcode.com/2019), written in [Rust](https://www.rust-lang.org/).

## Building

To build you will need [Cargo](https://doc.rust-lang.org/cargo/).
The easiest way to install this is to use [rustup](https://rustup.rs/).

To just build, run `cargo build`, or `cargo build --release` for optimizations.
These will produce an executable binary for each day in `./target/debug/` or `./target/release/` respectively.

Alternatively, you can just use `cargo run --bin dayXX` to run a given day `XX`.

Note that each day takes input from `stdin`.

For example, to run day 1 on the input, run:

`cat input/1.txt | cargo run --bin day01`
