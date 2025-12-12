/*
 * Copyright 2021-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.domain.geo;

import org.springframework.data.geo.Metric;
import io.valkey.springframework.data.valkey.connection.ValkeyGeoCommands;

/**
 * {@link Metric}s supported by Valkey.
 *
 * @author Christoph Strobl
 * @since 2.6
 */
public enum Metrics implements Metric {

	METERS(6378137, "m"), KILOMETERS(6378.137, "km"), MILES(3963.191, "mi"), FEET(20925646.325, "ft");

	private final double multiplier;
	private final String abbreviation;

	/**
	 * Creates a new {@link ValkeyGeoCommands.DistanceUnit} using the given muliplier.
	 *
	 * @param multiplier the earth radius at equator.
	 */
	Metrics(double multiplier, String abbreviation) {

		this.multiplier = multiplier;
		this.abbreviation = abbreviation;
	}

	public double getMultiplier() {
		return multiplier;
	}

	@Override
	public String getAbbreviation() {
		return abbreviation;
	}
}
