#[no_mangle]
pub extern fn div(a: i32, b: i32) -> i32 {
    a / b
}

#[no_mangle]
pub extern fn div_u(a: u64, b: u64) -> u64 {
    a / b
}

#[no_mangle]
pub extern fn assemble_longs(f: u64) -> u64 {
    let mut sum = 0;
    for &b in b"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed condimentum nec sem ac malesuada. Proin tincidunt lacus eget massa tincidunt, ac interdum turpis porttitor. Proin quis pretium elit. Maecenas vel enim non libero posuere rutrum. Pellentesque ac fringilla sapien. Cras et interdum odio. Mauris rutrum viverra ipsum, a vulputate nibh lobortis non. Suspendisse vel risus vel risus feugiat tempor. Maecenas blandit, tortor nec facilisis venenatis, eros mi ullamcorper mauris, at lacinia nisi dui in dui. Nulla sit amet elit ante. Suspendisse sed ex felis. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum consequat, nisl non ultrices aliquet, dui odio dignissim nibh, tincidunt ultrices dui mi quis urna. Maecenas tempor accumsan finibus. Etiam laoreet tortor et elit finibus, ut fringilla tellus convallis. Curabitur dignissim tellus eu ligula scelerisque, sed vulputate arcu pulvinar." {
        sum += b as u64 * f;
    }
    sum
}
