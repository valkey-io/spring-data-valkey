/*
 * Copyright 2011-2025 the original author or authors.
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
package io.valkey.springframework.data.valkey.connection.valkeyglide;

import org.springframework.data.domain.Range;
import org.springframework.util.Assert;

/**
 * Utility methods for Valkey-Glide connection implementation.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public abstract class ValkeyGlideUtils {

    /**
     * Convert a range min value to a string representation.
     *
     * @param range the range
     * @return string representation of the range min value
     */
    public static String convertRangeMinValue(Range<?> range) {
        Assert.notNull(range, "Range must not be null!");
        
        if (range.getLowerBound().isBounded()) {
            Object value = range.getLowerBound().getValue();
            return range.getLowerBound().isInclusive() ? String.valueOf(value) : "(" + String.valueOf(value);
        }
        
        return "-inf";
    }

    /**
     * Convert a range max value to a string representation.
     *
     * @param range the range
     * @return string representation of the range max value
     */
    public static String convertRangeMaxValue(Range<?> range) {
        Assert.notNull(range, "Range must not be null!");
        
        if (range.getUpperBound().isBounded()) {
            Object value = range.getUpperBound().getValue();
            return range.getUpperBound().isInclusive() ? String.valueOf(value) : "(" + String.valueOf(value);
        }
        
        return "+inf";
    }
}
