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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metric;
import org.springframework.data.geo.Point;
import io.valkey.springframework.data.valkey.connection.ValkeyGeoCommands;
import io.valkey.springframework.data.valkey.domain.geo.BoundingBox;
import io.valkey.springframework.data.valkey.domain.geo.BoxShape;
import io.valkey.springframework.data.valkey.domain.geo.GeoReference;
import io.valkey.springframework.data.valkey.domain.geo.GeoShape;
import io.valkey.springframework.data.valkey.domain.geo.RadiusShape;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import glide.api.models.GlideString;

/**
 * Implementation of {@link ValkeyGeoCommands} for Valkey-Glide.
 *
 * @author Ilia Kolominsky
 * @since 2.0
 */
public class ValkeyGlideGeoCommands implements ValkeyGeoCommands {

    private final ValkeyGlideConnection connection;

    /**
     * Creates a new {@link ValkeyGlideGeoCommands}.
     *
     * @param connection must not be {@literal null}.
     */
    public ValkeyGlideGeoCommands(ValkeyGlideConnection connection) {
        Assert.notNull(connection, "Connection must not be null!");
        this.connection = connection;
    }

    @Override
    @Nullable
    public Long geoAdd(byte[] key, Point point, byte[] member) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(point, "Point must not be null");
        Assert.notNull(member, "Member must not be null");

