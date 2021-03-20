#[no_mangle]
pub extern fn add(a: i32, b: i32) -> i32 {
    a + b
}

#[no_mangle]
pub extern fn mul(a: u32, b: u32) -> u32 {
    a * b
}

#[no_mangle]
pub extern fn mul_longs(a: u64, b: u64) -> u64 {
    a * b
}

#[no_mangle]
pub extern fn mem_stuff(arg: i32) -> i32 {
    let mut a = 0;
    let mut b = 0;

    let mut x = 0;
    while x < arg {
        *if x % 2 == 0 {
            &mut a
        } else {
            &mut b
        } += 10;
        x += 1;
    }
    return a + b * 2;
}

#[link_section = "test"]
pub static SECTION: [u8; 13] = *b"Test section!";
