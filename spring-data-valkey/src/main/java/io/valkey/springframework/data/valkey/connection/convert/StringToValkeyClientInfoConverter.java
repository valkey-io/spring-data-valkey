/*
 * Copyright 2014-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.convert;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo;
import io.valkey.springframework.data.valkey.core.types.ValkeyClientInfo.ValkeyClientInfoBuilder;

/**
 * {@link Converter} implementation to create one {@link ValkeyClientInfo} per line entry in given {@link String} array.
 *
 * <pre>
 * ## sample of single line
 * addr=127.0.0.1:60311 fd=6 name= age=4059 idle=0 flags=N db=0 sub=0 psub=0 multi=-1 qbuf=0 qbuf-free=32768 obl=0 oll=0 omem=0 events=r cmd=client
 * </pre>
 *
 * @author Christoph Strobl
 * @since 1.3
 */
public class StringToValkeyClientInfoConverter implements Converter<String[], List<ValkeyClientInfo>> {

	public static final StringToValkeyClientInfoConverter INSTANCE = new StringToValkeyClientInfoConverter();

	@Override
	public List<ValkeyClientInfo> convert(String[] lines) {

		List<ValkeyClientInfo> clientInfoList = new ArrayList<>(lines.length);
		for (String line : lines) {
			clientInfoList.add(ValkeyClientInfoBuilder.fromString(line));
		}

		return clientInfoList;
	}

}
