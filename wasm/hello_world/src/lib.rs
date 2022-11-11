mod io;
use std::io::Write;

#[no_mangle]
pub fn main() {
    io::println!("Hello, world!");
}
