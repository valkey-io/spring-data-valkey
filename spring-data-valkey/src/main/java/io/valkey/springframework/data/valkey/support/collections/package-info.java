/**
 * Package providing implementations for most of the {@code java.util} collections on top of Valkey.
 * <p>
 * For indexed collections, such as {@link java.util.List}, {@link java.util.Queue} or {@link java.util.Deque} consider
 * {@link io.valkey.springframework.data.valkey.support.collections.ValkeyList}.
 * <p>
 * For collections without duplicates the obvious candidate is
 * {@link io.valkey.springframework.data.valkey.support.collections.ValkeySet}. Use
 * {@link io.valkey.springframework.data.valkey.support.collections.ValkeyZSet} if a certain order is required.
 * <p>
 * Lastly, for key/value associations {@link io.valkey.springframework.data.valkey.support.collections.ValkeyMap} providing a
 * Map-like abstraction on top of a Valkey hash.
 */
@org.springframework.lang.NonNullApi
@org.springframework.lang.NonNullFields
package io.valkey.springframework.data.valkey.support.collections;
