use std::marker::PhantomData;

#[link(wasm_import_module = "java")]
extern {
    fn free(a: u32);
}

#[repr(transparent)]
struct JObj<'a, T> {
    value: *const T,
    _lifetime: PhantomData<&'a ()>,
}

#[repr(transparent)]
#[allow(dead_code)]
struct JObjRef<'a, T> {
    value: *const T,
    _lifetime: PhantomData<&'a ()>,
}

impl<'a, T> Drop for JObj<'a, T> {
    fn drop(&mut self) {
        unsafe { free(self.value as u32) };
    }
}

impl<'a, T> JObj<'a, T> {
    fn ptr(&self) -> JObjRef<'a, T> {
        JObjRef { value: self.value, _lifetime: self._lifetime }
    }
}

#[link(wasm_import_module = "java")]
extern {
    #[link_name = "Math/sin(D)D"]
    fn sin(a: f64) -> f64;
    #[link_name = "Math/cos(D)D"]
    fn cos(a: f64) -> f64;
    #[link_name = "Math/tan(D)D"]
    fn tan(a: f64) -> f64;

    #[link_name = "Integer/valueOf(I)Ljava/lang/Integer;"]
    fn int_value_of(a: i32) -> JObj<'static, i32>;
    #[link_name = "Number.intValue()I"]
    fn int_value(a: JObjRef<'_, i32>) -> i32;
}

impl<'a> JObj<'a, i32> {
    fn value(&self) -> i32 {
        unsafe { int_value(self.ptr()) }
    }

    fn boxed(v: i32) -> Self {
        unsafe { int_value_of(v) }
    }
}

#[no_mangle]
extern fn foo() -> JObj<'static, i32> {
    drop(JObj::boxed(2));
    JObj::boxed(100)
}

#[no_mangle]
extern fn bar(v: JObj<'static, i32>) -> i32 {
    v.value()
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
