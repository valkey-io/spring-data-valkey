/*
 * Copyright 2016-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.valkey.springframework.data.valkey.connection.lettuce;

import io.lettuce.core.CopyArgs;
import io.lettuce.core.ExpireArgs;
import io.lettuce.core.ScanStream;
import io.lettuce.core.api.reactive.RedisKeyReactiveCommands;
import io.lettuce.core.protocol.CommandArgs;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;

import io.valkey.springframework.data.valkey.connection.DataType;
import io.valkey.springframework.data.valkey.connection.ExpirationOptions;
import io.valkey.springframework.data.valkey.connection.ReactiveKeyCommands;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection.BooleanResponse;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection.CommandResponse;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection.KeyCommand;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection.MultiValueResponse;
import io.valkey.springframework.data.valkey.connection.ReactiveValkeyConnection.NumericResponse;
import io.valkey.springframework.data.valkey.connection.ValueEncoding;
import io.valkey.springframework.data.valkey.connection.ValueEncoding.ValkeyValueEncoding;
import io.valkey.springframework.data.valkey.core.ScanOptions;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 * @author Dahye Anne Lee
 * @since 2.0
 */
class LettuceReactiveKeyCommands implements ReactiveKeyCommands {

	private final LettuceReactiveValkeyConnection connection;

	/**
	 * Create new {@link LettuceReactiveKeyCommands}.
	 *
	 * @param connection must not be {@literal null}.
	 */
	LettuceReactiveKeyCommands(LettuceReactiveValkeyConnection connection) {

		Assert.notNull(connection, "Connection must not be null");

		this.connection = connection;
	}

