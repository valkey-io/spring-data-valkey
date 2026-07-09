/*
 * Copyright 2018-present the original author or authors.
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
package io.valkey.springframework.data.valkey.core;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Range;
import io.valkey.springframework.data.valkey.connection.Limit;
import io.valkey.springframework.data.valkey.connection.ValkeyConnection;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands.XAddOptions;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands.XClaimOptions;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands.XTrimOptions;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands.XDelOptions;
import io.valkey.springframework.data.valkey.connection.ValkeyStreamCommands.StreamEntryDeletionResult;
import io.valkey.springframework.data.valkey.connection.stream.ByteRecord;
import io.valkey.springframework.data.valkey.connection.stream.Consumer;
import io.valkey.springframework.data.valkey.connection.stream.MapRecord;
import io.valkey.springframework.data.valkey.connection.stream.PendingMessages;
import io.valkey.springframework.data.valkey.connection.stream.PendingMessagesSummary;
import io.valkey.springframework.data.valkey.connection.stream.ReadOffset;
import io.valkey.springframework.data.valkey.connection.stream.Record;
import io.valkey.springframework.data.valkey.connection.stream.RecordId;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoConsumers;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoGroups;
import io.valkey.springframework.data.valkey.connection.stream.StreamInfo.XInfoStream;
import io.valkey.springframework.data.valkey.connection.stream.StreamOffset;
import io.valkey.springframework.data.valkey.connection.stream.StreamReadOptions;
import io.valkey.springframework.data.valkey.hash.HashMapper;
import io.valkey.springframework.data.valkey.serializer.ValkeySerializer;
import io.valkey.springframework.data.valkey.support.collections.CollectionUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Default implementation of {@link ListOperations}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @author Marcin Zielinski
 * @author John Blum
 * @author jinkshower
 * @author Jeonggyu Choi
 * @since 2.2
 */
@NullUnmarked
class DefaultStreamOperations<K, HK, HV> extends AbstractOperations<K, Object> implements StreamOperations<K, HK, HV> {

	private final StreamObjectMapper objectMapper;

	@SuppressWarnings("unchecked")
	DefaultStreamOperations(@NonNull ValkeyTemplate<K, ?> template,
			@NonNull HashMapper<? super K, ? super HK, ? super HV> mapper) {

		super((ValkeyTemplate<K, Object>) template);

		this.objectMapper = new StreamObjectMapper(mapper) {
			@Override
			protected HashMapper<?, ?, ?> doGetHashMapper(ConversionService conversionService, Class<?> targetType) {

				if (isSimpleType(targetType)) {

					return new HashMapper<Object, Object, Object>() {

						@Override
						public Map<Object, Object> toHash(Object object) {

							Object key = "payload";
							Object value = object;

							if (!template.isEnableDefaultSerializer()) {
								if (template.getHashKeySerializer() == null) {
									key = key.toString().getBytes(StandardCharsets.UTF_8);
								}
								if (template.getHashValueSerializer() == null) {
									value = serializeHashValueIfRequires((HV) object);
								}
							}

							return Collections.singletonMap(key, value);
						}

						@Override
						public Object fromHash(Map<Object, Object> hash) {
							Object value = hash.values().iterator().next();
							if (ClassUtils.isAssignableValue(targetType, value)) {
								return value;
							}

							HV deserialized = deserializeHashValue((byte[]) value);

							if (ClassUtils.isAssignableValue(targetType, deserialized)) {
								return value;
							}

							return conversionService.convert(deserialized, targetType);
						}
					};
				}

				return super.doGetHashMapper(conversionService, targetType);
			}
		};
	}

