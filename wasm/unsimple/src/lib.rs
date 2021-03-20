#[no_mangle]
pub extern fn div(a: i32, b: i32) -> i32 {
    a / b
}

#[no_mangle]
pub extern fn div_u(a: u64, b: u64) -> u64 {
    a / b
}
