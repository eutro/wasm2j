#[link(wasm_import_module = "java:java.lang.Math")]
extern {
    fn sin(a: f64) -> f64;
    fn cos(a: f64) -> f64;
    fn tan(a: f64) -> f64;
}

#[no_mangle]
unsafe extern "C" fn csc(a: f64) -> f64 {
    1f64 / sin(a)
}

#[no_mangle]
unsafe extern "C" fn sec(a: f64) -> f64 {
    1f64 / cos(a)
}

#[no_mangle]
unsafe extern "C" fn cot(a: f64) -> f64 {
    1f64 / tan(a)
}

#[allow(dead_code)]
#[repr(C)]
enum Func {
    Sin,
    Cos,
    Tan,
    Csc,
    Sec,
    Cot,
}

#[no_mangle]
extern fn func(f: Func) -> unsafe extern "C" fn(f64) -> f64 {
    match f {
        Func::Sin => sin,
        Func::Cos => cos,
        Func::Tan => tan,
        Func::Csc => csc,
        Func::Sec => sec,
        Func::Cot => cot,
    }
}

#[no_mangle]
unsafe extern fn invoke(f: unsafe extern "C" fn(f64) -> f64, d: f64) -> f64 {
    f(d)
}