	@Override
	public Long acknowledge(@NonNull K key, @NonNull String group, @NonNull String @NonNull... recordIds) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xAck(rawKey, group, recordIds));
	}

	@Override
	@SuppressWarnings("unchecked")
	public RecordId add(@NonNull Record<K, ?> record) {

		Assert.notNull(record, "Record must not be null");

		MapRecord<K, HK, HV> input = StreamObjectMapper.toMapRecord(this, record);

		ByteRecord binaryRecord = input.serialize(keySerializer(), hashKeySerializer(), hashValueSerializer());

		return execute(connection -> connection.xAdd(binaryRecord));
	}

	@Override
	@SuppressWarnings("unchecked")
	public RecordId add(@NonNull Record<K, ?> record, @NonNull XAddOptions options) {

		Assert.notNull(record, "Record must not be null");
		Assert.notNull(options, "XAddOptions must not be null");

		MapRecord<K, HK, HV> input = StreamObjectMapper.toMapRecord(this, record);

		ByteRecord binaryRecord = input.serialize(keySerializer(), hashKeySerializer(), hashValueSerializer());

		return execute(connection -> connection.streamCommands().xAdd(binaryRecord, options));
	}

	@Override
	public List<MapRecord<K, HK, HV>> claim(@NonNull K key, @NonNull String consumerGroup, @NonNull String newOwner,
			@NonNull XClaimOptions xClaimOptions) {

		return CollectionUtils.nullSafeList(execute(new RecordDeserializingValkeyCallback() {

			@Nullable
			@Override
			List<ByteRecord> inValkey(ValkeyConnection connection) {
				return connection.streamCommands().xClaim(rawKey(key), consumerGroup, newOwner, xClaimOptions);
			}
		}));
	}

	@Override
	public Long delete(@NonNull K key, @NonNull RecordId @NonNull... recordIds) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xDel(rawKey, recordIds));
	}

	@Override
	public List<StreamEntryDeletionResult> deleteWithOptions(@NonNull K key, @NonNull XDelOptions options,
			@NonNull String @NonNull... recordIds) {

		byte[] rawKey = rawKey(key);
		RecordId[] recordIdArray = Arrays.stream(recordIds).map(RecordId::of).toArray(RecordId[]::new);
		return execute(connection -> connection.streamCommands().xDelEx(rawKey, options, recordIdArray));
	}

	@Override
	public List<StreamEntryDeletionResult> acknowledgeAndDelete(@NonNull K key, @NonNull String group,
																					@NonNull XDelOptions options,
																					@NonNull String @NonNull... recordIds) {

		byte[] rawKey = rawKey(key);
		RecordId[] recordIdArray = Arrays.stream(recordIds).map(RecordId::of).toArray(RecordId[]::new);
		return execute(connection -> connection.streamCommands().xAckDel(rawKey, group, options, recordIdArray));
	}

	@Override
	public String createGroup(@NonNull K key, @NonNull ReadOffset readOffset, @NonNull String group) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xGroupCreate(rawKey, group, readOffset, true));
	}

	@Override
	public Boolean deleteConsumer(@NonNull K key, @NonNull Consumer consumer) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xGroupDelConsumer(rawKey, consumer));
	}

	@Override
	public Boolean destroyGroup(@NonNull K key, @NonNull String group) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xGroupDestroy(rawKey, group));
	}

	@Override
	public XInfoStream info(@NonNull K key) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xInfo(rawKey));
	}

	@Override
	public XInfoConsumers consumers(@NonNull K key, @NonNull String group) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xInfoConsumers(rawKey, group));
	}

	@Override
	public XInfoGroups groups(@NonNull K key) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xInfoGroups(rawKey));
	}

	@Override
	public PendingMessages pending(@NonNull K key, @NonNull String group, @NonNull Range<?> range, long count) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xPending(rawKey, group, range, count));
	}

	@Override
	public PendingMessages pending(K key, String group, Range<?> range, long count, Duration minIdleTime) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xPending(rawKey, group, range, count, minIdleTime));
	}

	@Override
	public PendingMessages pending(@NonNull K key, @NonNull Consumer consumer, @NonNull Range<?> range, long count) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xPending(rawKey, consumer, range, count));
	}

	@Override
	public PendingMessages pending(K key, Consumer consumer, Range<?> range, long count, Duration minIdleTime) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xPending(rawKey, consumer, range, count, minIdleTime));
	}

	@Override
	public PendingMessagesSummary pending(@NonNull K key, @NonNull String group) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xPending(rawKey, group));
	}

	@Override
	public Long size(@NonNull K key) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xLen(rawKey));
	}

	@Override
	public List<MapRecord<K, HK, HV>> range(@NonNull K key, @NonNull Range<String> range, @NonNull Limit limit) {

		return execute(new RecordDeserializingValkeyCallback() {

			@Nullable
			@Override
			List<ByteRecord> inValkey(ValkeyConnection connection) {
				return connection.xRange(rawKey(key), range, limit);
			}
		});
	}

	@Override
	public List<MapRecord<K, HK, HV>> read(@NonNull StreamReadOptions readOptions,
			@NonNull StreamOffset<K> @NonNull... streams) {

		return execute(new RecordDeserializingValkeyCallback() {

			@Nullable
			@Override
			List<ByteRecord> inValkey(ValkeyConnection connection) {
				return connection.xRead(readOptions, rawStreamOffsets(streams));
			}
		});
	}

	@Override
	public List<MapRecord<K, HK, HV>> read(@NonNull Consumer consumer, @NonNull StreamReadOptions readOptions,
			@NonNull StreamOffset<K> @NonNull... streams) {

		return execute(new RecordDeserializingValkeyCallback() {

			@Nullable
			@Override
			List<ByteRecord> inValkey(ValkeyConnection connection) {
				return connection.xReadGroup(consumer, readOptions, rawStreamOffsets(streams));
			}
		});
	}

	@Override
	public List<MapRecord<K, HK, HV>> reverseRange(@NonNull K key, @NonNull Range<String> range, @NonNull Limit limit) {

		return execute(new RecordDeserializingValkeyCallback() {

			@Nullable
			@Override
			List<ByteRecord> inValkey(ValkeyConnection connection) {
				return connection.xRevRange(rawKey(key), range, limit);
			}
		});
	}

	@Override
	public Long trim(@NonNull K key, long count) {

		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xTrim(rawKey, count));
	}

	@Override
	public Long trim(@NonNull K key, long count, boolean approximateTrimming) {
		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.xTrim(rawKey, count, approximateTrimming));
	}

	@Override
	public Long trim(@NonNull K key, @NonNull XTrimOptions options) {
		byte[] rawKey = rawKey(key);
		return execute(connection -> connection.streamCommands().xTrim(rawKey, options));
	}

	@Override
	public <V> HashMapper<V, HK, HV> getHashMapper(@NonNull Class<V> targetType) {
		return objectMapper.getHashMapper(targetType);
	}

	@Override
	@SuppressWarnings("unchecked")
	public MapRecord<K, HK, HV> deserializeRecord(@NonNull ByteRecord record) {
		return record.deserialize(keySerializer(), hashKeySerializer(), hashValueSerializer());
	}

	protected byte[] serializeHashValueIfRequires(HV value) {
		return hashValueSerializerPresent() ? serialize(value, requiredHashValueSerializer())
				: objectMapper.getConversionService().convert(value, byte[].class);
	}

	protected boolean hashValueSerializerPresent() {
		return hashValueSerializer() != null;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private byte[] serialize(Object value, ValkeySerializer serializer) {

		Object _value = value;
		if (!serializer.canSerialize(value.getClass())) {
			_value = objectMapper.getConversionService().convert(value, serializer.getTargetType());
		}
		return serializer.serialize(_value);
	}

	@SuppressWarnings("unchecked")
	private StreamOffset<byte[]>[] rawStreamOffsets(StreamOffset<K>[] streams) {

		return Arrays.stream(streams) //
				.map(it -> StreamOffset.create(rawKey(it.getKey()), it.getOffset())) //
				.toArray(StreamOffset[]::new);
	}

	abstract class RecordDeserializingValkeyCallback implements ValkeyCallback<List<MapRecord<K, HK, HV>>> {

		public final List<MapRecord<K, HK, HV>> doInValkey(@NonNull ValkeyConnection connection) {

			List<ByteRecord> raw = inValkey(connection);
			if (raw == null) {
				return Collections.emptyList();
			}

			List<MapRecord<K, HK, HV>> result = new ArrayList<>();
			for (ByteRecord record : raw) {
				result.add(deserializeRecord(record));
			}

			return result;
		}

		abstract List<ByteRecord> inValkey(ValkeyConnection connection);
	}

}