	@Override
	public Flux<BooleanResponse<CopyCommand>> copy(Publisher<CopyCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap((command) -> {

			Assert.notNull(command.getKey(), "Key must not be null");

			CopyArgs copyArgs = CopyArgs.Builder.replace(command.isReplace());
			if (command.getDatabase() != null) {
				copyArgs.destinationDb(command.getDatabase());
			}

			return cmd.copy(command.getKey(), command.getTarget(), copyArgs)
					.map((value) -> new BooleanResponse<>(command, value));
		}));
	}

	@Override
	public Flux<BooleanResponse<KeyCommand>> exists(Publisher<KeyCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap((command) -> {

			Assert.notNull(command.getKey(), "Key must not be null");

			return cmd.exists(command.getKey()).map(LettuceConverters.longToBooleanConverter()::convert)
					.map((value) -> new BooleanResponse<>(command, value));
		}));
	}

	@Override
	public Flux<CommandResponse<KeyCommand, DataType>> type(Publisher<KeyCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap(command -> {

			Assert.notNull(command.getKey(), "Key must not be null");

			return cmd.type(command.getKey()).map(LettuceConverters::toDataType)
					.map(respValue -> new CommandResponse<>(command, respValue));
		}));
	}

	@Override
	public Flux<NumericResponse<Collection<ByteBuffer>, Long>> touch(Publisher<Collection<ByteBuffer>> keysCollection) {

		return connection.execute(cmd -> Flux.from(keysCollection).concatMap((keys) -> {

			Assert.notEmpty(keys, "Keys must not be null");

			return cmd.touch(keys.toArray(new ByteBuffer[keys.size()])).map((value) -> new NumericResponse<>(keys, value));
		}));
	}

	@Override
	public Flux<MultiValueResponse<ByteBuffer, ByteBuffer>> keys(Publisher<ByteBuffer> patterns) {

		return connection.execute(cmd -> Flux.from(patterns).concatMap(pattern -> {

			Assert.notNull(pattern, "Pattern must not be null");
			// TODO: stream elements instead of collection
			return cmd.keys(pattern).collectList().map(value -> new MultiValueResponse<>(pattern, value));
		}));
	}

	@Override
	public Flux<ByteBuffer> scan(ScanOptions options) {

		Assert.notNull(options, "ScanOptions must not be null");

		return connection.execute(cmd -> ScanStream.scan(cmd, LettuceConverters.toScanArgs(options)));
	}

	@Override
	public Mono<ByteBuffer> randomKey() {
		return connection.execute(RedisKeyReactiveCommands::randomkey).next();
	}

	@Override
	public Flux<BooleanResponse<RenameCommand>> rename(Publisher<RenameCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap(command -> {

			Assert.notNull(command.getKey(), "Key must not be null");
			Assert.notNull(command.getNewKey(), "New name must not be null");

			return cmd.rename(command.getKey(), command.getNewKey()).map(LettuceConverters::stringToBoolean)
					.map(value -> new BooleanResponse<>(command, value));
		}));
	}

	@Override
	public Flux<BooleanResponse<RenameCommand>> renameNX(Publisher<RenameCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap(command -> {

			Assert.notNull(command.getKey(), "Key must not be null");
			Assert.notNull(command.getNewKey(), "New name must not be null");

			return cmd.renamenx(command.getKey(), command.getNewKey()).map(value -> new BooleanResponse<>(command, value));
		}));
	}

	@Override
	public Flux<NumericResponse<KeyCommand, Long>> del(Publisher<KeyCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap((command) -> {

			Assert.notNull(command.getKey(), "Key must not be null");

			return cmd.del(command.getKey()).map((value) -> new NumericResponse<>(command, value));
		}));
	}

	@Override
	public Flux<NumericResponse<List<ByteBuffer>, Long>> mDel(Publisher<List<ByteBuffer>> keysCollection) {

		return connection.execute(cmd -> Flux.from(keysCollection).concatMap((keys) -> {

			Assert.notEmpty(keys, "Keys must not be null");

			return cmd.del(keys.toArray(new ByteBuffer[keys.size()])).map((value) -> new NumericResponse<>(keys, value));
		}));
	}

	@Override
	public Flux<NumericResponse<KeyCommand, Long>> unlink(Publisher<KeyCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap((command) -> {

			Assert.notNull(command.getKey(), "Key must not be null");

			return cmd.unlink(command.getKey()).map((value) -> new NumericResponse<>(command, value));
		}));
	}

	@Override
	public Flux<NumericResponse<List<ByteBuffer>, Long>> mUnlink(Publisher<List<ByteBuffer>> keysCollection) {

		return connection.execute(cmd -> Flux.from(keysCollection).concatMap((keys) -> {

			Assert.notEmpty(keys, "Keys must not be null");

			return cmd.unlink(keys.toArray(new ByteBuffer[keys.size()])).map((value) -> new NumericResponse<>(keys, value));
		}));
	}

	@Override
	public Flux<BooleanResponse<ExpireCommand>> applyExpiration(Publisher<ExpireCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap(command -> {

			Assert.notNull(command.getKey(), "Key must not be null");

			if (command.getExpiration().isPersistent()) {
				return cmd.persist(command.getKey()).map(value -> new BooleanResponse<>(command, value));
			}

			ExpireArgs args = new ExpireArgs() {

				@Override
				public <K, V> void build(CommandArgs<K, V> args) {
					super.build(args);
					if (ObjectUtils.nullSafeEquals(command.getOptions(), ExpirationOptions.none())) {
						return;
					}

					args.add(command.getOptions().getCondition().name());
				}
			};

			if (command.getExpiration().isUnixTimestamp()) {

				if (command.getExpiration().getTimeUnit().equals(TimeUnit.MILLISECONDS)) {
					return cmd.pexpireat(command.getKey(), command.getExpiration().getExpirationTimeInMilliseconds(), args)
							.map(value -> new BooleanResponse<>(command, value));
				}
				return cmd.expireat(command.getKey(), command.getExpiration().getExpirationTimeInSeconds(), args)
						.map(value -> new BooleanResponse<>(command, value));
			}

			if (command.getExpiration().getTimeUnit().equals(TimeUnit.MILLISECONDS)) {
				return cmd.pexpire(command.getKey(), command.getExpiration().getExpirationTimeInMilliseconds(), args)
						.map(value -> new BooleanResponse<>(command, value));
			}

			return cmd.expire(command.getKey(), command.getExpiration().getExpirationTimeInSeconds(), args)
					.map(value -> new BooleanResponse<>(command, value));
		}));
	}

	@Override
	public Flux<BooleanResponse<ExpireAtCommand>> expireAt(Publisher<ExpireAtCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap(command -> {

			Assert.notNull(command.getKey(), "Key must not be null");
			Assert.notNull(command.getExpireAt(), "Expire at must not be null");

			return cmd.expireat(command.getKey(), command.getExpireAt().getEpochSecond(), getExpireArgs(command.getOptions()))
					.map(value -> new BooleanResponse<>(command, value));
		}));
	}

	@Override
	public Flux<BooleanResponse<ExpireAtCommand>> pExpireAt(Publisher<ExpireAtCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap(command -> {

			Assert.notNull(command.getKey(), "Key must not be null");
			Assert.notNull(command.getExpireAt(), "Expire at must not be null");

			return cmd.pexpireat(command.getKey(), command.getExpireAt().toEpochMilli(), getExpireArgs(command.getOptions()))
					.map(value -> new BooleanResponse<>(command, value));
		}));
	}

	@Override
	public Flux<BooleanResponse<KeyCommand>> persist(Publisher<KeyCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap(command -> {

			Assert.notNull(command.getKey(), "Key must not be null");

			return cmd.persist(command.getKey()).map(value -> new BooleanResponse<>(command, value));
		}));
	}

	@Override
	public Flux<NumericResponse<KeyCommand, Long>> ttl(Publisher<KeyCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap(command -> {

			Assert.notNull(command.getKey(), "Key must not be null");

			return cmd.ttl(command.getKey()).map(value -> new NumericResponse<>(command, value));
		}));
	}

	@Override
	public Flux<NumericResponse<KeyCommand, Long>> pTtl(Publisher<KeyCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap(command -> {

			Assert.notNull(command.getKey(), "Key must not be null");

			return cmd.pttl(command.getKey()).map(value -> new NumericResponse<>(command, value));
		}));
	}

	@Override
	public Flux<BooleanResponse<MoveCommand>> move(Publisher<MoveCommand> commands) {

		return connection.execute(cmd -> Flux.from(commands).concatMap(command -> {

			Assert.notNull(command.getKey(), "Key must not be null");
			Assert.notNull(command.getDatabase(), "Database must not be null");

			return cmd.move(command.getKey(), command.getDatabase()).map(value -> new BooleanResponse<>(command, value));
		}));
	}

	@Override
	public Mono<ValueEncoding> encodingOf(ByteBuffer key) {

		return connection
				.execute(cmd -> cmd.objectEncoding(key).map(ValueEncoding::of).defaultIfEmpty(ValkeyValueEncoding.VACANT))
				.next();
	}

	@Override
	public Mono<Duration> idletime(ByteBuffer key) {
		return connection.execute(cmd -> cmd.objectIdletime(key).map(Duration::ofSeconds)).next();
	}

	@Override
	public Mono<Long> refcount(ByteBuffer key) {
		return connection.execute(cmd -> cmd.objectRefcount(key)).next();
	}

	@Override
	public Mono<Long> exists(List<ByteBuffer> keys) {

		Assert.notNull(keys, "Key list must not be null");
		Assert.notEmpty(keys, "Key list must not be empty");

		return connection.execute(cmd -> cmd.exists(keys.toArray(ByteBuffer[]::new))).next();
	}

	private static ExpireArgs getExpireArgs(ExpirationOptions options) {

		return new ExpireArgs() {

			@Override
			public <K, V> void build(CommandArgs<K, V> args) {
				super.build(args);
				if (ObjectUtils.nullSafeEquals(options.getCondition(), ExpirationOptions.Condition.ALWAYS)) {
					return;
				}

				args.add(options.getCondition().name());
			}
		};
	}

}