        try {
            return connection.execute("GEOADD",
                (Long glideResult) -> glideResult,
                key, point.getX(), point.getY(), member);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long geoAdd(byte[] key, Map<byte[], Point> memberCoordinateMap) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(memberCoordinateMap, "Member coordinate map must not be null");

        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            
            for (Map.Entry<byte[], Point> entry : memberCoordinateMap.entrySet()) {
                Point point = entry.getValue();
                commandArgs.add(point.getX());
                commandArgs.add(point.getY());
                commandArgs.add(entry.getKey());
            }
            
            return connection.execute("GEOADD",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long geoAdd(byte[] key, Iterable<GeoLocation<byte[]>> locations) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(locations, "Locations must not be null");

        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            
            for (GeoLocation<byte[]> location : locations) {
                Point point = location.getPoint();
                commandArgs.add(point.getX());
                commandArgs.add(point.getY());
                commandArgs.add(location.getName());
            }
            return connection.execute("GEOADD",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Distance geoDist(byte[] key, byte[] member1, byte[] member2) {
        return geoDist(key, member1, member2, DistanceUnit.METERS);
    }

    @Override
    @Nullable
    public Distance geoDist(byte[] key, byte[] member1, byte[] member2, Metric metric) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(member1, "Member1 must not be null");
        Assert.notNull(member2, "Member2 must not be null");
        Assert.notNull(metric, "Metric must not be null");

        try {
            return connection.execute("GEODIST",
                (Double glideResult) -> glideResult == null ? null : new Distance(glideResult, metric),
                key, member1, member2, metric.getAbbreviation());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<String> geoHash(byte[] key, byte[]... members) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(members, "Members must not be null");
        Assert.noNullElements(members, "Members must not contain null elements");

        try {
            Object[] args = new Object[members.length + 1];
            args[0] = key;
            System.arraycopy(members, 0, args, 1, members.length);
            return connection.execute("GEOHASH",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return null;
                    }
                    List<String> hashList = new ArrayList<>(glideResult.length);
                    for (Object item : glideResult) {
                        if (item == null) {
                            hashList.add(null);
                        }
                        else {
                            hashList.add(((GlideString) item).toString());
                        }
                    }
                    return hashList;
                },
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public List<Point> geoPos(byte[] key, byte[]... members) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(members, "Members must not be null");
        Assert.noNullElements(members, "Members must not contain null elements");

        try {
            Object[] args = new Object[members.length + 1];
            args[0] = key;
            System.arraycopy(members, 0, args, 1, members.length);

            
            return connection.execute("GEOPOS",
                (Object[] glideResult) -> {
                    if (glideResult == null) {
                        return new ArrayList<>();
                    }
                    
                    List<Point> pointList = new ArrayList<>(glideResult.length);
                    for (Object item : glideResult) {
                        if (item == null) {
                            pointList.add(null);
                        } else {
                            Point point = convertToPoint(item);
                            pointList.add(point);
                        }
                    }
                    return pointList;
                },
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public GeoResults<GeoLocation<byte[]>> geoRadius(byte[] key, Circle within) {
        return geoRadius(key, within, GeoRadiusCommandArgs.newGeoRadiusArgs());
    }

    @Override
    @Nullable
    public GeoResults<GeoLocation<byte[]>> geoRadiusByMember(byte[] key, byte[] member, Distance radius) {
        return geoRadiusByMember(key, member, radius, GeoRadiusCommandArgs.newGeoRadiusArgs());
    }

    @Override
    @Nullable
    public GeoResults<GeoLocation<byte[]>> geoRadius(byte[] key, Circle within, GeoRadiusCommandArgs args) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(within, "Circle must not be null");
        Assert.notNull(args, "GeoRadiusCommandArgs must not be null");

        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            commandArgs.add(within.getCenter().getX());
            commandArgs.add(within.getCenter().getY());
            commandArgs.add(within.getRadius().getValue());
            commandArgs.add(within.getRadius().getMetric().getAbbreviation());
            
            appendGeoRadiusArgs(commandArgs, args);
            
            return connection.execute("GEORADIUS",
                (Object[] glideResult) -> parseGeoResults(glideResult, args, within.getRadius().getMetric()),
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public GeoResults<GeoLocation<byte[]>> geoRadiusByMember(byte[] key, byte[] member, Distance radius,
            GeoRadiusCommandArgs args) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(member, "Member must not be null");
        Assert.notNull(radius, "Distance must not be null");
        Assert.notNull(args, "GeoRadiusCommandArgs must not be null");

        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            commandArgs.add(member);
            commandArgs.add(radius.getValue());
            commandArgs.add(radius.getMetric().getAbbreviation());
            
            appendGeoRadiusArgs(commandArgs, args);
            
            return connection.execute("GEORADIUSBYMEMBER",
                (Object[] glideResult) -> parseGeoResults(glideResult, args, radius.getMetric()),
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long geoRemove(byte[] key, byte[]... members) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(members, "Members must not be null");
        Assert.noNullElements(members, "Members must not contain null elements");

        try {
            Object[] args = new Object[members.length + 1];
            args[0] = key;
            System.arraycopy(members, 0, args, 1, members.length);


            return connection.execute("ZREM",
                (Long glideResult) -> glideResult,
                args);
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public GeoResults<GeoLocation<byte[]>> geoSearch(byte[] key, GeoReference<byte[]> reference, GeoShape predicate,
            GeoSearchCommandArgs args) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(reference, "GeoReference must not be null");
        Assert.notNull(predicate, "GeoShape must not be null");
        Assert.notNull(args, "GeoSearchCommandArgs must not be null");

        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(key);
            
            appendGeoReference(commandArgs, reference);
            appendGeoShape(commandArgs, predicate);
            appendGeoSearchArgs(commandArgs, args);
            
            return connection.execute("GEOSEARCH",
                (Object[] glideResult) -> parseGeoResults(glideResult, args, DistanceUnit.METERS),
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    @Override
    @Nullable
    public Long geoSearchStore(byte[] destKey, byte[] key, GeoReference<byte[]> reference, GeoShape predicate,
            GeoSearchStoreCommandArgs args) {
        Assert.notNull(destKey, "Destination key must not be null");
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(reference, "GeoReference must not be null");
        Assert.notNull(predicate, "GeoShape must not be null");
        Assert.notNull(args, "GeoSearchStoreCommandArgs must not be null");

        try {
            List<Object> commandArgs = new ArrayList<>();
            commandArgs.add(destKey);
            commandArgs.add(key);
            
            appendGeoReference(commandArgs, reference);
            appendGeoShape(commandArgs, predicate);
            appendGeoSearchStoreArgs(commandArgs, args);
            
            return connection.execute("GEOSEARCHSTORE",
                (Long glideResult) -> glideResult,
                commandArgs.toArray());
        } catch (Exception ex) {
            throw new ValkeyGlideExceptionConverter().convert(ex);
        }
    }

    // ==================== Helper Methods ====================

    private Point convertToPoint(Object obj) {
        if (obj == null) {
            return null;
        }
        
        Object[] coordinates = (Object[]) obj;
        
        if (coordinates.length >= 2 && coordinates[0] != null && coordinates[1] != null) {
            double x = parseDouble(coordinates[0]);
            double y = parseDouble(coordinates[1]);
            return new Point(x, y);
        }
        
        return null;
    }

    private double parseDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        } else if (obj instanceof GlideString) {
            return Double.parseDouble(((GlideString) obj).toString());
        } else {
            return Double.parseDouble(obj.toString());
        }
    }

    private void appendGeoRadiusArgs(List<Object> commandArgs, GeoRadiusCommandArgs args) {
        if (args.getFlags().contains(GeoRadiusCommandArgs.Flag.WITHCOORD)) {
            commandArgs.add("WITHCOORD");
        }
        if (args.getFlags().contains(GeoRadiusCommandArgs.Flag.WITHDIST)) {
            commandArgs.add("WITHDIST");
        }
        if (args.hasLimit()) {
            commandArgs.add("COUNT");
            commandArgs.add(args.getLimit());
            if (args.hasAnyLimit()) {
                commandArgs.add("ANY");
            }
        }
        if (args.hasSortDirection()) {
            commandArgs.add(args.getSortDirection() == Direction.ASC ? "ASC" : "DESC");
        }
    }

    private void appendGeoSearchArgs(List<Object> commandArgs, GeoSearchCommandArgs args) {
        if (args.getFlags().contains(GeoCommandArgs.GeoCommandFlag.withCord())) {
            commandArgs.add("WITHCOORD");
        }
        if (args.getFlags().contains(GeoCommandArgs.GeoCommandFlag.withDist())) {
            commandArgs.add("WITHDIST");
        }
        if (args.hasLimit()) {
            commandArgs.add("COUNT");
            commandArgs.add(args.getLimit());
            if (args.hasAnyLimit()) {
                commandArgs.add("ANY");
            }
        }
        if (args.hasSortDirection()) {
            commandArgs.add(args.getSortDirection() == Direction.ASC ? "ASC" : "DESC");
        }
    }

    private void appendGeoSearchStoreArgs(List<Object> commandArgs, GeoSearchStoreCommandArgs args) {
        if (args.isStoreDistance()) {
            commandArgs.add("STOREDIST");
        }
        if (args.hasLimit()) {
            commandArgs.add("COUNT");
            commandArgs.add(args.getLimit());
            if (args.hasAnyLimit()) {
                commandArgs.add("ANY");
            }
        }
        if (args.hasSortDirection()) {
            commandArgs.add(args.getSortDirection() == Direction.ASC ? "ASC" : "DESC");
        }
    }

    private void appendGeoReference(List<Object> commandArgs, GeoReference<byte[]> reference) {
        if (reference instanceof GeoReference.GeoMemberReference) {
            commandArgs.add("FROMMEMBER");
            commandArgs.add(((GeoReference.GeoMemberReference<byte[]>) reference).getMember());
        } else if (reference instanceof GeoReference.GeoCoordinateReference) {
            commandArgs.add("FROMLONLAT");
            GeoReference.GeoCoordinateReference<?> coordRef = (GeoReference.GeoCoordinateReference<?>) reference;
            commandArgs.add(coordRef.getLongitude());
            commandArgs.add(coordRef.getLatitude());
        }
    }

    private void appendGeoShape(List<Object> commandArgs, GeoShape shape) {
        if (shape instanceof RadiusShape) {
            commandArgs.add("BYRADIUS");
            RadiusShape radiusShape = (RadiusShape) shape;
            commandArgs.add(radiusShape.getRadius().getValue());
            commandArgs.add(radiusShape.getRadius().getMetric().getAbbreviation());
        } else if (shape instanceof BoxShape) {
            commandArgs.add("BYBOX");
            BoxShape boxShape = (BoxShape) shape;
            commandArgs.add(boxShape.getBoundingBox().getWidth().getValue());
            commandArgs.add(boxShape.getBoundingBox().getHeight().getValue());
            commandArgs.add(boxShape.getBoundingBox().getWidth().getMetric().getAbbreviation());
        }
    }
    
    private GeoResults<GeoLocation<byte[]>> parseGeoResults(Object[] result, GeoCommandArgs args, Metric defaultMetric) {
        if (result == null) {
            return new GeoResults<>(new ArrayList<>());
        }
        
        List<GeoResult<GeoLocation<byte[]>>> geoResults = new ArrayList<>(result.length);
        
        boolean hasDistance = args.getFlags().contains(GeoCommandArgs.GeoCommandFlag.withDist());
        boolean hasCoordinate = args.getFlags().contains(GeoCommandArgs.GeoCommandFlag.withCord());
        
        for (Object item : result) {
            if (hasDistance || hasCoordinate) {
                // Complex result format with additional information
                Object[] itemArray;
                if (item instanceof Object[]) {
                    itemArray = (Object[]) item;
                } else {
                    continue;
                }
                
                byte[] member = convertToBytes(itemArray[0]);
                Distance distance = null;
                Point point = null;
                
                int index = 1;
                if (hasDistance && index < itemArray.length) {
                    Object distObj = itemArray[index++];
                    if (distObj != null) {
                        try {
                            double dist = parseDouble(distObj);
                            distance = new Distance(dist, defaultMetric);
                        } catch (Exception e) {
                            distance = new Distance(0.0, defaultMetric);
                        }
                    } else {
                        distance = new Distance(0.0, defaultMetric);
                    }
                }
                if (hasCoordinate && index < itemArray.length) {
                    Object coordObj = itemArray[index];
                    point = convertToPoint(coordObj);
                }
                
                GeoLocation<byte[]> location = new GeoLocation<>(member, point);
                if (distance == null) {
                    distance = new Distance(0.0, defaultMetric);
                }
                geoResults.add(new GeoResult<>(location, distance));
            } else {
                // Simple result format - just member names
                byte[] member = convertToBytes(item);
                GeoLocation<byte[]> location = new GeoLocation<>(member, null);
                geoResults.add(new GeoResult<>(location, new Distance(0.0, defaultMetric)));
            }
        }
        
        return new GeoResults<>(geoResults);
    }

    private byte[] convertToBytes(Object obj) {
        if (obj instanceof GlideString) {
            return ((GlideString) obj).getBytes();
        } else if (obj instanceof byte[]) {
            return (byte[]) obj;
        } else {
            return obj.toString().getBytes();
        }
    }
}
