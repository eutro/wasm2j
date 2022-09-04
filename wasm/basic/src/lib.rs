#[no_mangle]
pub extern fn add(a: i32, b: i32) -> i32 {
    a + b
}

#[no_mangle]
pub extern fn mul(a: u32, b: u32) -> u32 {
    a * b
}

