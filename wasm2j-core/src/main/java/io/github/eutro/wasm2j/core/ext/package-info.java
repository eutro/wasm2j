/**
 * The ext API allows for associating arbitrary data with
 * instances of {@link io.github.eutro.wasm2j.core.ext.ExtContainer}.
 *
 * <pre>{@code
 * class Person extends ExtHolder { ... }
 *
 * class PersonExts {
 *   public static final Ext<String> NAME = Ext.create(String.class, "name");
 *   public static final Ext<Integer> AGE = Ext.create(Integer.class, "age");
 * }
 *
 * Person person = new Person();
 * person.attachExt(AGE, 33);
 * person.attachExt(NAME, "Jane");
 *
 * person.getExtOrThrow(AGE); // => 33
 * person.getExtOrThrow(NAME); // => "Jane"
 * }</pre>
 * <p>
 * This is useful for many algorithms which require some extra scratch data
 * on each element of a structure, that they then discard after. It is also
 * useful for extensibility, since it allows for associating data (fairly) efficiently
 * without having to change the code, or having to resort to ad-hoc {@link Map}s and passing them around.
 * <p>
 * It is further useful in the presence of the decorator pattern, where
 * some implementations may "wrap" others, obscuring the class of the actual implementation,
 * or when a class would like to delegate to one of its constituent components if it doesn't
 * itself contain a given ext.
 * <p>
 * Specialised implementations of {@link io.github.eutro.wasm2j.core.ext.ExtContainer}
 * may implement fast-paths for certain {@link io.github.eutro.wasm2j.core.ext.Ext}s
 * by storing them directly in fields of the class.
 */
package io.github.eutro.wasm2j.core.ext;

import java.util.Map;
